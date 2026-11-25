package io.github.tst_038.koth.schedule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * KOTH starts at every time any of the cron lines names and runs for that line's length. Windows that overlap
 * merge in effect: when one ends inside another, the other starts at once, for what is left of it.
 *
 * @param length the length of a window no cron line decides, such as a forced start's default
 */
public record CronTimetable(List<CronStart> starts, Duration length) implements Timetable {

    public CronTimetable {
        starts = List.copyOf(starts);
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("a cron timetable needs at least one start");
        }
        if (length.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("a window must last at least a minute, not " + length);
        }
    }

    @Override
    public Optional<Window> current(LocalDateTime now) {
        return earliest(start -> start.openAt(now));
    }

    @Override
    public Optional<Window> next(LocalDateTime now) {
        return earliest(start -> start.after(now));
    }

    private Optional<Window> earliest(Function<CronStart, Optional<Window>> window) {
        return starts.stream().map(window).flatMap(Optional::stream).min(Comparator.comparing(Window::start));
    }
}
