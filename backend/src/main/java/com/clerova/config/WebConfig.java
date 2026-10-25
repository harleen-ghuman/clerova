package com.clerova.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String allowedOrigin;
    private final AiRateLimitInterceptor aiRateLimitInterceptor;

    public WebConfig(
            @Value("${app.cors.allowed-origin}") String allowedOrigin,
            AiRateLimitInterceptor aiRateLimitInterceptor
    ) {
        this.allowedOrigin = allowedOrigin;
        this.aiRateLimitInterceptor = aiRateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Endpoints whose POST triggers an OpenAI call
        registry.addInterceptor(aiRateLimitInterceptor)
                .addPathPatterns(
                        "/api/work-items",
                        "/api/work-items/*/proposed-actions"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
                .allowedHeaders("*");
    }
}