package com.arkana.service;

import com.arkana.domain.Profile;
import com.arkana.dto.profile.ProfileResponse;
import com.arkana.dto.profile.UpdateProfileRequest;
import com.arkana.mapper.ProfileMapper;
import com.arkana.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
  private final ProfileRepository repository;
  private final ProfileMapper mapper;

  @Transactional
  public ProfileResponse get(UUID userId, String email) {
    return mapper.toResponse(findOrCreate(userId, email));
  }

  @Transactional
  public ProfileResponse update(UUID userId, String email, UpdateProfileRequest request) {
    if (!request.isDisplayNamePresent() && !request.isLocalePresent()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one editable field is required.");
    }
    Profile profile = findOrCreate(userId, email);
    if (request.isDisplayNamePresent()) {
      String displayName = request.getDisplayName();
      profile.updateDisplayName(displayName == null ? null : normalized(displayName, "displayName"));
    }
    if (request.isLocalePresent()) {
      profile.updateLocale(request.getLocale());
    }
    repository.flush();
    return mapper.toResponse(profile);
  }

  Profile find(UUID userId) {
    return repository.findById(userId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found."));
  }

  private Profile findOrCreate(UUID userId, String email) {
    return repository.findById(userId)
        .orElseGet(() -> {
          OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
          return repository.save(Profile.builder()
              .id(userId)
              .email(email)
              .locale("pt-BR")
              .createdAt(now)
              .updatedAt(now)
              .build());
        });
  }

  private String normalized(String value, String field) {
    String normalized = value.trim();
    if (normalized.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank.");
    }
    return normalized;
  }

}
