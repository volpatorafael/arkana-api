package com.arkana.repository;

import com.arkana.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BillingAccountRepository extends JpaRepository<BillingAccount, UUID> {
  Optional<BillingAccount> findByOwnerId(UUID ownerId);

  boolean existsByOwnerId(UUID ownerId);

  boolean existsByTrialEmailFingerprint(String fingerprint);

}
