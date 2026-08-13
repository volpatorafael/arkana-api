package com.arkana.controller;

import com.arkana.dto.workspace.WorkspaceBootstrapResponse;
import com.arkana.security.CurrentUser;
import com.arkana.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        return workspace.bootstrap(
            currentUser.id(jwt),
            currentUser.email(jwt),
            locale);
    }
}
