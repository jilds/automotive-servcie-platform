package com.asp.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AspApplication {

    public static void main(String[] args) {
        SpringApplication.run(AspApplication.class, args);
    }
}
