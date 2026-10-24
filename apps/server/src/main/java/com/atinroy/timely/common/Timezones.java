package com.atinroy.timely.common;

import java.time.ZoneId;

public final class Timezones {
    private Timezones() {
    }

    // Region names only. Offsets like "+05:30" parse in Java but mean the opposite sign to Postgres's AT TIME ZONE.
    public static String requireRegion(String timezone) {
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new IllegalArgumentException("Unknown timezone: " + timezone);
        }
        return timezone;
    }
}
