package com.arkana.repository;

import com.arkana.domain.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
  Optional<Client> findByIdAndOwnerId(UUID id, UUID ownerId);

  Page<Client> findAllByOwnerIdAndArchivedAtIsNull(UUID ownerId, Pageable pageable);

  Page<Client> findAllByOwnerIdAndArchivedAtIsNotNull(UUID ownerId, Pageable pageable);
}
