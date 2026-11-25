package io.github.tst_038.koth.round;

import io.github.tst_038.minigami.api.region.Region;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/** Finds who stands on the hill: alive, playing, on the ground inside it, and allowed to play: not in a match nor banned. Server thread only. */
final class PointWatcher {

    private final Region region;
    private final Predicate<UUID> mayPlay;

    PointWatcher(Region region, Predicate<UUID> mayPlay) {
        this.region = region;
        this.mayPlay = mayPlay;
    }

    List<UUID> onPoint() {
        return region.world().getPlayers().stream()
                .filter(player -> !player.isDead())
                .filter(PointWatcher::playing)
                .filter(player -> mayPlay.test(player.getUniqueId()))
                .filter(player -> region.containsStanding(player.getLocation()))
                .map(Player::getUniqueId)
                .toList();
    }

    private static boolean playing(Player player) {
        var mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
}
