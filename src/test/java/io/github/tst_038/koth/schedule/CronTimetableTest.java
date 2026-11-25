package io.github.tst_038.koth.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.minigami.core.schedule.Cron;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CronTimetableTest {

    private static LocalDateTime at(int day, int hour, int minute, int second) {
        return LocalDateTime.of(2026, 3, day, hour, minute, second);
    }

    private static Window window(int day, int hour, int minute, int lengthMinutes) {
        var start = at(day, hour, minute, 0);
        return new Window(start, start.plusMinutes(lengthMinutes));
    }

    private static CronTimetable daily(int length, String... lines) {
        return new CronTimetable(Arrays.stream(lines).<CronStart>map(line -> new CronStart.Fixed(Cron.parse(line), Duration.ofMinutes(length))).toList(), Duration.ofMinutes(length));
    }

    @Test
    void theWindowIsCurrentFromItsStartUntilItsEnd() {
        var timetable = daily(60, "0 20 * * *");

        assertEquals(Optional.empty(), timetable.current(at(2, 19, 59, 59)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 30, 15)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 59, 59)));
    }

    @Test
    void theEndIsExclusive() {
        assertEquals(Optional.empty(), daily(60, "0 20 * * *").current(at(2, 21, 0, 0)));
    }

    @Test
    void nextIsTheFirstStartAfterNow() {
        var timetable = daily(60, "0 20 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.next(at(2, 19, 0, 0)));
        assertEquals(Optional.of(window(3, 20, 0, 60)), timetable.next(at(2, 20, 0, 0)));
        assertEquals(Optional.of(window(3, 20, 0, 60)), timetable.next(at(2, 20, 30, 0)));
    }

    @Test
    void severalLinesTakeTheEarliest() {
        var timetable = daily(30, "0 20 * * *", "0 18 * * *");

        assertEquals(Optional.of(window(2, 18, 0, 30)), timetable.next(at(2, 17, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 30)), timetable.next(at(2, 19, 0, 0)));
        assertEquals(Optional.of(window(2, 18, 0, 30)), timetable.current(at(2, 18, 10, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 30)), timetable.current(at(2, 20, 10, 0)));
    }

    @Test
    void aStartInTheLastMinuteOfAWindowKeepsTheEarlierWindowCurrentUntilItEnds() {
        var timetable = daily(60, "0 20 * * *", "59 20 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 59, 30)));
        assertEquals(Optional.of(window(2, 20, 59, 60)), timetable.current(at(2, 21, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 59, 60)), timetable.next(at(2, 20, 30, 0)));
    }

    @Test
    void windowsLongerThanTheGapBetweenStartsAreTakenInTurn() {
        var timetable = daily(90, "0 20 * * *", "0 21 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 90)), timetable.current(at(2, 21, 15, 0)));
        assertEquals(Optional.of(window(2, 21, 0, 90)), timetable.current(at(2, 21, 30, 0)));
    }

    @Test
    void aWindowCanRunPastMidnight() {
        var timetable = daily(120, "0 23 * * *");

        assertEquals(Optional.of(window(2, 23, 0, 120)), timetable.current(at(3, 0, 30, 0)));
    }

    @Test
    void anImpossibleLineNeverStarts() {
        var timetable = daily(60, "0 0 30 2 *");

        assertEquals(Optional.empty(), timetable.current(at(2, 12, 0, 0)));
        assertEquals(Optional.empty(), timetable.next(at(2, 12, 0, 0)));
    }

    @Test
    void aStartNeedsAMinuteToo() {
        assertThrows(IllegalArgumentException.class, () -> new CronStart.Fixed(Cron.parse("0 20 * * *"), Duration.ofSeconds(59)));
    }

    @Test
    void aTimetableNeedsAStartAndAMinute() {
        assertThrows(IllegalArgumentException.class, () -> new CronTimetable(List.of(), Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class, () -> daily(0, "0 20 * * *"));
        assertThrows(IllegalArgumentException.class,
                () -> new CronTimetable(List.of(new CronStart.Fixed(Cron.parse("0 20 * * *"), Duration.ofHours(1))), Duration.ofSeconds(59)));
    }

    private static CronTimetable paired(String start, String end) {
        return new CronTimetable(List.of(new CronStart.Paired(Cron.parse(start), Cron.parse(end))), Duration.ofMinutes(60));
    }

    @Test
    void aPairedWindowEndsAtTheFirstEndAfterItsStart() {
        // 2026-03-02 is a Monday, 03-06 a Friday.
        var timetable = new CronTimetable(List.of(
                new CronStart.Paired(Cron.parse("0 20 * * 1-4"), Cron.parse("0 21 * * 1-4")),
                new CronStart.Paired(Cron.parse("0 20 * * 5"), Cron.parse("0 22 * * 5"))), Duration.ofMinutes(60));

        assertEquals(Optional.empty(), timetable.current(at(2, 19, 59, 59)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 0, 0)));
        assertEquals(Optional.empty(), timetable.current(at(2, 21, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.next(at(2, 12, 0, 0)));
        assertEquals(Optional.of(window(3, 20, 0, 60)), timetable.next(at(2, 20, 0, 0)));
        assertEquals(Optional.of(window(6, 20, 0, 120)), timetable.next(at(5, 21, 0, 0)));
        assertEquals(Optional.of(window(6, 20, 0, 120)), timetable.current(at(6, 21, 30, 0)));
        assertEquals(Optional.empty(), timetable.current(at(6, 22, 0, 0)));
    }

    @Test
    void aPairedWindowCanCrossMidnight() {
        var timetable = paired("0 23 * * 1", "0 1 * * 2");

        assertEquals(Optional.of(window(2, 23, 0, 120)), timetable.current(at(3, 0, 30, 0)));
        assertEquals(Optional.empty(), timetable.current(at(3, 1, 0, 0)));
        assertEquals(Optional.of(window(2, 23, 0, 120)), timetable.next(at(2, 12, 0, 0)));
    }

    @Test
    void startsBeforeTheSameEndTakeTheEarliest() {
        var timetable = paired("*/10 20 * * *", "0 21 * * *");

        assertEquals(Optional.of(window(2, 20, 0, 60)), timetable.current(at(2, 20, 35, 0)));
        // All of them end at 21:00, and none starts after it until the next day.
        assertEquals(Optional.empty(), timetable.current(at(2, 21, 0, 0)));
    }

    @Test
    void aStartInTheMinuteOfAnEndOpensTheWindowAfterIt() {
        var timetable = paired("0 21 * * *", "0 21 * * *");

        assertEquals(Optional.of(new Window(at(2, 21, 0, 0), at(3, 21, 0, 0))), timetable.current(at(2, 21, 0, 0)));
        assertEquals(Optional.of(new Window(at(2, 21, 0, 0), at(3, 21, 0, 0))), timetable.current(at(3, 20, 59, 0)));
    }

    @Test
    void anEndMoreThanSevenDaysAwayGivesNoWindow() {
        var timetable = paired("0 20 1 * *", "0 20 15 * *");

        assertEquals(Optional.empty(), timetable.current(at(5, 12, 0, 0)));
        assertEquals(Optional.empty(), timetable.next(at(1, 12, 0, 0)));
    }

    @Test
    void aLaterStartWithinSevenDaysOfTheEndStillHasAWindow() {
        var timetable = paired("0 20 * * *", "0 20 10 * *");

        assertEquals(Optional.of(new Window(at(3, 20, 0, 0), at(10, 20, 0, 0))), timetable.next(at(1, 12, 0, 0)));
        assertEquals(Optional.of(new Window(at(3, 20, 0, 0), at(10, 20, 0, 0))), timetable.current(at(4, 12, 0, 0)));
    }

    @Test
    void anEverySecondMinuteStartIsCheapAndCurrent() {
        var timetable = paired("* * * * *", "0 21 * * *");

        assertEquals(Optional.of(new Window(at(2, 21, 0, 0), at(3, 21, 0, 0))), timetable.current(at(2, 21, 5, 0)));
    }

    @Test
    void pairedAndFixedLinesTakeTheEarliestStart() {
        var timetable = new CronTimetable(List.of(
                new CronStart.Fixed(Cron.parse("0 19 * * *"), Duration.ofMinutes(30)),
                new CronStart.Paired(Cron.parse("0 20 * * *"), Cron.parse("0 22 * * *"))), Duration.ofMinutes(30));

        assertEquals(Optional.of(window(2, 19, 0, 30)), timetable.next(at(2, 12, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 120)), timetable.next(at(2, 19, 0, 0)));
        assertEquals(Optional.of(window(2, 20, 0, 120)), timetable.current(at(2, 21, 0, 0)));
    }
}
