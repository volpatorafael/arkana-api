package com.arkana.mapper;

import com.arkana.domain.Profile;
import com.arkana.dto.profile.ProfileResponse;

import org.mapstruct.Mapper;

@Mapper
public interface ProfileMapper {
  ProfileResponse toResponse(Profile profile);
}
