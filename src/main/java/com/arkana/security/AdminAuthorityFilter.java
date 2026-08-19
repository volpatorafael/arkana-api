package com.arkana.security;

import com.arkana.domain.AdminUser;
import com.arkana.repository.AdminUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AdminAuthorityFilter extends OncePerRequestFilter {
  private static final String ADMIN_PATH_PREFIX = "/v1/admin/";

  private final AdminUserRepository adminUsers;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(ADMIN_PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
      activeAdmin(authentication).ifPresent(admin -> grantAuthority(authentication, admin));
    }
    filterChain.doFilter(request, response);
  }

  private Optional<AdminUser> activeAdmin(JwtAuthenticationToken authentication) {
    try {
      UUID userId = UUID.fromString(authentication.getToken().getSubject());
      return adminUsers.findByUserIdAndActiveTrue(userId);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private void grantAuthority(JwtAuthenticationToken authentication, AdminUser admin) {
    List<GrantedAuthority> authorities = new ArrayList<>(authentication.getAuthorities());
    authorities.add(new SimpleGrantedAuthority("ROLE_" + admin.getRole().name()));
    JwtAuthenticationToken authorized = new JwtAuthenticationToken(
        authentication.getToken(),
        authorities,
        authentication.getName());
    authorized.setDetails(authentication.getDetails());
    SecurityContextHolder.getContext().setAuthentication(authorized);
  }
}
