package com.rental.pms.modules.property.mapper;

import com.rental.pms.modules.property.dto.CreatePropertyRequest;
import com.rental.pms.modules.property.dto.PropertyResponse;
import com.rental.pms.modules.property.entity.Property;
import com.rental.pms.modules.property.entity.PropertyType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

// disableBuilder=true — Property extends BaseEntity and Lombok's @Builder does NOT
// generate builder setters for inherited fields, so MapStruct must use plain setters.
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PropertyMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "defaultHousekeeperId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "propertyType", expression = "java(toPropertyType(request.propertyType()))")
    Property toEntity(CreatePropertyRequest request);

    @Mapping(target = "status", expression = "java(property.getStatus().name())")
    @Mapping(target = "propertyType", expression = "java(property.getPropertyType().name())")
    PropertyResponse toResponse(Property property);

    default PropertyType toPropertyType(String value) {
        return value == null ? null : PropertyType.valueOf(value);
    }

    /**
     * Applied on update to skip null fields (partial update semantics).
     * Used by PropertyService.applyUpdate.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "propertyType",
            expression = "java(request.propertyType() == null ? target.getPropertyType() : toPropertyType(request.propertyType()))")
    void applyUpdate(com.rental.pms.modules.property.dto.UpdatePropertyRequest request,
                     @org.mapstruct.MappingTarget Property target);
}
