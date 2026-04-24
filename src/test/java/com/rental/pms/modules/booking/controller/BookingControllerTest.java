package com.rental.pms.modules.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.booking.dto.BookingResponse;
import com.rental.pms.modules.booking.dto.CreateBookingRequest;
import com.rental.pms.modules.booking.dto.UpdateBookingStatusRequest;
import com.rental.pms.modules.booking.entity.BookingSource;
import com.rental.pms.modules.booking.entity.BookingStatus;
import com.rental.pms.modules.booking.service.BookingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BookingController Tests")
class BookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private BookingService bookingService;

    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private TenantFilter tenantFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;

    private BookingResponse sample(UUID id) {
        return new BookingResponse(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "BK-ABC123",
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                BookingStatus.CONFIRMED, 2, 30000L, "GBP",
                BookingSource.DIRECT, null, null, null, null, null,
                List.of(), Instant.now(), Instant.now());
    }

    @Test
    @WithMockUser(authorities = "BOOKING_CREATE")
    @DisplayName("POST /api/v1/bookings returns 201")
    void create_Returns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(bookingService.create(any())).willReturn(sample(id));

        CreateBookingRequest req = new CreateBookingRequest(
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2030, 7, 1), LocalDate.of(2030, 7, 4),
                2, null, null, null, null);

        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(authorities = "BOOKING_CREATE")
    @DisplayName("POST /api/v1/bookings returns 400 for missing required fields")
    void create_Invalid_Returns400() throws Exception {
        String bad = "{}"; // no propertyId / guestId / dates
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "BOOKING_EDIT")
    @DisplayName("PATCH /api/v1/bookings/{id}/status returns 200")
    void patchStatus_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(bookingService.changeStatus(eq(id), any())).willReturn(sample(id));

        mockMvc.perform(patch("/api/v1/bookings/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateBookingStatusRequest(BookingStatus.CHECKED_IN, null))))
                .andExpect(status().isOk());
    }
}
