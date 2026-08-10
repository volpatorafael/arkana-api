package com.arkana.config.http;

import com.arkana.ArkanaApplication;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class HeaderVersionInterceptor implements HandlerInterceptor {
  public static final String VERSION_HEADER = "X-API-Version";

  private String version;

  @PostConstruct
  public void init() {
    version = ArkanaApplication.class.getPackage().getImplementationVersion();
    if (version == null) {
      version = "snapshot";
    }
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    response.setHeader(VERSION_HEADER, version);
    return true;
  }
}
