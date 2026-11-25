package io.github.tst_038.koth.shop;

import io.github.tst_038.koth.text.Numbers;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** The items of the shop menus, worded from the messages. Server thread only. */
final class ShopIcons {

    private final Messages messages;
    private final MiniGamiSavedItems savedItems;
    private final Logger logger;

    ShopIcons(Messages messages, MiniGamiSavedItems savedItems, Logger logger) {
        this.messages = messages;
        this.savedItems = savedItems;
        this.logger = logger;
    }

    /** The entry's icon with its cost, marked as owned when the viewer has its permission, then the extra lines. */
    ItemStack entry(ShopEntry entry, Player viewer, List<Component> extra) {
        var own = new ArrayList<Component>();
        ItemStack item;
        switch (entry.icon()) {
            case ShopEntry.Icon.Plain plain -> {
                item = new ItemStack(plain.material());
                item.editMeta(meta -> meta.displayName(plain(messages.parse(plain.name()))));
                plain.lore().forEach(line -> own.add(plain(messages.parse(line))));
            }
            case ShopEntry.Icon.Saved saved -> {
                var found = savedItems.find(saved.saved());
                if (found.isEmpty()) {
                    logger.warning("The shop entry '" + entry.id() + "' shows the saved item '" + saved.saved()
                            + "', which has been deleted since the shop was read; showing a barrier instead.");
                    item = new ItemStack(Material.BARRIER);
                    item.editMeta(meta -> meta.displayName(plain(messages.parse(entry.name()))));
                } else {
                    item = found.get();
                    saved.nameOverride().ifPresent(name -> item.editMeta(meta -> meta.displayName(plain(messages.parse(name)))));
                    List<Component> lines = saved.loreOverride()
                            .map(texts -> texts.stream().map(text -> plain(messages.parse(text))).toList())
                            .orElseGet(() -> item.lore() == null ? List.of() : item.lore());
                    own.addAll(lines);
                }
            }
        }
        entry.icon().amount().ifPresent(item::setAmount);
        entry.icon().look().applyTo(item);
        item.editMeta(meta -> {
            var lore = new ArrayList<>(own);
            lore.add(plain(messages.render("shop.cost-line", Placeholder.unparsed("cost", Numbers.format(entry.cost())))));
            if (entry.ownedPermission().filter(viewer::hasPermission).isPresent()) {
                lore.add(plain(messages.render("shop.owned-line")));
            }
            extra.forEach(line -> lore.add(plain(line)));
            meta.lore(lore);
        });
        return item;
    }

    /** An arrow to the page, counted from 0, of so many. */
    ItemStack arrow(String key, int page, int pages) {
        return named(new ItemStack(Material.ARROW), messages.render(key, Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages))), List.of());
    }

    /** A pane with no tooltip, for the bar's empty slots. */
    ItemStack filler() {
        var pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        pane.editMeta(meta -> meta.setHideTooltip(true));
        return pane;
    }

    /** The viewer's own head. */
    ItemStack head(Player viewer, Component name) {
        var head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(viewer.getPlayerProfile()));
        return named(head, name, List.of());
    }

    ItemStack named(ItemStack item, Component name, List<Component> lore) {
        item.editMeta(meta -> {
            meta.displayName(plain(name));
            meta.lore(lore.stream().map(ShopIcons::plain).toList());
        });
        return item;
    }

    /** Item text is italic unless told otherwise; the admin's own italics stay. */
    private static Component plain(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
