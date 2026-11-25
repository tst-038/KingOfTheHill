package io.github.tst_038.koth.round;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.koth.message.StatusResolver;
import io.github.tst_038.koth.restriction.Refusal;
import io.github.tst_038.koth.schedule.Window;
import io.github.tst_038.minigami.core.time.Durations;
import io.github.tst_038.koth.text.Moments;
import java.time.Duration;
import java.time.LocalDateTime;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Tells players what KOTH is doing: the whole server hears when it starts and ends, the KOTH world who holds the hill. */
final class Announcer {

    /** How long a link in chat keeps working; by then the window it announced is over or long since joined. */
    private static final Duration JOIN_LINK_LIFETIME = Duration.ofHours(12);

    private final Server server;
    private final Messages messages;
    private final Joining joining;

    Announcer(Server server, Messages messages, Joining joining) {
        this.server = server;
        this.messages = messages;
        this.joining = joining;
    }

    /** The {@code <join>} tag: a click that sends the player there at once, without the client asking to confirm a command. */
    private TagResolver join() {
        return Placeholder.styling("join", ClickEvent.callback(
                (Audience audience) -> {
                    if (audience instanceof Player player) {
                        joining.joinFromLink(player);
                    }
                },
                ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(JOIN_LINK_LIFETIME).build()));
    }

    void warning(Duration before, LocalDateTime start) {
        messages.send(server, "announce.warning", Placeholder.unparsed("time", Durations.format(before)), Placeholder.unparsed("start", Moments.format(start)), join());
    }

    void started(Window window) {
        messages.send(server, "announce.started", Placeholder.unparsed("end", Moments.format(window.end())), join());
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

    void bar(World world, HillBar bar) {
        world.sendActionBar(switch (bar) {
            case HillBar.Free free -> messages.render("bar.free");
            case HillBar.Holding holding -> messages.render("bar.holding", Placeholder.unparsed("player", holding.holder()));
            case HillBar.Capturing capturing -> messages.render("bar.capturing", Placeholder.unparsed("player", capturing.taker()),
                    Placeholder.unparsed("seconds", String.valueOf(capturing.seconds())), Placeholder.unparsed("needed", String.valueOf(capturing.needed())));
            case HillBar.Contested contested -> messages.render("bar.contested");
        });
    }
}
