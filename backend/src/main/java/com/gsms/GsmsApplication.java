package com.gsms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 工伤与劳动仲裁案件协同管理系统
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.gsms.module.**.mapper")
public class GsmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GsmsApplication.class, args);
    }
}
