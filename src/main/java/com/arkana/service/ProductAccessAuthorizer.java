package com.arkana.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductAccessAuthorizer {
  private final BillingAccessEvaluator billing;

  @Transactional(readOnly = true)
  public void requireAccess(UUID userId) {
    if (!billing.hasActiveAccess(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active trial or subscription is required.");
    }
  }
}
