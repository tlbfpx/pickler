package com.heypickler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${hey-pickler.cors.admin-origins:http://localhost:5173,http://localhost:3000}")
    private String adminOrigins;

    @Value("${hey-pickler.cors.app-origins:}")
    private String appOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration adminConfig = new CorsConfiguration();
        adminConfig.setAllowedOrigins(List.of(adminOrigins.split(",")));
        adminConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        adminConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        adminConfig.setAllowCredentials(true);
        adminConfig.setMaxAge(3600L);

        CorsConfiguration appConfig = new CorsConfiguration();
        List<String> appOriginList = appOrigins.isBlank()
                ? List.of()
                : List.of(appOrigins.split(","));
        if (appOriginList.isEmpty()) {
            appConfig.setAllowedOrigins(List.of());
            appConfig.setAllowCredentials(false);
        } else {
            appConfig.setAllowedOrigins(appOriginList);
            appConfig.setAllowCredentials(true);
        }
        appConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        appConfig.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        appConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", adminConfig);
        source.registerCorsConfiguration("/api/app/**", appConfig);

        return new CorsFilter(source);
    }
}
