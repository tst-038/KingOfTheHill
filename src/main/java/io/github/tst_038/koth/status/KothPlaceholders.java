package io.github.tst_038.koth.status;

import io.github.tst_038.koth.ledger.Balances;
import io.github.tst_038.koth.schedule.ActivationEvent;
import io.github.tst_038.minigami.core.time.Durations;
import io.github.tst_038.koth.text.Numbers;
import io.github.tst_038.koth.text.Moments;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The values of KOTH for text: {@code holder}, {@code held_for}, {@code time_left}, {@code next_start} and {@code next_in}
 * for the sidebar and messages, and with {@code active}, {@code waiting} and {@code balance} for PlaceholderAPI.
 * {@code time_left} is the time left of the window while it waits for players too. A value with
 * nothing to say is empty. Safe to use from any thread.
 */
public final class KothPlaceholders {

    private final Supplier<KothStatus> status;
    private final Balances balances;
    private final Clock clock;

    public KothPlaceholders(Supplier<KothStatus> status, Balances balances, Clock clock) {
        this.status = status;
        this.balances = balances;
        this.clock = clock;
    }

    /**
     * @param viewer who asks, if anyone; only {@code balance} needs them
     * @return the text, or null for a name that isn't a value
     */
    public String resolve(Optional<UUID> viewer, String params) {
        var current = status.get();
        return switch (params) {
            case "active" -> String.valueOf(current.running().isPresent());
            case "waiting" -> String.valueOf(current.waiting().isPresent());
            case "balance" -> viewer.map(balances::of).filter(balance -> balance.isPresent()).map(balance -> String.valueOf(balance.getAsLong())).orElse("");
            case "balance_formatted" -> viewer.map(balances::of).filter(balance -> balance.isPresent()).map(balance -> Numbers.format(balance.getAsLong())).orElse("");
            default -> values(current, LocalDateTime.now(clock)).get(params);
        };
    }

    /** The values by name, empty text where there is nothing to say; shared by the sidebar and the messages. */
    public static Map<String, String> values(KothStatus status, LocalDateTime now) {
        return Map.of(
                "holder", status.holderName().orElse(""),
                "held_for", status.holderName().isPresent() ? Durations.format(Duration.ofSeconds(status.heldSeconds())) : "",
                "time_left", status.running().or(() -> status.waiting().map(ActivationEvent.Waiting::window)).map(window -> Durations.format(window.left(now))).orElse(""),
                "next_start", status.nextStart().map(Moments::format).orElse(""),
                "next_in", status.nextStart().map(start -> Durations.format(Duration.between(now, start))).orElse(""));
    }
}
