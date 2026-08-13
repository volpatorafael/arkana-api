package com.arkana.service;

import com.arkana.domain.Client;
import com.arkana.dto.client.ClientPageResponse;
import com.arkana.dto.client.ClientResponse;
import com.arkana.dto.client.SaveClientRequest;
import com.arkana.mapper.ClientMapper;
import com.arkana.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {
  private final ProductAccessAuthorizer access;
  private final ClientRepository repository;
  private final ClientMapper mapper;

  long countActiveForAuthorizedUser(UUID userId) {
    return repository.countByOwnerIdAndArchivedAtIsNull(userId);
  }

  @Transactional(readOnly = true)
  public ClientPageResponse list(UUID userId, int page, int pageSize, boolean archived) {
    access.requireAccess(userId);
    validatePage(page, pageSize);
    Pageable pageable = PageRequest.of(
        page - 1,
        pageSize,
        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    Page<Client> result = archived
        ? repository.findAllByOwnerIdAndArchivedAtIsNotNull(userId, pageable)
        : repository.findAllByOwnerIdAndArchivedAtIsNull(userId, pageable);
    return mapper.toPage(result.getContent(), page, pageSize, result.getTotalElements());
  }

  @Transactional
  public ClientResponse create(UUID userId, SaveClientRequest request) {
    access.requireAccess(userId);
    Client client = Client.builder()
        .id(UUID.randomUUID())
        .ownerId(userId)
        .name(normalizedName(request.name()))
        .birthDate(request.birthDate())
        .email(nullable(request.email()))
        .phone(nullable(request.phone()))
        .notes(nullable(request.notes()))
        .build();
    return mapper.toResponse(repository.save(client));
  }

  @Transactional(readOnly = true)
  public ClientResponse get(UUID userId, UUID clientId) {
    access.requireAccess(userId);
    return mapper.toResponse(find(userId, clientId));
  }

  @Transactional
  public ClientResponse update(UUID userId, UUID clientId, SaveClientRequest request) {
    access.requireAccess(userId);
    Client client = find(userId, clientId);
    client.update(
        normalizedName(request.name()),
        request.birthDate(),
        nullable(request.email()),
        nullable(request.phone()),
        nullable(request.notes()));
    repository.flush();
    return mapper.toResponse(client);
  }

  @Transactional
  public void delete(UUID userId, UUID clientId) {
    access.requireAccess(userId);
    Client client = find(userId, clientId);
    try {
      repository.delete(client);
      repository.flush();
    } catch (DataIntegrityViolationException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "A client with reading history cannot be deleted.");
    }
  }

  @Transactional
  public ClientResponse archive(UUID userId, UUID clientId) {
    access.requireAccess(userId);
    Client client = find(userId, clientId);
    client.archive();
    repository.flush();
    return mapper.toResponse(client);
  }

  @Transactional
  public ClientResponse restore(UUID userId, UUID clientId) {
    access.requireAccess(userId);
    Client client = find(userId, clientId);
    client.restore();
    repository.flush();
    return mapper.toResponse(client);
  }

  private Client find(UUID userId, UUID clientId) {
    return repository.findByIdAndOwnerId(clientId, userId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found."));
  }

  private void validatePage(int page, int pageSize) {
    if (page < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be a positive integer.");
    }
    if (pageSize < 1 || pageSize > 100) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "pageSize must be an integer between 1 and 100.");
    }
  }

  private String normalizedName(String value) {
    return value.trim();
  }

  private String nullable(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }

}
