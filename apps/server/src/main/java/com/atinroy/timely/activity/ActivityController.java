package com.atinroy.timely.activity;

import com.atinroy.timely.api.ActivitiesApi;
import com.atinroy.timely.api.model.ActivityDto;
import com.atinroy.timely.api.model.CreateActivityRequestDto;
import com.atinroy.timely.api.model.UpdateActivityRequestDto;
import com.atinroy.timely.auth.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ActivityController implements ActivitiesApi {
    private final ActivityService activities;

    @Override
    public ResponseEntity<List<ActivityDto>> listActivities(Boolean includeArchived) {
        List<ActivityDto> body = activities.list(AuthPrincipal.currentUserId(), includeArchived).stream()
                .map(ActivityMapper::toDto)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<ActivityDto> createActivity(CreateActivityRequestDto request) {
        var result = activities.create(AuthPrincipal.currentUserId(), request.getName(), request.getColor());
        HttpStatus status = result.unarchived() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ActivityMapper.toDto(result.activity()));
    }

    @Override
    public ResponseEntity<ActivityDto> getActivity(Integer activityId) {
        return ResponseEntity.ok(ActivityMapper.toDto(activities.get(AuthPrincipal.currentUserId(), activityId)));
    }

    @Override
    public ResponseEntity<ActivityDto> updateActivity(Integer activityId, UpdateActivityRequestDto request) {
        Activity activity = activities.update(AuthPrincipal.currentUserId(), activityId,
                request.getName(), request.getColor(), request.getArchived());
        return ResponseEntity.ok(ActivityMapper.toDto(activity));
    }

    @Override
    public ResponseEntity<Void> deleteActivity(Integer activityId) {
        activities.delete(AuthPrincipal.currentUserId(), activityId);
        return ResponseEntity.noContent().build();
    }
}
