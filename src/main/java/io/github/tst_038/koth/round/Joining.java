package io.github.tst_038.koth.round;

import io.github.tst_038.koth.config.KothConfig;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/**
 * Who may play KOTH, and sends a player to the KOTH world, for {@code /koth join} and for the link in the chat messages.
 * Server thread only.
 */
public final class Joining {

    /** Who may play KOTH. */
    public static final String PLAY = "koth.play";

    private final Supplier<Optional<KothConfig>> config;
    private final MiniGamiPlayers players;
    private final KothBans bans;
    private final Messages messages;
    private final Executor serverThread;

    public Joining(Supplier<Optional<KothConfig>> config, MiniGamiPlayers players, KothBans bans, Messages messages, Executor serverThread) {
        this.config = config;
        this.players = players;
        this.bans = bans;
        this.messages = messages;
        this.serverThread = serverThread;
    }

    /** For the command, which only those with {@link #PLAY} get to run. */
    public void join(Player player) {
        var current = config.get();
        if (current.isEmpty()) {
            messages.send(player, "status.not-configured");
        } else if (players.isInMatch(player.getUniqueId())) {
            messages.send(player, "join.in-match");
        } else if (!bans.keepsOut(player)) {
            player.teleportAsync(current.get().spawnOrDefault()).thenAcceptAsync(moved -> {
                if (moved) {
                    messages.send(player, "join.done");
                }
            }, serverThread);
        }
    }

    /** Whether the player counts on the hill: not in a MiniGami match nor banned from KOTH. */
    public boolean mayPlay(UUID player) {
        return !players.isInMatch(player) && bans.of(player).isEmpty();
    }

    /** For the chat link, which anyone online can click, so the permission is asked here. */
    public void joinFromLink(Player player) {
        if (player.hasPermission(PLAY)) {
            join(player);
        } else {
            messages.send(player, "join.no-permission");
        }
    }
}
