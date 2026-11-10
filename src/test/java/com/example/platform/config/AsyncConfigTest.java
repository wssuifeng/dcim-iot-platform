package com.example.platform.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 类功能:W-003 修复回归——评估线程池队列打满必须走 CallerRuns（提交线程自行执行），
 *   不得使用默认 AbortPolicy（会从 AFTER_COMMIT 穿透异常，丢告警评估且误记写入失败）
 * */
class AsyncConfigTest {

    @Test
    void rejectionPolicyIsCallerRuns() {
        AsyncConfig config = new AsyncConfig();
        Executor executor = config.getAsyncExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolExecutor jdkExecutor = ((ThreadPoolTaskExecutor) executor).getThreadPoolExecutor();
        assertThat(jdkExecutor.getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }
}
