package dev.atinroy.backend.mapper;

import dev.atinroy.backend.dto.todo.TodoRequest;
import dev.atinroy.backend.dto.todo.TodoResponse;
import dev.atinroy.backend.entity.Todo;
import org.mapstruct.*;

@Mapper(config = MapperConfiguration.class, uses = { TagMapper.class })
public interface TodoMapper {

    TodoResponse toResponse(Todo todo);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "tag", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Todo toEntity(TodoRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "tag", ignore = true)
    @Mapping(target = "completed", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(TodoRequest request, @MappingTarget Todo todo);
}
