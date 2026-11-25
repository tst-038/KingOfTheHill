package io.github.tst_038.koth.round;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.koth.message.StatusResolver;
import io.github.tst_038.koth.restriction.Refusal;
import io.github.tst_038.koth.schedule.Window;
import io.github.tst_038.koth.text.Durations;
import io.github.tst_038.koth.text.Moments;
import java.time.Duration;
import java.time.LocalDateTime;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.World;

/** Tells players what KOTH is doing: the whole server hears when it starts and ends, the KOTH world who holds the hill. */
final class Announcer {

    private final Server server;
    private final Messages messages;

    Announcer(Server server, Messages messages) {
        this.server = server;
        this.messages = messages;
    }

    void warning(Duration before, LocalDateTime start) {
        messages.send(server, "announce.warning", Placeholder.unparsed("time", Durations.format(before)), Placeholder.unparsed("start", Moments.format(start)));
    }

    void started(Window window) {
        messages.send(server, "announce.started", Placeholder.unparsed("end", Moments.format(window.end())));
    }

    void waiting(Window window, Refusal refusal) {
        messages.send(server, "announce.waiting", Placeholder.component("reason", StatusResolver.reason(messages, refusal)),
                Placeholder.unparsed("end", Moments.format(window.end())));
    }

    /** Why a start was refused, as plain text for the log. */
    String reason(Refusal refusal) {
        return PlainTextComponentSerializer.plainText().serialize(StatusResolver.reason(messages, refusal));
    }

    void ended() {
        messages.send(server, "announce.ended");
    }

    void captured(World world, String player) {
        messages.send(world, "announce.captured", Placeholder.unparsed("player", player));
    }

    void dropped(World world, String player) {
        messages.send(world, "announce.dropped", Placeholder.unparsed("player", player));
    }
}
