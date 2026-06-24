package com.example.panstwamiasta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRetry
public class PanstwaMiastaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PanstwaMiastaApplication.class, args);
    }

}
