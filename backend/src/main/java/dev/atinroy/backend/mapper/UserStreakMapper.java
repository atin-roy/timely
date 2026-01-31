package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.stats.StreakResponse;
import dev.atinroy.backend.entity.UserStreak;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class)
public interface UserStreakMapper {

    @Mapping(target = "isActive", expression = "java(userStreak.isStreakActive())")
    StreakResponse toResponse(UserStreak userStreak);
}
