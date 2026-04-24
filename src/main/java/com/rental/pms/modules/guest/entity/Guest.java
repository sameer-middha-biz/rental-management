package com.rental.pms.modules.guest.entity;

import com.rental.pms.common.encryption.EncryptedStringConverter;
import com.rental.pms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Guest CRM record. Tenant-scoped.
 *
 * <p>Email, phone, and notes are encrypted at rest using {@link EncryptedStringConverter}.
 * Because AES-GCM ciphertext is non-deterministic, we maintain {@code emailHash}
 * (SHA-256 of lower(email)+tenantId) for uniqueness lookups.
 *
 * <p>GDPR erasure (see {@code GuestService#gdprErase}) anonymises PII in place
 * rather than deleting the row, so bookings referencing this guest remain valid.
 */
@Entity
@Table(name = "guests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, of = {})
public class Guest extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", columnDefinition = "TEXT")
    private String email;

    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone", columnDefinition = "TEXT")
    private String phone;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "nationality", length = 3)
    private String nationality;

    @Column(name = "id_document_s3_key", length = 500)
    private String idDocumentS3Key;

    @Column(name = "total_bookings", nullable = false)
    @Builder.Default
    private Integer totalBookings = 0;

    @Column(name = "last_stay_at")
    private Instant lastStayAt;

    @Column(name = "gdpr_erased", nullable = false)
    @Builder.Default
    private boolean gdprErased = false;

    @Column(name = "gdpr_erased_at")
    private Instant gdprErasedAt;
}
