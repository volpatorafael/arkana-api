package com.arkana.repository;

import com.arkana.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface BillingAccountRepository extends JpaRepository<BillingAccount, UUID> {
  Optional<BillingAccount> findByOwnerId(UUID ownerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select account from BillingAccount account where account.ownerId = :ownerId")
  Optional<BillingAccount> findByOwnerIdForUpdate(@Param("ownerId") UUID ownerId);

  boolean existsByOwnerId(UUID ownerId);

  boolean existsByTrialEmailFingerprint(String fingerprint);

}
