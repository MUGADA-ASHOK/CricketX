package com.ipl.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IplEventServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IplEventServiceApplication.class, args);
	}

}

