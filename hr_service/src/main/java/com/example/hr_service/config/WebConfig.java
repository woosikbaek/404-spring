package com.example.hr_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 모든 경로(API)에 대해
                .allowedOriginPatterns("*") // 모든 도메인(프론트엔드) 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 모든 방식 허용
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true); // 쿠키/인증 허용
    }
}