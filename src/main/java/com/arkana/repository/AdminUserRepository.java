package com.arkana.repository;

import com.arkana.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
  @Query("select count(admin) from AdminUser admin")
  long countAll();

  Optional<AdminUser> findByUserIdAndActiveTrue(UUID userId);
}
