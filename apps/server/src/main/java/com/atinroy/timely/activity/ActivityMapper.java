package com.atinroy.timely.activity;

import com.atinroy.timely.api.model.ActivityDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ActivityMapper {
    private ActivityMapper() {
    }

    public static ActivityDto toDto(Activity activity) {
        Instant archivedAt = activity.getArchivedAt();
        return new ActivityDto()
                .id(activity.getId())
                .name(activity.getName())
                .color(activity.getColor())
                .archivedAt(archivedAt == null ? null : OffsetDateTime.ofInstant(archivedAt, ZoneOffset.UTC))
                .createdAt(OffsetDateTime.ofInstant(activity.getCreatedAt(), ZoneOffset.UTC));
    }
}
