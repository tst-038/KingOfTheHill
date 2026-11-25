package io.github.tst_038.koth.shop;

import io.github.tst_038.minigami.api.menu.MenuClick;
import io.github.tst_038.minigami.api.menu.MenuContent;
import io.github.tst_038.minigami.api.menu.MenuItem;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /kotha shop}: the shop as players see it, changed by clicking. Left click picks an entry up, and a second left
 * click puts it on an empty slot or swaps it with the entry there; shift-left and shift-right raise and lower its cost by
 * the shop's step; right click twice removes it; a left click on an empty slot sells the item held in the main hand there.
 * Every change goes through {@link ShopEditor}, and the reload after it redraws the menu. Nothing is bought here. Server
 * thread only.
 */
final class ShopEditorView implements MenuContent {

    private record Picked(String id, Place place) {
    }

    private final Player viewer;
    private final Supplier<Shop> shop;
    private final ShopIcons icons;
    private final Messages messages;
    private final ShopEditor editor;
    private final String permission;
    private Picked picked;
    private String removing;

    /** @param permission needed for every click, as it may be taken away while the menu is open */
    ShopEditorView(Player viewer, Supplier<Shop> shop, ShopIcons icons, Messages messages, ShopEditor editor, String permission) {
        this.viewer = viewer;
        this.shop = shop;
        this.icons = icons;
        this.messages = messages;
        this.editor = editor;
        this.permission = permission;
    }

    @Override
    public Component title() {
        return messages.render("shop-edit.menu-title");
    }

    @Override
    public List<MenuItem> items() {
        int step = shop.get().costStep();
        return shop.get().entries().stream().map(entry -> {
            var lines = new ArrayList<Component>();
            lines.add(Component.empty());
            if (picked != null && picked.id().equals(entry.id())) {
                lines.add(messages.render("shop-edit.menu-picked"));
            } else if (entry.id().equals(removing)) {
                lines.add(messages.render("shop-edit.menu-confirm-remove"));
            } else {
                lines.add(messages.render("shop-edit.menu-move"));
                lines.add(messages.render("shop-edit.menu-cost", Placeholder.unparsed("step", String.valueOf(step))));
                lines.add(messages.render("shop-edit.menu-remove"));
            }
            lines.add(messages.render("shop-edit.menu-id", Placeholder.unparsed("id", entry.id())));
            var icon = icons.entry(entry, viewer, lines);
            if (picked != null && picked.id().equals(entry.id())) {
                icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            }
            return new MenuItem(icon, entry.place(), click -> clickEntry(entry, click));
        }).toList();
    }

    @Override
    public Optional<MenuItem> centre() {
        var help = icons.named(new ItemStack(Material.BOOK), messages.render("shop-edit.menu-help"), List.of(
                messages.render("shop-edit.menu-help-add", Placeholder.unparsed("cost", String.valueOf(shop.get().newCost()))),
                messages.render("shop-edit.menu-help-move")));
        return Optional.of(MenuItem.of(help));
    }

    @Override
    public ItemStack previous(int page, int pages) {
        return icons.arrow("menu.previous", page - 1, pages);
    }

    @Override
    public ItemStack next(int page, int pages) {
        return icons.arrow("menu.next", page + 1, pages);
    }

    @Override
    public ItemStack filler() {
        return icons.filler();
    }

    @Override
    public void clickEmpty(MenuClick click) {
        if (!allowed(click)) {
            return;
        }
        var place = click.place().orElseThrow();
        removing = null;
        if (picked != null) {
            editor.place(click.player(), picked.id(), place);
            picked = null;
            click.menu().refresh();
        } else if (click.type().isLeftClick()) {
            editor.addHeld(click.player(), place);
        }
    }

    private void clickEntry(ShopEntry entry, MenuClick click) {
        if (!allowed(click)) {
            return;
        }
        var player = click.player();
        var place = click.place().orElseThrow();
        var id = entry.id();
        switch (click.type()) {
            case LEFT -> {
                removing = null;
                if (picked == null) {
                    picked = new Picked(id, place);
                } else if (picked.id().equals(id)) {
                    picked = null;
                } else {
                    editor.swap(player, picked.id(), picked.place(), id, place);
                    picked = null;
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                removing = null;
                int step = shop.get().costStep();
                editor.changeCost(player, id, click.type() == ClickType.SHIFT_LEFT ? step : -step);
            }
            case RIGHT -> {
                picked = null;
                if (id.equals(removing)) {
                    removing = null;
                    editor.remove(player, id);
                } else {
                    removing = id;
                }
            }
            default -> {
                return;
            }
        }
        click.menu().refresh();
    }

    /** Closes the menu on whoever may no longer edit the shop. */
    private boolean allowed(MenuClick click) {
        if (click.player().hasPermission(permission)) {
            return true;
        }
        click.player().closeInventory();
        return false;
    }
}
