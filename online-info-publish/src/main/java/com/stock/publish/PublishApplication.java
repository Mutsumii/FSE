package com.stock.publish;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.stock.publish.mapper")
@EnableScheduling
public class PublishApplication {
    public static void main(String[] args) {
        SpringApplication.run(PublishApplication.class, args);
    }
}
