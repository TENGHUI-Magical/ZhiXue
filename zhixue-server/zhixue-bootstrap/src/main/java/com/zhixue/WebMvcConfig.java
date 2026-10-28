package com.zhixue;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Set default Content-Type to application/json for all controllers
     * when the incoming Accept header is wildcard.
     * This makes springdoc emit application/json instead of wildcard in
     * generated /v3/api-docs responses, aligning with docs/openapi.yaml.
     * SSE endpoints override this via explicit produces on their method.
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}
