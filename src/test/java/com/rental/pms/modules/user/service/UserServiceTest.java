package com.rental.pms.modules.user.service;

import com.rental.pms.common.audit.AuditEventPublisher;
import com.rental.pms.common.event.DomainEventPublisher;
import com.rental.pms.common.exception.ConflictException;
import com.rental.pms.common.exception.ResourceNotFoundException;
import com.rental.pms.common.security.CurrentUser;
import com.rental.pms.modules.user.dto.UpdateUserRequest;
import com.rental.pms.modules.user.dto.UpdateUserStatusRequest;
import com.rental.pms.modules.user.dto.UserResponse;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;
import com.rental.pms.modules.user.event.UserDeletedEvent;
import com.rental.pms.modules.user.exception.LastAdminException;
import com.rental.pms.modules.user.fixture.TestUserBuilder;
import com.rental.pms.modules.user.mapper.UserMapper;
import com.rental.pms.modules.user.repository.RefreshTokenRepository;
import com.rental.pms.modules.user.repository.RoleRepository;
import com.rental.pms.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private UUID currentUserId;
    private UUID tenantId;
    private User testUser;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        tenantId = UUID.randomUUID();

        testUser = TestUserBuilder.aUser()
                .withTenantId(tenantId)
                .withEmail("user@test.com")
                .withFirstName("Test")
                .withLastName("User")
                .build();

        userResponse = new UserResponse(
                testUser.getId(), "user@test.com", "Test", "User",
                null, "ACTIVE", List.of(), Instant.now());
    }

    @Test
    void getUsers_ShouldReturnPaginatedResults() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(testUser), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);

        // Act
        var result = userService.getUsers(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getUser_WhenFound_ShouldReturnUserResponse() {
        // Arrange
        UUID userId = testUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // Act
        UserResponse result = userService.getUser(userId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    void getUser_WhenNotFound_ShouldThrowResourceNotFoundException() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUser(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getCurrentUser_ShouldReturnCurrentUserProfile() {
        // Arrange
        when(currentUser.getUserId()).thenReturn(testUser.getId());
        when(userRepository.findByIdWithRolesAndPermissions(testUser.getId()))
                .thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(userResponse);

        // Act
        UserResponse result = userService.getCurrentUser();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("user@test.com");
    }

    @Test
    void updateUser_ShouldUpdateFields() {
        // Arrange
        UUID userId = testUser.getId();
        UpdateUserRequest updateRequest = new UpdateUserRequest("Updated", "Name", "+1234567890");

        UserResponse updatedResponse = new UserResponse(
                userId, "user@test.com", "Updated", "Name",
                "+1234567890", "ACTIVE", List.of(), Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(userMapper.toResponse(any(User.class))).thenReturn(updatedResponse);

        // Act
        UserResponse result = userService.updateUser(userId, updateRequest);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.firstName()).isEqualTo("Updated");
        assertThat(result.lastName()).isEqualTo("Name");
        assertThat(result.phone()).isEqualTo("+1234567890");
        verify(userRepository).save(testUser);
    }

    @Test
    void deleteUser_ShouldDeleteAndPublishEvent() {
        // Arrange
        UUID userId = testUser.getId();
        when(currentUser.getUserId()).thenReturn(currentUserId);
        when(currentUser.getTenantId()).thenReturn(tenantId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act
        userService.deleteUser(userId);

        // Assert
        verify(userRepository).delete(testUser);
        verify(domainEventPublisher).publish(eventCaptor.capture());

        Object event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(UserDeletedEvent.class);

        UserDeletedEvent deletedEvent = (UserDeletedEvent) event;
        assertThat(deletedEvent.userId()).isEqualTo(userId);
        assertThat(deletedEvent.email()).isEqualTo("user@test.com");
    }

    @Test
    void deleteUser_WhenSelf_ShouldThrowConflictException() {
        // Arrange
        UUID userId = testUser.getId();
        when(currentUser.getUserId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(userId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteUser_WhenLastAdmin_ShouldThrowLastAdminException() {
        // Arrange
        Role adminRole = new Role();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("AGENCY_ADMIN");
        adminRole.setPermissions(new HashSet<>());

        User adminUser = TestUserBuilder.aUser()
                .withTenantId(tenantId)
                .withRoles(Set.of(adminRole))
                .build();

        UUID adminUserId = adminUser.getId();
        when(currentUser.getUserId()).thenReturn(currentUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(userRepository.countByTenantIdAndRoleName(tenantId, "AGENCY_ADMIN")).thenReturn(1L);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(adminUserId))
                .isInstanceOf(LastAdminException.class);
    }

    @Test
    void updateUserStatus_WhenDisablingLastAdmin_ShouldThrowLastAdminException() {
        // Arrange
        Role adminRole = new Role();
        adminRole.setId(UUID.randomUUID());
        adminRole.setName("AGENCY_ADMIN");
        adminRole.setPermissions(new HashSet<>());

        User adminUser = TestUserBuilder.aUser()
                .withTenantId(tenantId)
                .withRoles(Set.of(adminRole))
                .build();

        UUID adminUserId = adminUser.getId();
        UpdateUserStatusRequest statusRequest = new UpdateUserStatusRequest("DISABLED");

        when(currentUser.getUserId()).thenReturn(currentUserId);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(userRepository.countByTenantIdAndRoleName(tenantId, "AGENCY_ADMIN")).thenReturn(1L);

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUserStatus(adminUserId, statusRequest))
                .isInstanceOf(LastAdminException.class);
    }
}
