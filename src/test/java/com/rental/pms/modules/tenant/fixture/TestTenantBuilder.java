package com.rental.pms.modules.tenant.fixture;

import com.rental.pms.modules.tenant.entity.Tenant;
import com.rental.pms.modules.tenant.entity.TenantStatus;

import java.util.UUID;

public class TestTenantBuilder {

    private UUID id = UUID.randomUUID();
    private String name = "Test Agency";
    private String slug = "test-agency";
    private String timezone = "UTC";
    private String defaultCurrency = "GBP";
    private String contactEmail = "admin@test.com";
    private TenantStatus status = TenantStatus.ACTIVE;

    public static TestTenantBuilder aTenant() {
        return new TestTenantBuilder();
    }

    public TestTenantBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public TestTenantBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public TestTenantBuilder withSlug(String slug) {
        this.slug = slug;
        return this;
    }

    public TestTenantBuilder withStatus(TenantStatus status) {
        this.status = status;
        return this;
    }

    public TestTenantBuilder withContactEmail(String email) {
        this.contactEmail = email;
        return this;
    }

    public Tenant build() {
        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(slug)
                .timezone(timezone)
                .defaultCurrency(defaultCurrency)
                .contactEmail(contactEmail)
                .status(status)
                .build();
        tenant.setId(id);
        return tenant;
    }
}
