package com.arkana.security;

import com.arkana.config.ArkanaProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static com.arkana.exception.GlobalExceptionHandler.ERROR_MESSAGE;

@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  private static void writeError(
      String requestUri,
      HttpServletResponse response,
      HttpStatus status,
      String detail,
      ObjectMapper objectMapper)
      throws IOException {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(requestUri));
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(objectMapper.writeValueAsString(problem));
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/favicon.ico").permitAll()
            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
            .requestMatchers(
                "/v1/public/**",
                "/v1/webhook/payment/**")
            .permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
            .authenticationEntryPoint((request, response, exception) -> {
              log.info(ERROR_MESSAGE, request.getMethod(), request.getRequestURI(), HttpStatus.UNAUTHORIZED, "Unauthorized",
                  exception.getMessage());
              writeError(
                  request.getRequestURI(),
                  response,
                  HttpStatus.UNAUTHORIZED,
                  "A valid access token is required.",
                  objectMapper);
            }))
        .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> writeError(
            request.getRequestURI(),
            response,
            HttpStatus.FORBIDDEN,
            "The authenticated user cannot perform this operation.",
            objectMapper)))
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(ArkanaProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.cors().allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of(
        HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
        "Idempotency-Key", "X-Webhook-Signature", "asaas-access-token"));
    configuration.setExposedHeaders(List.of("X-Request-Id", "X-API-Version"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
