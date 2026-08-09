package com.arkana.mapper;

import com.arkana.domain.Client;
import com.arkana.dto.client.ClientResponse;

import org.mapstruct.Mapper;

@Mapper
public interface ClientMapper {
  ClientResponse toResponse(Client client);
}
