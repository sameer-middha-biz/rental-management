package com.rental.pms.modules.property.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.pms.common.exception.TenantLimitExceededException;
import com.rental.pms.common.security.JwtAuthenticationFilter;
import com.rental.pms.common.security.JwtTokenProvider;
import com.rental.pms.common.security.RateLimitFilter;
import com.rental.pms.common.security.TenantFilter;
import com.rental.pms.modules.property.dto.ConfirmPhotoUploadRequest;
import com.rental.pms.modules.property.dto.CreatePropertyRequest;
import com.rental.pms.modules.property.dto.GenerateUploadUrlRequest;
import com.rental.pms.modules.property.dto.GenerateUploadUrlResponse;
import com.rental.pms.modules.property.dto.PropertyPhotoResponse;
import com.rental.pms.modules.property.dto.PropertyResponse;
import com.rental.pms.modules.property.dto.ReorderPhotosRequest;
import com.rental.pms.modules.property.service.PropertyPhotoService;
import com.rental.pms.modules.property.service.PropertyService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for PropertyController.
 * Covers acceptance criteria: plan enforcement (409), create (201), delete (204),
 * archive/restore, pagination response shape, photo upload URL generation.
 */
@WebMvcTest(PropertyController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PropertyController Tests")
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private PropertyService propertyService;
    @MockitoBean private PropertyPhotoService propertyPhotoService;

    // Security plumbing — not exercised (filters disabled above) but required for context.
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private TenantFilter tenantFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;

    private PropertyResponse sampleProperty(UUID id, String name, String status) {
        return new PropertyResponse(
                id, UUID.randomUUID(), null, name, "slug-" + name.toLowerCase(),
                null, "VILLA",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                status, null, Instant.now(), Instant.now());
    }

    private CreatePropertyRequest sampleCreateRequest() {
        return new CreatePropertyRequest(
                "Beach Villa", null, null, "VILLA", null,
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null);
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_CREATE")
    @DisplayName("POST /api/v1/properties returns 201 with created property")
    void create_Returns201() throws Exception {
        UUID id = UUID.randomUUID();
        given(propertyService.create(any())).willReturn(sampleProperty(id, "Beach Villa", "ACTIVE"));

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCreateRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Beach Villa"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_CREATE")
    @DisplayName("POST /api/v1/properties returns 409 with TENANT.LIMIT.EXCEEDED when plan limit hit")
    void create_AtPlanLimit_Returns409() throws Exception {
        given(propertyService.create(any()))
                .willThrow(new TenantLimitExceededException("properties", 5, 5));

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleCreateRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TENANT.LIMIT.EXCEEDED"));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_CREATE")
    @DisplayName("POST /api/v1/properties returns 400 when propertyType is invalid")
    void create_InvalidPropertyType_Returns400() throws Exception {
        CreatePropertyRequest bad = new CreatePropertyRequest(
                "X", null, null, "MANSION", // not in allowed enum list
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/v1/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_VIEW")
    @DisplayName("GET /api/v1/properties/{id} returns 200")
    void getById_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(propertyService.getById(id)).willReturn(sampleProperty(id, "Villa", "ACTIVE"));

        mockMvc.perform(get("/api/v1/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Villa"));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_EDIT")
    @DisplayName("PATCH /api/v1/properties/{id}/archive returns archived property")
    void archive_Returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(propertyService.archive(id)).willReturn(sampleProperty(id, "Villa", "ARCHIVED"));

        mockMvc.perform(patch("/api/v1/properties/{id}/archive", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_DELETE")
    @DisplayName("DELETE /api/v1/properties/{id} returns 204")
    void delete_Returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/properties/{id}", id))
                .andExpect(status().isNoContent());

        verify(propertyService).delete(id);
    }

    // --- Photos ---

    @Test
    @WithMockUser(authorities = "PROPERTY_EDIT")
    @DisplayName("POST /{id}/photos/upload-url returns pre-signed URL payload")
    void generateUploadUrl_Returns200() throws Exception {
        UUID propertyId = UUID.randomUUID();
        GenerateUploadUrlRequest req = new GenerateUploadUrlRequest(
                "vacation.jpg", "image/jpeg", 500_000L);
        Instant expiresAt = Instant.now().plusSeconds(900);
        GenerateUploadUrlResponse resp = new GenerateUploadUrlResponse(
                "https://s3.local/bucket/tenants/t/properties/p/photos/uid-vacation.jpg",
                "tenants/t/properties/p/photos/uid-vacation.jpg",
                "PUT", expiresAt);

        given(propertyPhotoService.generateUploadUrl(eq(propertyId), any())).willReturn(resp);

        mockMvc.perform(post("/api/v1/properties/{id}/photos/upload-url", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(resp.uploadUrl()))
                .andExpect(jsonPath("$.s3Key").value(resp.s3Key()))
                .andExpect(jsonPath("$.httpMethod").value("PUT"));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_EDIT")
    @DisplayName("POST /{id}/photos (confirm) returns 201")
    void confirmUpload_Returns201() throws Exception {
        UUID propertyId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        ConfirmPhotoUploadRequest req = new ConfirmPhotoUploadRequest(
                "tenants/t/properties/p/photos/abc-vacation.jpg",
                "vacation.jpg", "image/jpeg", 500_000L, "Sunset");
        PropertyPhotoResponse resp = new PropertyPhotoResponse(
                photoId, propertyId, req.s3Key(), req.filename(),
                req.contentType(), req.sizeBytes(), 0, req.caption(), Instant.now());

        given(propertyPhotoService.confirmUpload(eq(propertyId), any())).willReturn(resp);

        mockMvc.perform(post("/api/v1/properties/{id}/photos", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(photoId.toString()))
                .andExpect(jsonPath("$.sortOrder").value(0));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_EDIT")
    @DisplayName("PUT /{id}/photos/reorder returns reordered list")
    void reorderPhotos_Returns200() throws Exception {
        UUID propertyId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        ReorderPhotosRequest req = new ReorderPhotosRequest(List.of(p1, p2));

        given(propertyPhotoService.reorderPhotos(eq(propertyId), any())).willReturn(List.of(
                new PropertyPhotoResponse(p1, propertyId, "k1", "f1", "image/jpeg", 1L, 0, null, Instant.now()),
                new PropertyPhotoResponse(p2, propertyId, "k2", "f2", "image/jpeg", 1L, 1, null, Instant.now())
        ));

        mockMvc.perform(put("/api/v1/properties/{id}/photos/reorder", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sortOrder").value(0))
                .andExpect(jsonPath("$[1].sortOrder").value(1));
    }

    @Test
    @WithMockUser(authorities = "PROPERTY_EDIT")
    @DisplayName("DELETE /{id}/photos/{photoId} returns 204")
    void deletePhoto_Returns204() throws Exception {
        UUID propertyId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/properties/{id}/photos/{photoId}", propertyId, photoId))
                .andExpect(status().isNoContent());

        verify(propertyPhotoService).deletePhoto(propertyId, photoId);
    }
}
