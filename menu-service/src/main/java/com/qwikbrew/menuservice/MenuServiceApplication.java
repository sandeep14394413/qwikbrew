package com.qwikbrew.menuservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MenuServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }
}
