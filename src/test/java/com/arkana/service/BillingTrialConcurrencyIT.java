package com.arkana.service;

import com.arkana.BaseIT;
import com.arkana.domain.Profile;
import com.arkana.dto.billing.BillingOverview;
import com.arkana.repository.BillingAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BillingTrialConcurrencyIT extends BaseIT {
  @Autowired
  private BillingService billingService;
  @Autowired
  private BillingAccountRepository accounts;

  @Test
  void shouldSerializeConcurrentTrialStartsForTheSameProfile() throws Exception {
    Profile profile = entityGeneratorService.randomProfile();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      List<Future<BillingOverview>> results = List.of(
          executor.submit(() -> startTrialWhenReleased(profile, ready, start)),
          executor.submit(() -> startTrialWhenReleased(profile, ready, start)));

      ready.await();
      start.countDown();

      assertThat(results)
          .allSatisfy(result -> assertThat(result.get().status())
              .isEqualTo("TRIALING"));
      assertThat(accounts.findByOwnerId(profile.getId())).isPresent();
      assertThat(accounts.findAll().stream()
          .filter(account -> account.getOwnerId().equals(profile.getId())))
          .hasSize(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private BillingOverview startTrialWhenReleased(
      Profile profile,
      CountDownLatch ready,
      CountDownLatch start) throws InterruptedException {
    ready.countDown();
    start.await();
    return billingService.startTrial(profile.getId());
  }
}
