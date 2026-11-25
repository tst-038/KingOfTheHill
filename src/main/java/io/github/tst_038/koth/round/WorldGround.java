package io.github.tst_038.koth.round;

import io.github.tst_038.minigami.api.region.DyedBlocks;
import io.github.tst_038.koth.config.KothConfig;
import io.github.tst_038.koth.ledger.Wallet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.DyeColor;
import org.bukkit.Server;
import org.bukkit.World;

/**
 * The {@link Ground} of a running round: the KOTH world as the server has it. Made when a round starts, it finds the dyed
 * blocks of the colour box then and keeps them for the round. Server thread only.
 */
final class WorldGround implements Ground {

    private final Server server;
    private final World world;
    private final PointWatcher watcher;
    private final DyedBlocks colours;
    private final Announcer announcer;
    private final Wallet wallet;
    private final Predicate<UUID> mayPlay;

    WorldGround(Server server, KothConfig config, Announcer announcer, Wallet wallet, Predicate<UUID> mayPlay) {
        this.server = server;
        this.world = config.world();
        this.watcher = new PointWatcher(config.point().region(), mayPlay);
        this.colours = DyedBlocks.scan(config.point().colourBox(), DyedBlocks.DYED);
        this.announcer = announcer;
        this.wallet = wallet;
        this.mayPlay = mayPlay;
    }

    @Override
    public List<UUID> onPoint() {
        return watcher.onPoint();
    }

    @Override
    public boolean holderHere(UUID player) {
        var online = server.getPlayer(player);
        return online != null && online.getWorld().equals(world) && mayPlay.test(player);
    }

    @Override
    public String nameOf(UUID player) {
        var online = server.getPlayer(player);
        if (online != null) {
            return online.getName();
        }
        var offline = server.getOfflinePlayer(player).getName();
        return offline != null ? offline : player.toString();
    }

    @Override
    public void paint(DyeColor dye) {
        colours.paint(dye);
    }

    @Override
    public void captured(String player) {
        announcer.captured(world, player);
    }

    @Override
    public void dropped(String player) {
        announcer.dropped(world, player);
    }

    @Override
    public void show(HillBar bar) {
        announcer.bar(world, bar);
    }

    @Override
    public void credit(UUID player, long points) {
        wallet.credit(player, points);
    }
}
