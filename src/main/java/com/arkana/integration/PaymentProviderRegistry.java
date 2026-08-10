package com.arkana.integration;

import com.arkana.domain.BillingProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PaymentProviderRegistry {
  private final Map<BillingProvider, PaymentProvider> providers;
  private final Environment environment;

  public PaymentProviderRegistry(
      @Qualifier("abacatePayProvider") PaymentProvider abacatePay,
      @Qualifier("asaasProvider") PaymentProvider asaas,
      Environment environment) {
    providers = Map.of(
        BillingProvider.ABACATEPAY, abacatePay,
        BillingProvider.ASAAS, asaas);
    this.environment = environment;
  }

  public BillingProvider selectedProvider() {
    return environment.getProperty(
        "arkana.billing.provider",
        BillingProvider.class,
        BillingProvider.ABACATEPAY);
  }

  public PaymentProvider selected() {
    return get(selectedProvider());
  }

  public PaymentProvider get(BillingProvider provider) {
    PaymentProvider paymentProvider = providers.get(provider);
    if (paymentProvider == null) {
      throw new IllegalStateException("Unsupported billing provider: " + provider);
    }
    return paymentProvider;
  }
}
