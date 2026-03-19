package com.sage.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.sage.backend.mapper")
@EnableScheduling
public class SageBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SageBackendApplication.class, args);
    }
}
