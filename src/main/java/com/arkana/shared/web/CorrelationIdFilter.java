package com.arkana.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-101)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            chain.doFilter(request, response);
        }
    }
}
