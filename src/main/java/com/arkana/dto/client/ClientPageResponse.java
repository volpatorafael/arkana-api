package com.arkana.dto.client;

import java.util.List;

public record ClientPageResponse(List<ClientResponse> items, int page, int pageSize, long total) {
}
