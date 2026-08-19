package com.arkana.controller;

import com.arkana.dto.admin.AdminDeckResponse;
import com.arkana.dto.admin.AdminTarotCardResponse;
import com.arkana.dto.admin.CreateAdminDeckRequest;
import com.arkana.dto.admin.CreateAdminTarotCardRequest;
import com.arkana.dto.admin.UpdateAdminDeckRequest;
import com.arkana.dto.admin.UpdateAdminTarotCardRequest;
import com.arkana.service.AdminCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {
  private final AdminCatalogService adminCatalog;

  @GetMapping("/decks")
  List<AdminDeckResponse> listDecks() {
    return adminCatalog.listDecks();
  }

  @PostMapping("/decks")
  ResponseEntity<AdminDeckResponse> createDeck(@Valid @RequestBody CreateAdminDeckRequest req) {
    AdminDeckResponse created = adminCatalog.createDeck(req);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/decks/{deckId}")
  AdminDeckResponse getDeck(@PathVariable String deckId) {
    return adminCatalog.getDeck(deckId);
  }

  @PutMapping("/decks/{deckId}")
  AdminDeckResponse updateDeck(@PathVariable String deckId, @Valid @RequestBody UpdateAdminDeckRequest req) {
    return adminCatalog.updateDeck(deckId, req);
  }

  @GetMapping("/decks/{deckId}/cards")
  List<AdminTarotCardResponse> listCards(@PathVariable String deckId) {
    return adminCatalog.listCards(deckId);
  }

  @PostMapping("/decks/{deckId}/cards")
  ResponseEntity<AdminTarotCardResponse> createCard(
      @PathVariable String deckId,
      @Valid @RequestBody CreateAdminTarotCardRequest req) {
    AdminTarotCardResponse created = adminCatalog.createCard(deckId, req);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/decks/{deckId}/cards/{cardId}")
  AdminTarotCardResponse updateCard(
      @PathVariable String deckId,
      @PathVariable String cardId,
      @Valid @RequestBody UpdateAdminTarotCardRequest req) {
    return adminCatalog.updateCard(deckId, cardId, req);
  }

  @DeleteMapping("/decks/{deckId}/cards/{cardId}")
  ResponseEntity<Void> deleteCard(
      @PathVariable String deckId,
      @PathVariable String cardId) {
    adminCatalog.deleteCard(deckId, cardId);
    return ResponseEntity.noContent().build();
  }
}
