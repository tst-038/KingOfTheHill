package io.github.tst_038.koth.schedule;

import io.github.tst_038.koth.restriction.Refusal;
import java.time.Duration;
import java.time.LocalDateTime;

/** Something {@link Activations} wants the server to act on. */
public sealed interface ActivationEvent {

    /** A start is {@code before} away; tell the players. */
    record Warning(Duration before, LocalDateTime start) implements ActivationEvent {
    }

    /** KOTH should begin now and run until the window ends. */
    record Started(Window window) implements ActivationEvent {
    }

    /** A window opened and a restriction said no; KOTH starts as soon as none does, if that is before the window ends. */
    record Waiting(Window window, Refusal refusal) implements ActivationEvent {
    }

    /** A window ended while still waiting, so it didn't run. The refusal is the last one given. */
    record Skipped(Refusal refusal) implements ActivationEvent {
    }

    /** Staff stopped the wait of a window. */
    record Cancelled(Window window) implements ActivationEvent {
    }

    /** KOTH should end now. */
    record Ended(Window window) implements ActivationEvent {
    }
}
