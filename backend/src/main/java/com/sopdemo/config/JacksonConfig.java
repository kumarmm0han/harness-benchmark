package com.sopdemo.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * API response fields are snake_case per the documented contract
 * (IR-001: {@code sop_id}, {@code publish_failed}, {@code saved_at}, ...).
 * A single naming strategy keeps every record in sync (PRN-005: stable,
 * centralized conventions).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer snakeCase() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
