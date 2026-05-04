package com.example.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.platform.mapper")
public class IotPlatformApplication {
	public static void main(String[] args) {
		SpringApplication.run(IotPlatformApplication.class, args);
		System.out.println("========================================");
		System.out.println("物联网平台启动成功！");
		System.out.println("访问地址：http://localhost:8080/api");
		System.out.println("Druid监控：http://localhost:8080/api/druid");
		System.out.println("========================================");
	}

}
