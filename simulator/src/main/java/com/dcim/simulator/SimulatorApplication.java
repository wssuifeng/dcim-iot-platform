package com.dcim.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 机房仿真服务主启动类：设备模块(Modbus slave)+网关模块(轮询/北向上报)同一进程内的两个独立子系统
 */
@SpringBootApplication
@EnableScheduling
public class SimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}
