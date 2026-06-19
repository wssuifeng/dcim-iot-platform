package com.example.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class IotPlatformApplicationTests {

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void verifyEnvFileLoaded() {
		// 验证 .env 文件中的关键变量是否被 Spring Environment 加载
		String dbPassword = environment.getProperty("DB_PASSWORD");
		String mailUsername = environment.getProperty("MAIL_USERNAME");
		String mailPassword = environment.getProperty("MAIL_PASSWORD");
		String notifyMailTo = environment.getProperty("NOTIFY_MAIL_TO");

		System.out.println("===== .env 加载验证 =====");
		System.out.println("DB_PASSWORD = " + (dbPassword != null ? dbPassword.substring(0, Math.min(3, dbPassword.length())) + "***" : "NULL"));
		System.out.println("MAIL_USERNAME = " + mailUsername);
		System.out.println("MAIL_PASSWORD length = " + (mailPassword != null ? mailPassword.length() : 0));
		System.out.println("NOTIFY_MAIL_TO = " + notifyMailTo);
		System.out.println("===== 验证结束 =====");

		assertNotNull(dbPassword, "DB_PASSWORD 未从 .env 加载");
	 assertFalse(dbPassword.isEmpty(), "DB_PASSWORD 为空");

		assertNotNull(mailUsername, "MAIL_USERNAME 未从 .env 加载");
		assertFalse(mailUsername.isEmpty(), "MAIL_USERNAME 为空");

		assertNotNull(mailPassword, "MAIL_PASSWORD 未从 .env 加载");
		assertFalse(mailPassword.isEmpty(), "MAIL_PASSWORD 为空");

		assertNotNull(notifyMailTo, "NOTIFY_MAIL_TO 未从 .env 加载");
		assertFalse(notifyMailTo.isEmpty(), "NOTIFY_MAIL_TO 为空");
	}

	@Test
	void verifySpringMailPropertiesResolved() {
		// 验证 spring.mail.* 属性是否正确解析了 ${MAIL_*} 占位符
		String springMailUsername = environment.getProperty("spring.mail.username");
		String springMailPassword = environment.getProperty("spring.mail.password");
		String springMailHost = environment.getProperty("spring.mail.host");

		System.out.println("===== spring.mail.* 属性解析验证 =====");
		System.out.println("spring.mail.host = " + springMailHost);
		System.out.println("spring.mail.username = " + springMailUsername);
		System.out.println("spring.mail.password length = " + (springMailPassword != null ? springMailPassword.length() : 0));
		System.out.println("===== 验证结束 =====");

		assertNotNull(springMailUsername, "spring.mail.username 未解析");
		assertFalse(springMailUsername.isEmpty(), "spring.mail.username 为空");

		assertNotNull(springMailPassword, "spring.mail.password 未解析");
		assertFalse(springMailPassword.isEmpty(), "spring.mail.password 为空");
	}
}
