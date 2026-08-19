package com.arkana.integration.supabase;

import com.arkana.config.SupabaseAdminProperties;
import com.arkana.integration.supabase.dto.SupabaseAuthUser;
import com.arkana.integration.supabase.dto.SupabaseUsersResponse;
import com.arkana.integration.supabase.dto.SupabaseUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Component
public class SupabaseAuthAdminClient {

    private final SupabaseAdminProperties properties;
    private final RestClient restClient;

    public SupabaseAuthAdminClient(SupabaseAdminProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    public void deleteUser(UUID userId) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Supabase admin not configured");
        }

        String url = properties.normalizedUrl() + "/auth/v1/admin/users/" + userId;

        restClient.delete()
            .uri(url)
            .header("apikey", properties.secretKey())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
            .retrieve()
            .toBodilessEntity();
    }

    public List<SupabaseAuthUser> listUsers(int page, int perPage) {
        if (!properties.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Supabase admin not configured");
        }
        try {
            SupabaseUsersResponse response = restClient.get()
                .uri(properties.normalizedUrl() + "/auth/v1/admin/users?page={page}&per_page={perPage}", page, perPage)
                .header("apikey", properties.secretKey())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                .retrieve()
                .body(SupabaseUsersResponse.class);
            if (response == null || response.users() == null) {
                return List.of();
            }
            return response.users().stream()
                .map(u -> new SupabaseAuthUser(u.id(), u.email(), u.createdAt(), u.lastSignInAt()))
                .toList();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to list auth users", e);
        }
    }
}
