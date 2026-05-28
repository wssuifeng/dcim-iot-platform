package com.example.platform.common.exception;

import lombok.Getter;

/*
 * 类功能:缓存操作异常，当Redis不可用导致计数器等无DB兜底的操作失败时抛出
 *
 * 【为什么 increment/decrement 需要抛异常而非静默降级？】
 * 与 get/set 不同，计数器操作没有数据库兜底：
 *   - get 失败 → 返回 null → 业务层查 DB → 数据完整
 *   - set 失败 → 仅记日志 → DB 是主存储 → 下次查询自然回填
 *   - increment 失败 → 返回 0 → 调用方无法区分"成功递增到0"和"Redis挂了"
 *   - decrement 失败 → 返回 0 → 同上，0 是合法业务值，静默降级会误导业务层
 *
 * 因此计数器操作失败应向上传播，由 GlobalExceptionHandler 统一响应 503，
 * 明确告知客户端"服务暂时不可用"，而非返回一个看似正常的错误值
 * */
@Getter
public class CacheOperationException extends RuntimeException {

    private final int code;

    public CacheOperationException(String message) {
        super(message);
        this.code = 503;
    }

    public CacheOperationException(int code, String message) {
        super(message);
        this.code = code;
    }

    public CacheOperationException(String message, Throwable cause) {
        super(message, cause);
        this.code = 503;
    }
}
