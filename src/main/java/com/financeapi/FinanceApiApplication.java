package com.financeapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
@org.springframework.scheduling.annotation.EnableScheduling
public class FinanceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceApiApplication.class, args);
    }
}
