package com.rental.pms.modules.user.repository;

import com.rental.pms.modules.user.entity.Invitation;
import com.rental.pms.modules.user.entity.InvitationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    Optional<Invitation> findByEmailAndTenantIdAndStatus(String email, UUID tenantId, InvitationStatus status);

    Page<Invitation> findAllByTenantId(UUID tenantId, Pageable pageable);
}
