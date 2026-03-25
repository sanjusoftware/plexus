package com.bankengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
public class PlexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlexusApplication.class, args);
    }
}