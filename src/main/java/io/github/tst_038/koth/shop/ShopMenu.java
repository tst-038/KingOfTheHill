package io.github.tst_038.koth.shop;

import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The holder of an open shop inventory, which is how {@link ShopListener} knows one and which {@link Shop} it
 * shows: a click counts against what the player saw, even if the shop has been reloaded since. Server thread only.
 */
public final class ShopMenu implements InventoryHolder {

    private final Shop shop;
    private Inventory inventory;

    private ShopMenu(Shop shop) {
        this.shop = shop;
    }

    /** A menu with the shop's icons, each marked as owned when the viewer has its permission. */
    public static Inventory create(Shop shop, Player viewer, Messages messages, MiniGamiSavedItems savedItems, Logger logger) {
        var menu = new ShopMenu(shop);
        menu.inventory = Bukkit.createInventory(menu, shop.rows() * 9, messages.parse(shop.title()));
        for (ShopEntry entry : shop.entries()) {
            menu.inventory.setItem(entry.slot(), icon(entry, viewer, messages, savedItems, logger));
        }
        return menu.inventory;
    }

    public Shop shop() {
        return shop;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack icon(ShopEntry entry, Player viewer, Messages messages, MiniGamiSavedItems savedItems, Logger logger) {
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
        item.editMeta(meta -> {
            var lore = new ArrayList<>(own);
            lore.add(plain(messages.render("shop.cost-line", Placeholder.unparsed("cost", String.valueOf(entry.cost())))));
            if (entry.ownedPermission().filter(viewer::hasPermission).isPresent()) {
                lore.add(plain(messages.render("shop.owned-line")));
            }
            meta.lore(lore);
        });
        return item;
    }

    /** Item text is italic unless told otherwise; the admin's own italics stay. */
    private static Component plain(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
