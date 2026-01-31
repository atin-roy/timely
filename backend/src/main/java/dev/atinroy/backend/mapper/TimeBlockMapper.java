package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.timeblock.TimeBlockResponse;
import dev.atinroy.backend.entity.TimeBlock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = { TodoMapper.class, TagMapper.class })
public interface TimeBlockMapper {

    @Mapping(target = "active", expression = "java(timeBlock.isActive())")
    @Mapping(target = "currentDurationSeconds", expression = "java(timeBlock.getCurrentDurationSeconds())")
    @Mapping(target = "remainingSeconds", expression = "java(timeBlock.getRemainingSeconds())")
    TimeBlockResponse toResponse(TimeBlock timeBlock);
}
