package com.sopdemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is limited to the single configured local UI origin (IR-001, NFR-020).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String uiOrigin;

    public CorsConfig(@Value("${sop.cors-origin}") String uiOrigin) {
        this.uiOrigin = uiOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(uiOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "X-Demo-User")
                .maxAge(3600);
    }
}
