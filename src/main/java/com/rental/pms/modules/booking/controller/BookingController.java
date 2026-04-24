package com.rental.pms.modules.booking.controller;

import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.modules.booking.dto.BookingResponse;
import com.rental.pms.modules.booking.dto.CreateBookingRequest;
import com.rental.pms.modules.booking.dto.UpdateBookingRequest;
import com.rental.pms.modules.booking.dto.UpdateBookingStatusRequest;
import com.rental.pms.modules.booking.entity.BookingStatus;
import com.rental.pms.modules.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Create and manage reservations")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOKING_CREATE')")
    @Operation(summary = "Create a booking (atomic: locks availability, prices, inserts, publishes event)")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BOOKING_VIEW')")
    @Operation(summary = "List bookings (optionally filter by property or status)")
    public ResponseEntity<PageResponse<BookingResponse>> list(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) BookingStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(bookingService.list(propertyId, status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_VIEW')")
    @Operation(summary = "Get a booking by id, including nightly rate breakdown")
    public ResponseEntity<BookingResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BOOKING_EDIT')")
    @Operation(summary = "Update editable fields (guest count, notes). Does not re-price or re-lock.")
    public ResponseEntity<BookingResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateBookingRequest request) {
        return ResponseEntity.ok(bookingService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('BOOKING_EDIT') or hasAuthority('BOOKING_CANCEL')")
    @Operation(summary = "Change booking status (PENDING -> CONFIRMED -> CHECKED_IN -> CHECKED_OUT; CANCELLED/DECLINED are terminal)")
    public ResponseEntity<BookingResponse> changeStatus(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateBookingStatusRequest request) {
        return ResponseEntity.ok(bookingService.changeStatus(id, request));
    }
}
