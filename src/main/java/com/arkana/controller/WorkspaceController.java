package com.arkana.controller;

import com.arkana.dto.workspace.WorkspaceBootstrapResponse;
import com.arkana.security.CurrentUser;
import com.arkana.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/workspace")
@RequiredArgsConstructor
public class WorkspaceController {
    private final CurrentUser currentUser;
    private final WorkspaceService workspace;

    @PostMapping("/bootstrap")
    WorkspaceBootstrapResponse bootstrap(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String locale) {
        UUID userId = currentUser.id(jwt);
        String email = currentUser.email(jwt);
        try {
            return workspace.bootstrap(userId, email, locale);
        } catch (DataIntegrityViolationException raceOnFirstBootstrap) {
            // Two concurrent first-ever bootstrap calls for a brand-new user (e.g. two
            // tabs, or a double request right after email confirmation) can both try to
            // insert the same profile/billing-account row; the loser retries against the
            // row the winner already committed instead of surfacing a 409 to the client.
            return workspace.bootstrap(userId, email, locale);
        }
    }
}
