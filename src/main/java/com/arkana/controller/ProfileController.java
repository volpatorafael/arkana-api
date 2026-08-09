package com.arkana.controller;

import com.arkana.dto.profile.ProfileResponse;
import com.arkana.dto.profile.UpdateProfileRequest;
import com.arkana.security.CurrentUser;
import com.arkana.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/profile")
@RequiredArgsConstructor
public class ProfileController {
  private final CurrentUser currentUser;
  private final ProfileService profiles;

  @GetMapping
  ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
    return profiles.get(currentUser.id(jwt), currentUser.email(jwt));
  }

  @PatchMapping
  ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return profiles.update(currentUser.id(jwt), currentUser.email(jwt), request);
  }
}
