package com.rental.pms.modules.user.mapper;

import com.rental.pms.modules.user.dto.UserResponse;
import com.rental.pms.modules.user.entity.Role;
import com.rental.pms.modules.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "rolesToNames")
    @Mapping(target = "status", source = "status")
    UserResponse toResponse(User user);

    @Named("rolesToNames")
    default List<String> rolesToNames(Set<Role> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(Role::getName)
                .sorted()
                .toList();
    }
}
