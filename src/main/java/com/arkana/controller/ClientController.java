package com.arkana.controller;

import com.arkana.dto.client.ClientPageResponse;
import com.arkana.dto.client.ClientResponse;
import com.arkana.dto.client.SaveClientRequest;
import com.arkana.security.CurrentUser;
import com.arkana.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/clients")
@RequiredArgsConstructor
public class ClientController {
  private final ClientService clients;
  private final CurrentUser currentUser;

  @GetMapping
  ClientPageResponse list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "25") int pageSize,
      @RequestParam(defaultValue = "false") boolean archived) {
    return clients.list(currentUser.id(jwt), page, pageSize, archived);
  }

  @PostMapping
  ResponseEntity<ClientResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody SaveClientRequest request) {
    return ResponseEntity.status(201).body(clients.create(currentUser.id(jwt), request));
  }

  @GetMapping("/{clientId}")
  ClientResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
    return clients.get(currentUser.id(jwt), clientId);
  }

  @PutMapping("/{clientId}")
  ClientResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID clientId,
      @Valid @RequestBody SaveClientRequest request) {
    return clients.update(currentUser.id(jwt), clientId, request);
  }

  @DeleteMapping("/{clientId}")
  ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
    clients.delete(currentUser.id(jwt), clientId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{clientId}/archive")
  ClientResponse archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
    return clients.archive(currentUser.id(jwt), clientId);
  }

  @PostMapping("/{clientId}/restore")
  ClientResponse restore(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID clientId) {
    return clients.restore(currentUser.id(jwt), clientId);
  }
}
