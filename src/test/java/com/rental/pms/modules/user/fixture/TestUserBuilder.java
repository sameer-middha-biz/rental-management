package com.rental.pms.modules.user.fixture;

import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import com.rental.pms.modules.user.entity.UserStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TestUserBuilder {

    private UUID tenantId = UUID.randomUUID();
    private String email = "user@test.com";
    private String passwordHash = "$2a$12$encodedHash";
    private String firstName = "Test";
    private String lastName = "User";
    private UserStatus status = UserStatus.ACTIVE;
    private Set<Role> roles = new HashSet<>();

    public static TestUserBuilder aUser() {
        return new TestUserBuilder();
    }

    public TestUserBuilder withTenantId(UUID tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    public TestUserBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public TestUserBuilder withPasswordHash(String hash) {
        this.passwordHash = hash;
        return this;
    }

    public TestUserBuilder withFirstName(String name) {
        this.firstName = name;
        return this;
    }

    public TestUserBuilder withLastName(String name) {
        this.lastName = name;
        return this;
    }

    public TestUserBuilder withStatus(UserStatus status) {
        this.status = status;
        return this;
    }

    public TestUserBuilder withRoles(Set<Role> roles) {
        this.roles = roles;
        return this;
    }

    public User build() {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .firstName(firstName)
                .lastName(lastName)
                .status(status)
                .roles(roles)
                .build();
        user.setTenantId(tenantId);
        user.setId(UUID.randomUUID());
        return user;
    }
}
