package com.skysentry.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serve the frontend (index.html) from the project root.
 * This eliminates CORS issues by running everything under the same origin.
 *
 * index.html lives at: ../index.html (relative to backend-spring/)
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve index.html and any other frontend files from the project root
        Path projectRoot = Paths.get("..").toAbsolutePath().normalize();
        String rootUri = "file:///" + projectRoot.toString().replace('\\', '/') + "/";

        registry.addResourceHandler("/index.html")
                .addResourceLocations(rootUri);

        // Also serve at root "/"
        registry.addResourceHandler("/app/**")
                .addResourceLocations(rootUri);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Redirect root "/" to index.html
        registry.addRedirectViewController("/", "/index.html");
    }
}
