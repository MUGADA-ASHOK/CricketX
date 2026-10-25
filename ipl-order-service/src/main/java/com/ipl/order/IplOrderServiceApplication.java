package com.ipl.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IplOrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IplOrderServiceApplication.class, args);
    }
}
