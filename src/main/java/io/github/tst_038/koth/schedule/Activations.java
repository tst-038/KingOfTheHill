package io.github.tst_038.koth.schedule;

import io.github.tst_038.koth.restriction.Refusal;
import io.github.tst_038.koth.restriction.Situation;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Turns a {@link Plan} and the passing time into the moments KOTH starts, warns, is skipped and ends.
 * The server thread ticks it about once a second. Not thread-safe.
 *
 * <p>Every window is handled once: a window that was skipped, stopped or has ended is not offered again.
 * When a window opens (or is already open when this was created, as after a restart) and a restriction refuses,
 * it waits: the restrictions are asked again every tick, and KOTH starts as soon as none refuses, running until the
 * window's own end. A window that ends while it waits is skipped. A forced start ignores the restrictions.
 * Warnings are given once each, never for a moment before this was created,
 * and never while KOTH runs. A window that was skipped or stopped is handed to the caller to remember, and is not
 * resumed by the next {@code Activations} either.
 */
public final class Activations {

    private final Consumer<LocalDateTime> onDismissed;
    private Plan plan;
    private Optional<Window> running = Optional.empty();
    private Optional<ActivationEvent.Waiting> waiting = Optional.empty();
    /** The start of the window last handled. */
    private Optional<LocalDateTime> handled;
    private LocalDateTime previousTick;

    public Activations(Plan plan, LocalDateTime createdAt) {
        this(plan, createdAt, Optional.empty(), start -> { });
    }

    /**
     * @param dismissedBefore the start of the window that was skipped or stopped before this was created, if any: it is not resumed
     * @param onDismissed told the start of each window that is skipped or stopped, to remember it for the next {@code dismissedBefore}
     */
    public Activations(Plan plan, LocalDateTime createdAt, Optional<LocalDateTime> dismissedBefore, Consumer<LocalDateTime> onDismissed) {
        this.plan = plan;
        this.previousTick = createdAt;
        this.handled = dismissedBefore;
        this.onDismissed = onDismissed;
    }

    /** A reload: the new plan applies from now on, while a running window and the one last handled carry on. */
    public void replan(Plan plan) {
        this.plan = plan;
    }

    public List<ActivationEvent> tick(LocalDateTime now, Situation situation) {
        var events = new ArrayList<ActivationEvent>();
        running.filter(window -> !now.isBefore(window.end())).ifPresent(window -> {
            events.add(new ActivationEvent.Ended(window));
            running = Optional.empty();
        });
        waiting.ifPresent(wait -> events.addAll(keepWaiting(wait, now, situation)));
        if (running.isEmpty() && waiting.isEmpty()) {
            plan.timetable().current(now).filter(window -> !handled.equals(Optional.of(window.start()))).ifPresent(window -> {
                handled = Optional.of(window.start());
                events.add(refusal(situation).<ActivationEvent>map(refusal -> wait(window, refusal)).orElseGet(() -> started(window)));
            });
        }
        if (running.isEmpty()) {
            plan.timetable().next(now).ifPresent(next -> {
                for (var before : plan.warnings()) {
                    var due = next.start().minus(before);
                    if (due.isAfter(previousTick) && !due.isAfter(now)) {
                        events.add(new ActivationEvent.Warning(before, next.start()));
                    }
                }
            });
        }
        previousTick = now;
        return events;
    }

    /** Staff start: runs now for the given length, or the timetable's, whatever the restrictions say. Empty if already running. */
    public Optional<ActivationEvent> forceStart(LocalDateTime now, Optional<Duration> length) {
        if (running.isPresent()) {
            return Optional.empty();
        }
        waiting = Optional.empty();
        // The forced run stands in for any window open right now.
        plan.timetable().current(now).ifPresent(window -> handled = Optional.of(window.start()));
        return Optional.of(started(new Window(now, now.plus(length.orElse(plan.timetable().length())))));
    }

    /** Staff stop. The window open right now is not started again. Empty if nothing is running or waiting. */
    public Optional<ActivationEvent> forceStop(LocalDateTime now) {
        var window = running;
        if (window.isEmpty()) {
            return waiting.map(wait -> {
                waiting = Optional.empty();
                onDismissed.accept(wait.window().start());
                return new ActivationEvent.Cancelled(wait.window());
            });
        }
        running = Optional.empty();
        plan.timetable().current(now).ifPresent(open -> {
            handled = Optional.of(open.start());
            onDismissed.accept(open.start());
        });
        return Optional.of(new ActivationEvent.Ended(window.get()));
    }

    public Optional<Window> running() {
        return running;
    }

    /** The window that is open and refused by a restriction, with the latest refusal. */
    public Optional<ActivationEvent.Waiting> waiting() {
        return waiting;
    }

    public Optional<LocalDateTime> nextStart(LocalDateTime now) {
        return plan.timetable().next(now).map(Window::start);
    }

    private Optional<Refusal> refusal(Situation situation) {
        return plan.restrictions().stream()
                .map(restriction -> restriction.check(situation))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private ActivationEvent wait(Window window, Refusal refusal) {
        var event = new ActivationEvent.Waiting(window, refusal);
        waiting = Optional.of(event);
        return event;
    }

    /** One tick of a window that is waiting: it is skipped when it has ended, and starts if nothing refuses now. */
    private List<ActivationEvent> keepWaiting(ActivationEvent.Waiting wait, LocalDateTime now, Situation situation) {
        if (!now.isBefore(wait.window().end())) {
            waiting = Optional.empty();
            onDismissed.accept(wait.window().start());
            return List.of(new ActivationEvent.Skipped(wait.refusal()));
        }
        var refusal = refusal(situation);
        if (refusal.isEmpty()) {
            waiting = Optional.empty();
            return List.of(started(wait.window()));
        }
        waiting = Optional.of(new ActivationEvent.Waiting(wait.window(), refusal.get()));
        return List.of();
    }

    private ActivationEvent started(Window window) {
        running = Optional.of(window);
        return new ActivationEvent.Started(window);
    }
}
