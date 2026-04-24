package com.rental.pms.modules.guest.mapper;

import com.rental.pms.modules.guest.dto.CreateGuestRequest;
import com.rental.pms.modules.guest.dto.GuestResponse;
import com.rental.pms.modules.guest.dto.UpdateGuestRequest;
import com.rental.pms.modules.guest.entity.Guest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

// disableBuilder=true — Guest extends BaseEntity, whose inherited fields
// don't appear in Lombok's generated builder (same reason as PropertyMapper).
@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface GuestMapper {

    Guest toEntity(CreateGuestRequest request);

    GuestResponse toResponse(Guest guest);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyUpdate(UpdateGuestRequest request, @MappingTarget Guest target);
}
