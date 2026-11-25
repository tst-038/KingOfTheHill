package io.github.tst_038.koth.world;

import io.github.tst_038.koth.round.KothBans;
import io.github.tst_038.minigami.api.ban.MiniGamiPlayerBannedEvent;
import java.util.concurrent.Executor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps players banned from KOTH out of its world: sends out those who come in by any route (a teleport, a portal, logging
 * in there) and those in it when they are banned.
 */
public final class BanListener implements Listener {

    private final KothBans bans;
    private final SendOut sendOut;
    private final Executor serverThread;

    public BanListener(KothBans bans, SendOut sendOut, Executor serverThread) {
        this.bans = bans;
        this.sendOut = sendOut;
        this.serverThread = serverThread;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        keepOutLater(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        keepOutLater(event.getPlayer());
    }

    @EventHandler
    public void onBanned(MiniGamiPlayerBannedEvent event) {
        if (event.getBan().covers(KothBans.SCOPE)) {
            event.getPlayer().ifPresent(player -> sendOut.send(player, "ban.sent-out"));
        }
    }

    /** After the event, as teleporting while the server is still moving the player in goes wrong. */
    private void keepOutLater(Player player) {
        serverThread.execute(() -> {
            if (player.isOnline()) {
                bans.of(player.getUniqueId()).ifPresent(ban -> sendOut.send(player, "ban.banned", bans.tags(ban)));
            }
        });
    }
}
