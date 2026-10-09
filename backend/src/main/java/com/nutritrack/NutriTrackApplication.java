package com.nutritrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NutriTrackApplication {
    public static void main(String[] args) {
        SpringApplication.run(NutriTrackApplication.class, args);
    }
}
