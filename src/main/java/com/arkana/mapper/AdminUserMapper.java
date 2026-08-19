package com.arkana.mapper;

import com.arkana.domain.Profile;
import com.arkana.dto.admin.AdminUserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AdminUserMapper {

  @Mapping(target = "lastSignInAt", ignore = true)
  AdminUserResponse toAdminUser(Profile profile);
}
