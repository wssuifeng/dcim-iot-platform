package com.example.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * 类功能:IoT平台主启动类，启用Spring Boot自动配置、定时任务调度和重试机制
 * */
@SpringBootApplication
@EnableScheduling
@EnableRetry
public class IotPlatformApplication {

    /*
     * 方法功能:应用入口方法
     * 参数:args-命令行参数
     * 返回数据:无
     * */
    public static void main(String[] args) {
        SpringApplication.run(IotPlatformApplication.class, args);
    }
}
