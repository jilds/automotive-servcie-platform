package com.asp.platform;

import org.springframework.boot.SpringApplication;

/// Runs the app locally against a throw-away database: run this class from the IDE, no compose needed.
public class TestAspApplication {

    public static void main(String[] args) {
        SpringApplication.from(AspApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
