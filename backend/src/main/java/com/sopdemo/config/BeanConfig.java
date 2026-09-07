package com.sopdemo.config;

import com.sopdemo.domain.parse.SafeYaml;
import com.sopdemo.domain.validate.ValidationService;
import com.sopdemo.web.ApiErrors;
import com.sopdemo.web.identity.IdentityFilter;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wire-up for the validation pipeline, identity filter, and CORS (DES-010, DES-012, IR-001). */
@Configuration
public class BeanConfig {

    @Bean
    public ValidationService validationService(DemoProperties props) {
        int maxNesting = props.source() != null && props.source().maxYamlNesting() > 0
                ? props.source().maxYamlNesting() : 20;
        return new ValidationService(new SafeYaml.ParseLimits(maxNesting));
    }

    @Bean
    public FilterRegistrationBean<IdentityFilter> identityFilter(DemoProperties props, ApiErrors errors) {
        FilterRegistrationBean<IdentityFilter> r = new FilterRegistrationBean<>(new IdentityFilter(props, errors));
        r.addUrlPatterns("/api/v1/*");
        r.setDispatcherTypes(DispatcherType.REQUEST);
        r.setOrder(10);
        return r;
    }

    /** CORS is limited to the single configured local UI origin (IR-001, NFR-020, DES-012). */
    @Bean
    public CorsFilter corsFilter(DemoProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        String origin = props.cors() != null ? props.cors().allowOrigin() : "http://localhost";
        cfg.setAllowedOrigins(List.of(origin));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-Demo-User"));
        cfg.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/v1/**", cfg);
        return new CorsFilter(src);
    }

    /** MVC-level CORS mapping, so both filter and MVC stacks enforce the same policy. */
    @Bean
    public WebMvcConfigurer mvcCorsConfigurer(DemoProperties props) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                String origin = props.cors() != null ? props.cors().allowOrigin() : "http://localhost";
                registry.addMapping("/api/v1/**")
                        .allowedOrigins(origin)
                        .allowedMethods("GET", "POST", "PUT", "OPTIONS")
                        .allowedHeaders("Content-Type", "X-Demo-User");
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(DemoProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        String origin = props.cors() != null ? props.cors().allowOrigin() : "http://localhost";
        cfg.setAllowedOrigins(List.of(origin));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-Demo-User"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/v1/**", cfg);
        return src;
    }
}
