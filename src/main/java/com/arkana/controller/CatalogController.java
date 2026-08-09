package com.arkana.controller;

import com.arkana.dto.catalog.SpreadResponse;
import com.arkana.dto.catalog.TarotCardResponse;
import com.arkana.security.CurrentUser;
import com.arkana.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CatalogController {
  private final CatalogService catalog;
  private final CurrentUser currentUser;

  @GetMapping("/cards")
  List<TarotCardResponse> cards(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String deckMode,
      @RequestParam(required = false) String locale) {
    return catalog.cards(currentUser.id(jwt), deckMode, locale);
  }

  @GetMapping("/spreads")
  List<SpreadResponse> spreads(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String locale) {
    return catalog.spreads(currentUser.id(jwt), locale);
  }

  @GetMapping("/spreads/{spreadId}")
  SpreadResponse spread(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String spreadId,
      @RequestParam(required = false) String locale) {
    return catalog.spread(currentUser.id(jwt), spreadId, locale);
  }
}
