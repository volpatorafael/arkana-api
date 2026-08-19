package com.arkana.repository;

import com.arkana.domain.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
  Optional<Client> findByIdAndOwnerId(UUID id, UUID ownerId);

  Page<Client> findAllByOwnerIdAndArchivedAtIsNull(UUID ownerId, Pageable pageable);

  Page<Client> findAllByOwnerIdAndArchivedAtIsNotNull(UUID ownerId, Pageable pageable);

  @Query("""
      select new com.arkana.repository.AdminUserEventProjection(client.ownerId, client.updatedAt)
      from Client client
      where client.updatedAt >= :from and client.updatedAt < :to
      """)
  List<AdminUserEventProjection> findAdminActivityEvents(
      @Param("from") OffsetDateTime from,
      @Param("to") OffsetDateTime to);

  @Query("select distinct client.ownerId from Client client where client.ownerId in :ownerIds")
  List<UUID> findDistinctOwnerIdsWithClients(@Param("ownerIds") Collection<UUID> ownerIds);
}
