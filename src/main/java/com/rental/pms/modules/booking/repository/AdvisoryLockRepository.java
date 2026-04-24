package com.rental.pms.modules.booking.repository;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;

/**
 * Thin wrapper around PostgreSQL's advisory-lock primitives.
 * <p>
 * {@code pg_advisory_xact_lock} takes a bigint and blocks until it can hold the lock for the
 * lifetime of the current transaction — we hash {@code propertyId} into a stable long and key
 * the lock on that, so concurrent booking attempts against the same property serialize while
 * bookings against different properties proceed in parallel.
 */
@Component
public class AdvisoryLockRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Acquires a transaction-scoped advisory lock for the given property. Must be called inside
     * an open transaction (the lock is released automatically at COMMIT/ROLLBACK).
     */
    public void lockProperty(UUID propertyId) {
        long key = stableKey(propertyId);
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:key)")
                .setParameter("key", key)
                .getSingleResult();
    }

    /** XOR of the UUID's two halves — deterministic and evenly distributed. */
    static long stableKey(UUID id) {
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }
}
