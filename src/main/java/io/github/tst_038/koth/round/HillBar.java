package io.github.tst_038.koth.round;

/** What the action bar of the KOTH world shows about the hill this second. */
sealed interface HillBar {

    /** Nobody holds it and nobody is taking it. */
    record Free() implements HillBar {}

    /** Held, and nobody else is taking it. */
    record Holding(String holder) implements HillBar {}

    /** One player stands on it alone and is taking it. */
    record Capturing(String taker, int seconds, int needed) implements HillBar {}

    /** More than one player stands on it, so nobody makes progress. */
    record Contested() implements HillBar {}
}
