package com.ticksim.writer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WriterApplication {
    public static void main(String[] args) {
        SpringApplication.run(WriterApplication.class, args);
    }
}
