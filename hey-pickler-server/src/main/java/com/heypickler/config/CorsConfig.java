package com.heypickler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration adminConfig = new CorsConfiguration();
        adminConfig.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:5173"
        ));
        adminConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        adminConfig.setAllowedHeaders(List.of("*"));
        adminConfig.setAllowCredentials(true);
        adminConfig.setMaxAge(3600L);

        CorsConfiguration appConfig = new CorsConfiguration();
        appConfig.setAllowedOriginPatterns(List.of("*"));
        appConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        appConfig.setAllowedHeaders(List.of("*"));
        appConfig.setAllowCredentials(true);
        appConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", adminConfig);
        source.registerCorsConfiguration("/api/app/**", appConfig);

        return new CorsFilter(source);
    }
}
