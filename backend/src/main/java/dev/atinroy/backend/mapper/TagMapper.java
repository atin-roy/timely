package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.tag.TagRequest;
import dev.atinroy.backend.dto.tag.TagResponse;
import dev.atinroy.backend.entity.Tag;
import org.mapstruct.*;

@Mapper(config = MapperConfiguration.class)
public interface TagMapper {

    TagResponse toResponse(Tag tag);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Tag toEntity(TagRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(TagRequest request, @MappingTarget Tag tag);
}
