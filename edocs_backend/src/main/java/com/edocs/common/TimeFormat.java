package com.edocs.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Human-readable time labels in the same style the frontend mock data uses.
public final class TimeFormat {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private static Clock clock = Clock.systemUTC();

    private TimeFormat() {
    }

    static void useClock(Clock testClock) {
        clock = testClock;
    }

    public static String ago(Instant at) {
        if (at == null) {
            return "";
        }
        Duration d = Duration.between(at, Instant.now(clock));
        if (d.toMinutes() < 1) {
            return "Just now";
        }
        if (d.toMinutes() < 60) {
            return d.toMinutes() + "m ago";
        }
        if (d.toHours() < 24) {
            return d.toHours() + (d.toHours() == 1 ? " hour ago" : " hours ago");
        }
        if (d.toDays() < 2) {
            return "Yesterday";
        }
        if (d.toDays() < 7) {
            return d.toDays() + " days ago";
        }
        return longDate(at);
    }

    // "Today, 14:22 UTC" / "Yesterday, 19:40 UTC" / "Sept 24, 04:00 UTC".
    public static String stamp(Instant at) {
        ZonedDateTime t = at.atZone(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String day;
        if (t.toLocalDate().equals(today)) {
            day = "Today";
        } else if (t.toLocalDate().equals(today.minusDays(1))) {
            day = "Yesterday";
        } else {
            day = MONTH_DAY.format(t);
        }
        return day + ", " + HOUR.format(t) + " UTC";
    }

    // "Today, 14:25" style used by version history.
    public static String when(Instant at) {
        ZonedDateTime t = at.atZone(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (t.toLocalDate().equals(today)) {
            return Duration.between(at, Instant.now(clock)).toMinutes() < 1 ? "Just now" : "Today, " + HOUR.format(t);
        }
        if (t.toLocalDate().equals(today.minusDays(1))) {
            return "Yesterday, " + HOUR.format(t);
        }
        return LONG_DATE.format(t);
    }

    public static String longDate(Instant at) {
        return LONG_DATE.format(at.atZone(ZoneOffset.UTC));
    }

    public static String longDate(LocalDate date) {
        return date == null ? null : LONG_DATE.format(date);
    }

    public static String iso(Instant at) {
        return DateTimeFormatter.ISO_INSTANT.format(at);
    }
}
