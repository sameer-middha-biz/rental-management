package com.rental.pms.modules.property.mapper;

import com.rental.pms.modules.property.dto.PropertyPhotoResponse;
import com.rental.pms.modules.property.entity.PropertyPhoto;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;

// disableBuilder=true because PropertyPhoto inherits from BaseEntity (see PropertyMapper note)
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PropertyPhotoMapper {

    PropertyPhotoResponse toResponse(PropertyPhoto photo);
}
