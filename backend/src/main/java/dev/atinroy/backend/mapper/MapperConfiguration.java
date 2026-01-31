package dev.atinroy.backend.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

@MapperConfig(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface MapperConfiguration {
}
