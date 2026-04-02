package com.rental.pms.modules.tenant.mapper;

import com.rental.pms.modules.tenant.dto.TenantResponse;
import com.rental.pms.modules.tenant.dto.UpdateTenantRequest;
import com.rental.pms.modules.tenant.entity.Tenant;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface TenantMapper {

    TenantResponse toResponse(Tenant tenant);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromRequest(UpdateTenantRequest request, @MappingTarget Tenant tenant);
}
