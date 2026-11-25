package io.github.tst_038.koth.round;

import io.github.tst_038.minigami.api.ban.Ban;
import io.github.tst_038.minigami.api.ban.MiniGamiBans;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.core.time.Durations;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** KOTH's side of MiniGami's bans: the {@code koth} scope, which a ban from {@code all} covers too. Server thread only. */
public final class KothBans {

    /** The scope KOTH registers; {@code /mga ban <players> koth} keeps players out. */
    public static final String SCOPE = "koth";

    private final MiniGamiBans bans;
    private final Messages messages;
    private final Clock clock;

    public KothBans(Plugin plugin, MiniGamiBans bans, Messages messages, Clock clock) {
        this.bans = bans;
        this.messages = messages;
        this.clock = clock;
        bans.register(plugin, SCOPE);
    }

    /** The ban that keeps the player out of KOTH, if any. */
    public Optional<Ban> of(UUID player) {
        return bans.ban(player, SCOPE);
    }

    /** Whether a ban keeps the player out; if so, they hear for how long and why. */
    public boolean keepsOut(Player player) {
        var ban = of(player.getUniqueId());
        ban.ifPresent(found -> messages.send(player, "ban.banned", tags(found)));
        return ban.isPresent();
    }

    /** {@code %left%} ("for 2h 5m" or "for good") and {@code %reason%}. */
    public TagResolver tags(Ban ban) {
        var left = ban.left(clock.instant()).map(time -> messages.render("ban.left", Placeholder.unparsed("time", Durations.format(time))))
                .orElseGet(() -> messages.render("ban.forever"));
        return TagResolver.resolver(Placeholder.component("left", left),
                Placeholder.component("reason", ban.reason().<Component>map(Component::text).orElseGet(() -> messages.render("ban.no-reason"))));
    }
}
