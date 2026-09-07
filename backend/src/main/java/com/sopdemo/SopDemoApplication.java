package com.sopdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SopDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(SopDemoApplication.class, args);
    }
}
