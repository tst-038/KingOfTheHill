package io.github.tst_038.koth.world;

import io.github.tst_038.koth.config.KothConfig;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Sends players out of the KOTH world, to {@code world.leave} or else the main world's spawn, and drops the hill if they
 * hold it: the one way out for {@code /koth leave}, {@code /kotha kick}, a new ban and a banned player coming in. Server
 * thread only.
 */
public final class SendOut {

    private final Supplier<Optional<KothConfig>> config;
    private final Server server;
    private final Consumer<UUID> dropHill;
    private final Messages messages;
    private final Executor serverThread;

    /** @param dropHill frees the hill at once if the player holds it */
    public SendOut(Supplier<Optional<KothConfig>> config, Server server, Consumer<UUID> dropHill, Messages messages, Executor serverThread) {
        this.config = config;
        this.server = server;
        this.dropHill = dropHill;
        this.messages = messages;
        this.serverThread = serverThread;
    }

    /**
     * Sends the player out, and once they are out tells them {@code message}.
     *
     * @return false, doing nothing, when the player is not in the KOTH world
     */
    public boolean send(Player player, String message, TagResolver... tags) {
        var koth = config.get().filter(current -> player.getWorld().equals(current.world()));
        if (koth.isEmpty()) {
            return false;
        }
        dropHill.accept(player.getUniqueId());
        var out = koth.get().leave().orElseGet(() -> server.getWorlds().getFirst().getSpawnLocation());
        player.teleportAsync(out).thenAcceptAsync(moved -> {
            if (moved) {
                messages.send(player, message, tags);
            }
        }, serverThread);
        return true;
    }
}
