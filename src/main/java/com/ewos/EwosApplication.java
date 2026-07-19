package com.ewos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("com.ewos")
@EnableScheduling
public class EwosApplication {

    public static void main(String[] args) {
        SpringApplication.run(EwosApplication.class, args);
    }
}
