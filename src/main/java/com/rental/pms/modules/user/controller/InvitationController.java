package com.rental.pms.modules.user.controller;

import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.modules.user.dto.AcceptInviteRequest;
import com.rental.pms.modules.user.dto.AuthResponse;
import com.rental.pms.modules.user.dto.InvitationResponse;
import com.rental.pms.modules.user.dto.InviteUserRequest;
import com.rental.pms.modules.user.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Team member invitation management")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER_INVITE')")
    @Operation(summary = "Invite a team member by email")
    public ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InviteUserRequest request) {
        InvitationResponse response = invitationService.invite(request);
        return ResponseEntity.created(URI.create("/api/v1/invitations/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_INVITE')")
    @Operation(summary = "List pending invitations")
    public ResponseEntity<PageResponse<InvitationResponse>> getInvitations(Pageable pageable) {
        return ResponseEntity.ok(invitationService.getInvitations(pageable));
    }

    @PostMapping("/{token}/accept")
    @Operation(summary = "Accept invitation and create account")
    public ResponseEntity<AuthResponse> acceptInvite(
            @PathVariable String token,
            @Valid @RequestBody AcceptInviteRequest request) {
        AuthResponse response = invitationService.acceptInvite(token, request);
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_INVITE')")
    @Operation(summary = "Revoke invitation")
    public ResponseEntity<Void> revokeInvitation(@PathVariable UUID id) {
        invitationService.revokeInvitation(id);
        return ResponseEntity.noContent().build();
    }
}
