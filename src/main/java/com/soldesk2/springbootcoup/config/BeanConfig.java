package com.soldesk2.springbootcoup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.gson.Gson;

@Configuration
public class BeanConfig {
    @Bean
    public Gson gson() {
        return new Gson();
    }
}
