package com.rental.pms.modules.pricing.controller;

import com.rental.pms.modules.pricing.dto.PriceBreakdownResponse;
import com.rental.pms.modules.pricing.dto.PriceCalculationRequest;
import com.rental.pms.modules.pricing.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pricing endpoints. Mounted under /api/v1/bookings (per Phase 5 spec) but kept in its own
 * controller so booking workflow and pricing-engine concerns stay separable.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Quote a booking before creating it")
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/api/v1/bookings/calculate-price")
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    @Operation(summary = "Calculate a price breakdown for a potential booking (no side effects)")
    public ResponseEntity<PriceBreakdownResponse> calculate(
            @Valid @RequestBody PriceCalculationRequest request) {
        return ResponseEntity.ok(pricingService.calculatePrice(request));
    }
}
