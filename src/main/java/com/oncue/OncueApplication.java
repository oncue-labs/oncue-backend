package com.oncue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OncueApplication {

    public static void main(String[] args) {
        SpringApplication.run(OncueApplication.class, args);
    }
}
