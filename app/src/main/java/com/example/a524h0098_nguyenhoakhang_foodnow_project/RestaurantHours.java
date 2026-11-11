package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

public final class RestaurantHours {
    public enum Status {
        OPEN, CLOSED, UNKNOWN
    }

    private RestaurantHours() {
    }

    public static Status getStatus(Boolean active, String open, String close, String timezone, long now) {
        if (Boolean.FALSE.equals(active))
            return Status.CLOSED;
        if (!Boolean.TRUE.equals(active) || timezone == null
                || !Arrays.asList(TimeZone.getAvailableIDs()).contains(timezone))
            return Status.UNKNOWN;
        int start = parse(open);
        int end = parse(close);
        if (start < 0 || end < 0 || start == end)
            return Status.UNKNOWN;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(timezone));
        calendar.setTimeInMillis(now);
        int current = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        boolean isOpen = start < end ? current >= start && current < end : current >= start || current < end;
        return isOpen ? Status.OPEN : Status.CLOSED;
    }

    private static int parse(String value) {
        if (value == null || !value.matches("[0-9]{2}:[0-9]{2}"))
            return -1;
        int hours = Integer.parseInt(value.substring(0, 2));
        int minutes = Integer.parseInt(value.substring(3));
        return hours < 24 && minutes < 60 ? hours * 60 + minutes : -1;
    }
}
