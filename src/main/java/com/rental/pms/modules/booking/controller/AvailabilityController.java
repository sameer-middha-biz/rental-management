package com.rental.pms.modules.booking.controller;

import com.rental.pms.modules.booking.dto.AvailabilityResponse;
import com.rental.pms.modules.booking.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only availability view. Mounted under the property resource path per Phase 5 spec.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Availability", description = "Check property availability windows")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/api/v1/properties/{id}/availability")
    @PreAuthorize("hasAuthority('PROPERTY_VIEW')")
    @Operation(summary = "List blocked ranges for a property over a date window")
    public ResponseEntity<AvailabilityResponse> getAvailability(
            @PathVariable("id") UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(availabilityService.getAvailability(propertyId, dateFrom, dateTo));
    }
}
