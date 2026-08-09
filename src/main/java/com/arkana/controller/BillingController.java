package com.arkana.controller;

import com.arkana.dto.billing.BillingCheckoutResponse;
import com.arkana.dto.billing.BillingOverview;
import com.arkana.dto.billing.ChangeBillingPlanRequest;
import com.arkana.dto.billing.CreateBillingCheckoutRequest;
import com.arkana.dto.billing.SubscriptionPlanResponse;
import com.arkana.security.CurrentUser;
import com.arkana.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class BillingController {
  private final BillingService billing;
  private final CurrentUser currentUser;

  @PostMapping("/billing/trial")
  BillingOverview trial(@AuthenticationPrincipal Jwt jwt) {
    return billing.startTrial(currentUser.id(jwt));
  }

  @GetMapping("/billing/plans")
  List<SubscriptionPlanResponse> eligible(@AuthenticationPrincipal Jwt jwt) {
    return billing.eligiblePlans(currentUser.id(jwt));
  }

  @GetMapping("/billing/subscription")
  BillingOverview subscription(@AuthenticationPrincipal Jwt jwt) {
    return billing.overview(currentUser.id(jwt));
  }

  @PostMapping("/billing/checkouts")
  ResponseEntity<BillingCheckoutResponse> checkout(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody CreateBillingCheckoutRequest body) {
    return ResponseEntity.status(201).body(billing.checkout(currentUser.id(jwt), key, body));
  }

  @PostMapping("/billing/subscription/cancel")
  BillingOverview cancel(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key) {
    return billing.cancel(currentUser.id(jwt));
  }

  @PostMapping("/billing/subscription/change-plan")
  BillingOverview change(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody ChangeBillingPlanRequest body) {
    return billing.changePlan(currentUser.id(jwt), body);
  }
}
