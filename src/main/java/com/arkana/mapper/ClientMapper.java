package com.arkana.mapper;

import com.arkana.domain.Client;
import com.arkana.dto.client.ClientPageResponse;
import com.arkana.dto.client.ClientResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface ClientMapper {
  ClientResponse toResponse(Client client);

  List<ClientResponse> toResponses(List<Client> clients);

  @Mapping(target = "items", source = "clients")
  @Mapping(target = "page", source = "page")
  @Mapping(target = "pageSize", source = "pageSize")
  @Mapping(target = "total", source = "total")
  ClientPageResponse toPage(
      List<Client> clients,
      int page,
      int pageSize,
      long total);
}
