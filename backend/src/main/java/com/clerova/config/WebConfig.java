package com.clerova.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration. CORS is configured in SecurityConfig so it also applies
 * to responses rejected by Spring Security.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AiRateLimitInterceptor aiRateLimitInterceptor;

    public WebConfig(AiRateLimitInterceptor aiRateLimitInterceptor) {
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
}
