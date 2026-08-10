package com.arkana.config;

import com.arkana.config.http.HeaderVersionInterceptor;
import com.arkana.observability.RequestLoggingFilter;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
  private final HeaderVersionInterceptor headerVersionInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(headerVersionInterceptor);
  }

  @Bean
  FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
    FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new RequestLoggingFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(-99);
    registration.setName("arkanaRequestLoggingFilter");
    return registration;
  }
}
