package com.example.platform.common.constant;

import java.util.List;

/*
 * 类功能:登录失败Lua脚本常量，实现滑动窗口计数与锁定状态机的原子操作
 *
 * 【为什么必须用Lua脚本？】
 * "读取状态 → 判断窗口/锁定 → 递增/重置 → 写回" 必须在同一个原子操作中完成。
 * Java应用层多线程并发会导致竞态条件：两个线程同时读到旧状态，各自写回，后写覆盖前写。
 * Redis单线程执行Lua脚本时，脚本内所有命令不会被其他客户端插队，天然保证原子性。
 *
 * 【为什么不用INCR实现？】
 * INCR只能实现粗粒度固定窗口（边界突刺问题：4:59失败5次，5:01计数器重置又能失败5次）。
 * 滑动窗口+锁定状态需要维护多个字段，INCR无法表达。
 *
 * 【为什么用Hash而不是JSON？】
 *   - JSON：每次操作需 cjson.decode → 修改 → cjson.encode → SET，开销大且需 pcall 容错
 *   - Hash：HINCRBY 原子递增，HGET 直接读字段，无需序列化/反序列化
 *   - Hash 不存在的 field 返回 nil，用 or '0' 即可处理，无需 pcall
 *   - 代码量减少 50%，性能提升一个数量级
 *
 * 【禁忌：Lua脚本内禁止调用redis.call('TIME')】
 * Redis主从复制要求Lua脚本在所有节点产生相同的执行结果（确定性），
 * TIME命令在不同节点返回不同值，会导致主从数据不一致。
 * 所有时间数据由Java端通过ARGV传入。
 *
 * 【Redis Hash 结构】
 * Key:   auth:fail:state:{ip}
 * Field: count        → 失败次数
 * Field: lock_until   → 锁定截止时间戳（毫秒），未锁定时为 0
 * Field: window_start → 当前滑动窗口起始时间戳（毫秒）
 *
 * 【ARGV参数顺序】
 * ARGV[1] = 当前时间戳（毫秒）
 * ARGV[2] = 滑动窗口时长（毫秒）
 * ARGV[3] = 最大失败次数
 * ARGV[4] = 锁定时长（毫秒）
 *
 * 【返回值约定】
 * 返回数组 [状态码, 重试次数, 锁定剩余秒数]：
 *   - [0, retryCount, 0]           → 正常失败（未锁定）
 *   - [1, retryCount, lockRemain]  → 已处于锁定期
 *   - [2, retryCount, lockTotal]   → 刚触发锁定（本次达到阈值）
 * */
public final class LoginFailScript {

    private LoginFailScript() {
    }

    /*
     * 登录失败Lua脚本（Hash结构版）
     *
     * 逻辑步骤：
     * 1. 从Hash中读取当前状态（count、lock_until、window_start）
     * 2. 从ARGV提取时间参数（禁止在Lua内调用TIME）
     * 3. 判断是否处于锁定期且未到期 → 返回[1, count, 锁定剩余秒数]
     * 4. 判断滑动窗口是否过期 → 过期则重置计数为1并更新窗口起始，否则递增
     * 5. 若递增后达到阈值 → 设置lock_until，更新TTL，返回[2, count, 锁定总秒数]
     * 6. 若未达阈值 → 更新TTL，返回[0, count, 0]
     * */
    public static final String RECORD_FAIL =
            """
                    -- 步骤1：从Hash中读取当前状态，不存在的field返回nil，or '0'兜底
                    local count = tonumber(redis.call('HGET', KEYS[1], 'count') or '0')
                    local lock_until = tonumber(redis.call('HGET', KEYS[1], 'lock_until') or '0')
                    local window_start = tonumber(redis.call('HGET', KEYS[1], 'window_start') or '0')

                    -- 步骤2：从ARGV提取Java端传入的时间参数（禁止在Lua内调用TIME）
                    local now_ms      = tonumber(ARGV[1])
                    local window_ms   = tonumber(ARGV[2])
                    local max_retries = tonumber(ARGV[3])
                    local lock_ms     = tonumber(ARGV[4])
                    local buffer_sec  = 10

                    -- 步骤3：判断是否处于锁定期且未到期
                    if lock_until > now_ms then
                      local lock_remain_sec = math.ceil((lock_until - now_ms) / 1000)
                      redis.call('EXPIRE', KEYS[1], lock_remain_sec + buffer_sec)
                      return {1, count, lock_remain_sec}
                    end

                    -- 步骤4：判断滑动窗口是否过期，过期则重置计数，否则递增
                    if window_start + window_ms <= now_ms then
                      count = 1
                      window_start = now_ms
                      redis.call('HSET', KEYS[1], 'count', count, 'window_start', window_start)
                      redis.call('HDEL', KEYS[1], 'lock_until')
                    else
                      if count < max_retries then
                        count = count + 1
                        redis.call('HINCRBY', KEYS[1], 'count', 1)
                      end
                    end

                    -- 步骤5：若达到阈值，触发锁定
                    if count >= max_retries then
                      lock_until = now_ms + lock_ms
                      redis.call('HSET', KEYS[1], 'lock_until', lock_until)
                      local lock_sec = math.ceil(lock_ms / 1000)
                      redis.call('EXPIRE', KEYS[1], lock_sec + buffer_sec)
                      return {2, count, lock_sec}
                    end

                    -- 步骤6：未达阈值，正常失败，更新TTL防止僵尸Key
                    local window_remain_ms = window_start + window_ms - now_ms
                    local window_remain_sec = math.ceil(window_remain_ms / 1000)
                    redis.call('EXPIRE', KEYS[1], window_remain_sec + buffer_sec)
                    return {0, count, 0}""";

    /*
     * 类功能:Lua脚本返回值的领域对象封装
     *
     * 【为什么需要封装？】
     * Java端用 result.get(0)、result.get(1) 裸下标解析 Lua 返回值，可读性差且易出错。
     * 封装为 record 后：
     *   - failResult.isLocked() 比 result.get(0) == 1 自文档化
     *   - IDE 自动补全，编译期类型检查
     *   - 解析逻辑集中在一处，Lua 返回格式变更时只改这里
     * */
    public record LoginFailResult(boolean locked, int retryCount, long lockRemainSec) {

        public boolean isLocked() {
            return locked;
        }

        /*
         * 方法功能:从Lua脚本返回的List<Long>构造LoginFailResult
         * 解析规则：
         *   result[0] = 状态码（0=正常失败, 1=已锁定, 2=刚触发锁定）
         *   result[1] = 重试次数
         *   result[2] = 锁定剩余秒数（状态码0时为0）
         * */
        public static LoginFailResult fromLuaResult(List<Long> result) {
            if (result == null || result.size() < 3) {
                return new LoginFailResult(false, 0, 0);
            }
            boolean locked = result.get(0) == 1 || result.get(0) == 2;
            int retryCount = result.get(1).intValue();
            long lockRemainSec = result.get(2);
            return new LoginFailResult(locked, retryCount, lockRemainSec);
        }

        /*
         * 方法功能:计算剩余尝试次数
         * 返回数据:最大失败次数 - 当前重试次数
         * */
        public int remainingAttempts(int maxRetries) {
            return maxRetries - retryCount;
        }
    }
}
