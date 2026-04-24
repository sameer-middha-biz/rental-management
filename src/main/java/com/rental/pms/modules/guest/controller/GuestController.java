package com.rental.pms.modules.guest.controller;

import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.modules.guest.dto.CreateGuestRequest;
import com.rental.pms.modules.guest.dto.GuestResponse;
import com.rental.pms.modules.guest.dto.UpdateGuestRequest;
import com.rental.pms.modules.guest.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
@Tag(name = "Guests", description = "Manage guest CRM records (GDPR-aware)")
public class GuestController {

    private final GuestService guestService;

    @PostMapping
    @PreAuthorize("hasAuthority('GUEST_CREATE')")
    @Operation(summary = "Create a new guest")
    public ResponseEntity<GuestResponse> create(@Valid @RequestBody CreateGuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GUEST_VIEW')")
    @Operation(summary = "List guests (optional name substring search)")
    public ResponseEntity<PageResponse<GuestResponse>> list(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(guestService.search(search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('GUEST_VIEW')")
    @Operation(summary = "Get guest by ID")
    public ResponseEntity<GuestResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(guestService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GUEST_EDIT')")
    @Operation(summary = "Update a guest (partial — null fields ignored)")
    public ResponseEntity<GuestResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateGuestRequest request) {
        return ResponseEntity.ok(guestService.update(id, request));
    }

    @DeleteMapping("/{id}/gdpr")
    @PreAuthorize("hasAuthority('GUEST_MANAGE')")
    @Operation(summary = "GDPR erasure — anonymise PII in place and delete ID document from S3")
    public ResponseEntity<Void> gdprErase(@PathVariable UUID id) {
        guestService.gdprErase(id);
        return ResponseEntity.noContent().build();
    }
}
