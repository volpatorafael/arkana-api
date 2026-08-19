package com.arkana.service;

import com.arkana.domain.Profile;
import com.arkana.dto.admin.AdminUserResponse;
import com.arkana.integration.supabase.SupabaseAuthAdminClient;
import com.arkana.integration.supabase.dto.SupabaseAuthUser;
import com.arkana.repository.ClientRepository;
import com.arkana.repository.ProfileRepository;
import com.arkana.repository.ReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final ProfileRepository profiles;
    private final ClientRepository clients;
    private final ReadingRepository readings;
    private final SupabaseAuthAdminClient supabaseAuthAdminClient;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(int limit, int offset) {
        int page = (offset / Math.max(limit, 1)) + 1;
        List<SupabaseAuthUser> authUsers = supabaseAuthAdminClient.listUsers(page, limit);

        List<UUID> ids = authUsers.stream().map(SupabaseAuthUser::id).toList();

        Map<UUID, Profile> profileById = profiles.findAllById(ids).stream()
            .collect(Collectors.toMap(Profile::getId, Function.identity()));

        return authUsers.stream()
            .map(auth -> {
                Profile p = profileById.get(auth.id());
                boolean hasProfile = p != null;
                String email = hasProfile && p.getEmail() != null ? p.getEmail() : auth.email();
                String displayName = hasProfile ? p.getDisplayName() : null;
                int clientCount = (int) clients.countByOwnerId(auth.id());
                int readingCount = (int) readings.countByOwnerId(auth.id());
                return new AdminUserResponse(
                    auth.id(),
                    email,
                    displayName,
                    auth.createdAt(),
                    auth.lastSignInAt(),
                    hasProfile,
                    clientCount,
                    readingCount
                );
            })
            .toList();
    }

    @Transactional
    public void deleteUser(UUID userId) {
        // Delete profile if present (will throw DataIntegrityViolation if referenced by clients/readings)
        profiles.deleteById(userId);

        // Then delete from Supabase Auth (not reached on constraint violation)
        try {
            supabaseAuthAdminClient.deleteUser(userId);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete user from auth provider", e);
        }
    }
}
