package com.arkana.integration;

import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.BillingProvider;
import com.arkana.integration.dto.PaymentWebhookEvent;

import java.time.OffsetDateTime;
import java.util.Set;

public interface PaymentProvider {
  BillingProvider provider();

  Set<BillingPaymentMethod> supportedPaymentMethods();

  boolean requiresPlanMapping();

  default boolean requiresPlanMapping(BillingPaymentMethod paymentMethod) {
    return requiresPlanMapping();
  }

  default boolean supportsDeferredFirstCharge() {
    return false;
  }

  Checkout createCheckout(CreateCheckout command);

  void cancel(String subscriptionId);

  default void cancel(String subscriptionId, BillingPaymentMethod paymentMethod) {
    cancel(subscriptionId);
  }

  void changePlan(ChangePlan command);

  default void updateFutureCharge(
      String subscriptionId,
      String chargeId,
      OffsetDateTime dueAt,
      int amount) {
  }

  PaymentWebhookEvent verifyWebhook(byte[] rawBody, String signature);

  record Checkout(
      String providerId,
      String subscriptionId,
      ActionType actionType,
      String url,
      String copyPasteCode,
      String qrCodeImage,
      OffsetDateTime expiresAt) {
    public Checkout(String providerId, String url, OffsetDateTime expiresAt) {
      this(providerId, null, ActionType.REDIRECT, url, null, null, expiresAt);
    }
  }

  enum ActionType {
    REDIRECT,
    PENDING_CONFIRMATION,
    PIX_QR_CODE
  }

  record Plan(
      String id,
      String code,
      String name,
      String interval,
      int amount,
      String currency,
      String providerProductId) {
  }

  record CreateCheckout(
      String accountId,
      String checkoutId,
      String email,
      BillingPaymentMethod paymentMethod,
      Plan plan,
      OffsetDateTime firstChargeAt,
      Payer payer,
      String paymentToken) {
    public CreateCheckout(
        String accountId,
        String checkoutId,
        BillingPaymentMethod paymentMethod,
        Plan plan,
        OffsetDateTime firstChargeAt) {
      this(accountId, checkoutId, null, paymentMethod, plan, firstChargeAt, null, null);
    }
  }

  record Payer(String name, String document, String phoneNumber, Address billingAddress) {
  }

  record Address(
      String street,
      String number,
      String neighborhood,
      String zipCode,
      String city,
      String state,
      String complement) {
  }

  record ChangePlan(
      String subscriptionId,
      Plan plan,
      OffsetDateTime nextChargeAt,
      boolean updatePendingPayments,
      BillingPaymentMethod paymentMethod) {
    public ChangePlan(
        String subscriptionId,
        Plan plan,
        OffsetDateTime nextChargeAt,
        boolean updatePendingPayments) {
      this(subscriptionId, plan, nextChargeAt, updatePendingPayments, null);
    }
  }

}
