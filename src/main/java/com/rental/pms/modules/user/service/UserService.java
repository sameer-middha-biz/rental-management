package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.dto.PageResponse;
import com.rental.pms.common.event.DomainEvent;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.user.dto.UpdateUserRequest;
import com.rental.pms.modules.user.dto.UpdateUserRolesRequest;
import com.rental.pms.modules.user.dto.UpdateUserStatusRequest;
import com.rental.pms.modules.user.dto.UserResponse;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import com.rental.pms.modules.user.event.UserDeletedEvent;
import com.rental.pms.modules.user.exception.LastAdminException;
import com.rental.pms.modules.user.mapper.UserMapper;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.RoleRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper userMapper;
    private final CurrentUser currentUser;
    private final AuditEventPublisher auditEventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    public PageResponse<UserResponse> getUsers(Pageable pageable) {
        Page<UserResponse> page = userRepository.findAll(pageable)
                .map(userMapper::toResponse);
        return PageResponse.from(page);
    }

    public UserResponse getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return userMapper.toResponse(user);
    }

    public UserResponse getCurrentUser() {
        User user = userRepository.findByIdWithRolesAndPermissions(currentUser.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUser.getUserId()));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUserStatus(UUID id, UpdateUserStatusRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (id.equals(currentUser.getUserId())) {
            throw new ConflictException("Cannot change your own status", "USER.STATUS.SELF_CHANGE");
        }

        UserStatus newStatus = UserStatus.valueOf(request.status());

        // Prevent disabling the last admin
        if (newStatus == UserStatus.DISABLED) {
            checkNotLastAdmin(user);
        }

        user.setStatus(newStatus);
        user = userRepository.save(user);

        // Revoke tokens if disabling
        if (newStatus == UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllByUserId(id);
        }

        auditEventPublisher.publish(currentUser.getTenantId(), currentUser.getUserId(),
                "USER_STATUS_CHANGED", "User", id, "Status changed to " + newStatus);

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUserRoles(UUID id, UpdateUserRolesRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (id.equals(currentUser.getUserId())) {
            throw new ConflictException("Cannot change your own roles", "USER.ROLES.SELF_CHANGE");
        }

        Set<Role> roles = roleRepository.findByNameIn(request.roleNames());
        if (roles.size() != request.roleNames().size()) {
            throw new ResourceNotFoundException("Role", "names", request.roleNames());
        }

        user.setRoles(roles);
        user = userRepository.save(user);

        auditEventPublisher.publish(currentUser.getTenantId(), currentUser.getUserId(),
                "USER_ROLES_CHANGED", "User", id, "Roles updated to " + request.roleNames());

        return userMapper.toResponse(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (id.equals(currentUser.getUserId())) {
            throw new ConflictException("Cannot delete your own account", "USER.DELETE.SELF");
        }

        checkNotLastAdmin(user);

        // Cascade handles refresh_tokens and user_roles via ON DELETE CASCADE
        userRepository.delete(user);

        domainEventPublisher.publish(new UserDeletedEvent(
                DomainEvent.now(currentUser.getTenantId()),
                currentUser.getTenantId(), id, user.getEmail()));

        auditEventPublisher.publish(currentUser.getTenantId(), currentUser.getUserId(),
                "USER_DELETED", "User", id, "User deleted: " + user.getEmail());

        log.info("User deleted: userId={}, email={}", id, user.getEmail());
    }

    private void checkNotLastAdmin(User user) {
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> "AGENCY_ADMIN".equals(r.getName()));
        if (isAdmin) {
            long adminCount = userRepository.countByTenantIdAndRoleName(
                    user.getTenantId(), "AGENCY_ADMIN");
            if (adminCount <= 1) {
                throw new LastAdminException();
            }
        }
    }
}
