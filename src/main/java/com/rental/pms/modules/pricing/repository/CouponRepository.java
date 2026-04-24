package com.rental.pms.modules.pricing.repository;

import com.rental.pms.modules.pricing.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    Optional<Coupon> findByTenantIdAndCode(UUID tenantId, String code);
}
