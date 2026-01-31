package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.timeblock.TimeBlockResponse;
import dev.atinroy.backend.entity.TimeBlock;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class, uses = { TodoMapper.class, TagMapper.class })
public interface TimeBlockMapper {

    TimeBlockResponse toResponse(TimeBlock timeBlock);
}
