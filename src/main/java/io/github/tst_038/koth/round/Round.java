package io.github.tst_038.koth.round;

import io.github.tst_038.koth.hill.Hill;
import io.github.tst_038.koth.stats.KothStats;
import io.github.tst_038.koth.stats.StatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.bukkit.DyeColor;

/**
 * One run of KOTH: the hill, what happens on it each second, and the points and stats it brings. It knows the
 * world only through {@link Ground}. Server thread only.
 */
final class Round {

    private final Hill hill;
    private final DyeColor neutral;
    private final Ground ground;
    private final StatBuffer stats;
    private final RandomGenerator random;
    private Optional<DyeColor> colour = Optional.empty();
    private Optional<String> holderName = Optional.empty();

    Round(Hill hill, DyeColor neutral, Ground ground, StatBuffer stats, RandomGenerator random) {
        this.hill = hill;
        this.neutral = neutral;
        this.ground = ground;
        this.stats = stats;
        this.random = random;
    }

    void begin() {
        ground.paint(neutral);
    }

    /** One second: a holder who is gone drops the hill, then whoever stands on it takes it or earns from it. */
    void second() {
        hill.holder().filter(holder -> !ground.holderHere(holder)).ifPresent(holder -> {
            hill.drop();
            ground.dropped(holderName.orElseGet(() -> ground.nameOf(holder)));
            holderName = Optional.empty();
            colour = Optional.empty();
            ground.paint(neutral);
        });
        var onPoint = ground.onPoint();
        var tick = hill.tick(onPoint);
        tick.captured().ifPresent(player -> {
            var name = ground.nameOf(player);
            holderName = Optional.of(name);
            var dye = randomOther(colour, random);
            colour = Optional.of(dye);
            ground.paint(dye);
            ground.captured(name);
            stats.add(player, KothStats.CAPTURES, 1);
        });
        tick.earner().ifPresent(player -> {
            ground.credit(player, tick.earned());
            stats.add(player, KothStats.POINTS, tick.earned());
        });
        ground.show(bar(onPoint));
    }

    private HillBar bar(List<UUID> onPoint) {
        if (onPoint.size() > 1) {
            return new HillBar.Contested();
        }
        var taker = hill.taker();
        if (taker.isPresent()) {
            return new HillBar.Capturing(ground.nameOf(taker.get()), hill.progress(), hill.captureSeconds());
        }
        return holderName.<HillBar>map(HillBar.Holding::new).orElseGet(HillBar.Free::new);
    }

    void end() {
        ground.paint(neutral);
    }

    /** A random colour other than {@code current}; any colour when there is none. */
    static DyeColor randomOther(Optional<DyeColor> current, RandomGenerator random) {
        var others = Arrays.stream(DyeColor.values()).filter(dye -> !current.equals(Optional.of(dye))).toList();
        return others.get(random.nextInt(others.size()));
    }

    Optional<UUID> holder() {
        return hill.holder();
    }

    /** The holder's name as it was when they took the hill. */
    Optional<String> holderName() {
        return holderName;
    }

    Optional<UUID> taker() {
        return hill.taker();
    }

    int progress() {
        return hill.progress();
    }

    long heldSeconds() {
        return hill.heldSeconds();
    }
}
