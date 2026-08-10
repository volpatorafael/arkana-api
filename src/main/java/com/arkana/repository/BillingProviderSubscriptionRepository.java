package com.arkana.repository;

import com.arkana.domain.BillingProvider;
import com.arkana.domain.BillingProviderSubscription;
import com.arkana.domain.BillingProviderSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;
import java.util.List;
import com.arkana.domain.BillingPaymentMethod;

public interface BillingProviderSubscriptionRepository extends JpaRepository<BillingProviderSubscription, UUID> {
  Optional<BillingProviderSubscription> findByBillingAccountIdAndProvider(
      UUID accountId,
      BillingProvider provider);

  Optional<BillingProviderSubscription> findByProviderAndProviderSubscriptionId(
      BillingProvider provider,
      String subscriptionId);

  Optional<BillingProviderSubscription> findFirstByBillingAccountId(UUID accountId);

  Optional<BillingProviderSubscription> findFirstByBillingAccountIdAndStatusIn(
      UUID accountId,
      Collection<BillingProviderSubscriptionStatus> statuses);

  List<BillingProviderSubscription> findAllByProviderAndPaymentMethodAndStatusIn(
      BillingProvider provider,
      BillingPaymentMethod paymentMethod,
      Collection<BillingProviderSubscriptionStatus> statuses);
}
