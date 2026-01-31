package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.settings.UserSettingsRequest;
import dev.atinroy.backend.dto.settings.UserSettingsResponse;
import dev.atinroy.backend.entity.UserSettings;
import org.mapstruct.*;

@Mapper(config = MapperConfiguration.class)
public interface UserSettingsMapper {

    UserSettingsResponse toResponse(UserSettings userSettings);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    UserSettings toEntity(UserSettingsRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UserSettingsRequest request, @MappingTarget UserSettings userSettings);
}
