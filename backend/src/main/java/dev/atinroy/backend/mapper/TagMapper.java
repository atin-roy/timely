package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.tag.TagResponse;
import dev.atinroy.backend.entity.Tag;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfiguration.class)
public interface TagMapper {

    TagResponse toResponse(Tag tag);
}
