package com.sop.config;

import com.sop.api.ErrorCode;
import com.sop.api.IdentityFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class WebConfig {

  @Bean
  public FilterRegistrationBean<IdentityFilter> identityFilterRegistration(IdentityFilter identityFilter) {
    FilterRegistrationBean<IdentityFilter> bean = new FilterRegistrationBean<>(identityFilter);
    bean.addUrlPatterns("/api/v1/*");
    bean.setOrder(0);
    return bean;
  }

  // CORS is enforced on the backend as defense-in-depth (DES-011).
  // The nginx proxy already collapses it for the browser.
  @Bean
  public CorsFilter corsFilter() {
    CorsConfiguration config = new CorsConfiguration();
    // Default to the local UI origin (DES-011).
    config.setAllowedOrigins(List.of("http://localhost:8075"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "X-Demo-User"));
    config.setExposedHeaders(List.of("X-Request-Id"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/*", config);
    return new CorsFilter(source);
  }
}
