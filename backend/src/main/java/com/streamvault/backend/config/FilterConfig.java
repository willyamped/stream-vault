package com.streamvault.backend.config;

import com.streamvault.backend.middleware.RateLimiterFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final RateLimiterFilter rateLimiterFilter;

    @Bean
    public FilterRegistrationBean<RateLimiterFilter> registerRateLimiterFilter() {
        FilterRegistrationBean<RateLimiterFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(rateLimiterFilter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
