package io.github.tst_038.koth.schedule;

import io.github.tst_038.minigami.core.schedule.Cron;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/** One entry of a cron timetable: when windows start, and when they end. */
public sealed interface CronStart {

    /** The longest a window can last. */
    Duration LONGEST = Duration.ofDays(7);

    /** The first window that starts after the given time. */
    Optional<Window> after(LocalDateTime time);

    /** The window that has started and not yet ended. */
    Optional<Window> openAt(LocalDateTime now);

    /** Windows that start at each time of the cron line and last as long as given. */
    record Fixed(Cron cron, Duration length) implements CronStart {

        public Fixed {
            if (length.compareTo(Duration.ofMinutes(1)) < 0) {
                throw new IllegalArgumentException("a window must last at least a minute, not " + length);
            }
        }

        @Override
        public Optional<Window> after(LocalDateTime time) {
            return cron.next(time).map(this::window);
        }

        @Override
        public Optional<Window> openAt(LocalDateTime now) {
            // The first start after now - length is the earliest one whose window hasn't ended yet.
            return cron.next(now.minus(length)).filter(start -> !start.isAfter(now)).map(this::window);
        }

        private Window window(LocalDateTime start) {
            return new Window(start, start.plus(length));
        }
    }

    /**
     * Windows that start at each time of the start line and end at the first time of the end line after that.
     * A start whose end is more than {@link #LONGEST} away has no window.
     */
    record Paired(Cron start, Cron end) implements CronStart {

        @Override
        public Optional<Window> after(LocalDateTime time) {
            return firstAfter(time);
        }

        /**
         * A start is open at {@code now} when it is not after it and no end falls after the start up to now. So the
         * candidates are the starts from the latest end up to now (or from the longest window ago if no end fell
         * that recently), and the earliest of them is the one that is open.
         */
        @Override
        public Optional<Window> openAt(LocalDateTime now) {
            var earliest = now.minus(LONGEST);
            var floor = earliest;
            for (var time = end.next(earliest); time.isPresent() && !time.get().isAfter(now); time = end.next(time.get())) {
                // A start in the very minute of an end belongs to the window after it.
                floor = time.get().minusMinutes(1);
            }
            return firstAfter(floor).filter(window -> !window.start().isAfter(now));
        }

        private Optional<Window> firstAfter(LocalDateTime time) {
            // The cron lines repeat, so a start that never gets an end in range would be searched for ever.
            var horizon = time.plusYears(8);
            while (true) {
                var from = start.next(time);
                var to = from.flatMap(end::next);
                if (to.isEmpty() || from.get().isAfter(horizon)) {
                    return Optional.empty();
                }
                if (Duration.between(from.get(), to.get()).compareTo(LONGEST) <= 0) {
                    return Optional.of(new Window(from.get(), to.get()));
                }
                // Starts before the end's last 7 days are too early for it; look at the ones after.
                time = to.get().minus(LONGEST).minusMinutes(1);
            }
        }
    }
}
