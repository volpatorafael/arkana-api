package com.arkana.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_TAG = "REQ";
    private static final String RESPONSE_TAG = "RES";
    private static final String REQUEST_MESSAGE = "{} [{} {}] user={}";
    private static final String RESPONSE_MESSAGE = "{} [{} {}] status={} duration={} user={}";
    private static final String RESPONSE_MESSAGE_WITH_IP = "{} [{} {}] status={} duration={} user={} ip={}";
    private static final String MANAGEMENT_PATH_PREFIX = "/actuator";
    private static final String API_PATH_PREFIX = "/v1/";
    private static final String UNKNOWN = "unknown";
    private static final Pattern SECRET_QUERY_PARAMETER = Pattern.compile(
            "(?i)(webhookSecret|password|secret|token|key)=([^&]*)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String uri = requestUri(request);
        boolean management = request.getRequestURI().startsWith(MANAGEMENT_PATH_PREFIX);
        String user = username();
        String ip = clientIp(request);

        if (request.getRequestURI().startsWith(API_PATH_PREFIX)) {
            log.info(REQUEST_MESSAGE, REQUEST_TAG, method, uri, user);
        } else if (management) {
            log.trace(REQUEST_MESSAGE, REQUEST_TAG, method, uri, user);
        } else {
            log.debug(REQUEST_MESSAGE, REQUEST_TAG, method, uri, user);
        }

        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = System.currentTimeMillis() - startedAt;
            logResponse(method, uri, user, ip, response.getStatus(), durationMillis, management);
        }
    }

    private void logResponse(
            String method,
            String uri,
            String user,
            String ip,
            int status,
            long durationMillis,
            boolean management) {
        if (management) {
            return;
        }
        String duration = String.format(
                "%d:%02d.%03d",
                durationMillis / 60_000,
                (durationMillis % 60_000) / 1_000,
                durationMillis % 1_000);
        if (durationMillis >= 10_000) {
            log.warn(RESPONSE_MESSAGE_WITH_IP, RESPONSE_TAG, method, uri, status, duration, user, ip);
        } else if (durationMillis >= 6_000) {
            log.info(RESPONSE_MESSAGE_WITH_IP, RESPONSE_TAG, method, uri, status, duration, user, ip);
        } else if (durationMillis >= 2_000) {
            log.debug(RESPONSE_MESSAGE, RESPONSE_TAG, method, uri, status, duration, user);
        } else if (durationMillis >= 1_000) {
            log.trace(RESPONSE_MESSAGE, RESPONSE_TAG, method, uri, status, duration, user);
        }
    }

    private String requestUri(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null) {
            return request.getRequestURI();
        }
        String sanitizedQuery = SECRET_QUERY_PARAMETER.matcher(query).replaceAll("$1=[REDACTED]");
        return request.getRequestURI() + "?" + sanitizedQuery;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? UNKNOWN : ip;
    }

    private String username() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return Optional.ofNullable(authentication)
                    .filter(Authentication::isAuthenticated)
                    .map(Authentication::getName)
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.trace("Security context was not available while logging the request.", exception);
            return null;
        }
    }
}
