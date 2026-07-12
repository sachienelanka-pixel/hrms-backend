package com.hrms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded files at /uploads/** from the upload directory
        String absolutePath = "file:" + System.getProperty("user.dir") + "/" + uploadDir + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(absolutePath)
                .setCachePeriod(3600);
    }
}
