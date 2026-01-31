package dev.atinroy.backend.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.ReportingPolicy;

@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN, injectionStrategy = InjectionStrategy.FIELD, nullValueCheckStrategy = org.mapstruct.NullValueCheckStrategy.ALWAYS)
public interface MapperConfiguration {
}
