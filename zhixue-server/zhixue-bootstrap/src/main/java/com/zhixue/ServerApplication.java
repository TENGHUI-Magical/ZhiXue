package com.zhixue;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 智学领航业务服务统一入口（模块化单体，规划书 2.1 节 阶段 A）。
 * MapperScan 扫全包，各业务模块的 Mapper 接口无需单独配置。
 * EnableScheduling：OCR 超时任务回收器（契约 §3 状态机）。
 */
@SpringBootApplication
@MapperScan("com.zhixue")
@ConfigurationPropertiesScan("com.zhixue")
@EnableScheduling
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}