package io.github.tst_038.koth.message;

import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.koth.restriction.Refusal;
import io.github.tst_038.koth.status.KothPlaceholders;
import io.github.tst_038.koth.status.KothStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** The status values as message placeholders, with a text of its own for no holder and no start planned. */
public final class StatusResolver {

    private StatusResolver() {
    }

    /** The values of a refusal as placeholders, such as {@code %online%} and {@code %needed%}. */
    public static TagResolver of(Refusal refusal) {
        return TagResolver.resolver(refusal.values().entrySet().stream().map(value -> Placeholder.unparsed(value.getKey(), value.getValue())).toList());
    }

    /** Why a start was refused, in words. */
    public static Component reason(Messages messages, Refusal refusal) {
        return messages.render(refusal.messageKey(), of(refusal));
    }

    public static TagResolver of(Messages messages, KothStatus status, LocalDateTime now) {
        var resolvers = new ArrayList<TagResolver>();
        KothPlaceholders.values(status, now).forEach((name, text) -> {
            var fallback = name.equals("holder") ? "placeholder.no-holder" : name.equals("next_start") ? "placeholder.never" : null;
            resolvers.add(text.isEmpty() && fallback != null ? Placeholder.component(name, messages.render(fallback)) : name.equals("holder") ? Placeholder.unparsed(name, text) : Placeholder.parsed(name, text));
        });
        return TagResolver.resolver(resolvers);
    }
}
