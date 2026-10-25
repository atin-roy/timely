package com.atinroy.timely.activity;

import com.atinroy.timely.common.ConflictException;
import com.atinroy.timely.common.NotFoundException;
import com.atinroy.timely.session.SessionService;
import com.atinroy.timely.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityService {
    public record CreateResult(Activity activity, boolean unarchived) {
    }

    private final ActivityRepository activities;
    private final UserRepository users;
    private final SessionService sessionService;

    public List<Activity> list(Integer userId, boolean includeArchived) {
        return includeArchived
                ? activities.findByUserIdOrderByName(userId)
                : activities.findByUserIdAndArchivedAtIsNullOrderByName(userId);
    }

    public Activity get(Integer userId, Integer id) {
        return activities.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("not-found"));
    }

    // Reusing the name of an archived activity unarchives it, with the given colour, instead of creating a new one.
    @Transactional
    public CreateResult create(Integer userId, String name, String color) {
        var existing = activities.findByUserIdAndNameIgnoreCase(userId, name);
        if (existing.isPresent()) {
            Activity activity = existing.get();
            if (activity.getArchivedAt() == null) {
                throw new ConflictException("activity-name-taken");
            }
            activity.setArchivedAt(null);
            activity.setColor(color);
            return new CreateResult(activity, true);
        }
        Activity activity = new Activity();
        activity.setUser(users.getReferenceById(userId));
        activity.setName(name);
        activity.setColor(color);
        return new CreateResult(activities.saveAndFlush(activity), false);
    }

    // A null argument leaves that field as it is. Archiving stops a session running on the activity.
    @Transactional
    public Activity update(Integer userId, Integer id, String name, String color, Boolean archived) {
        Activity activity = get(userId, id);
        if (name != null) {
            activities.findByUserIdAndNameIgnoreCase(userId, name)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ConflictException("activity-name-taken");
                    });
            activity.setName(name);
        }
        if (color != null) {
            activity.setColor(color);
        }
        if (archived != null) {
            if (archived && activity.getArchivedAt() == null) {
                sessionService.stopIfRunningOn(userId, id);
                activity.setArchivedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            } else if (!archived) {
                activity.setArchivedAt(null);
            }
        }
        return activity;
    }

    // The database cascades the delete to the activity's sessions.
    @Transactional
    public void delete(Integer userId, Integer id) {
        activities.delete(get(userId, id));
    }
}
