package io.github.tst_038.koth.shop;

import io.github.tst_038.koth.config.FileEdits;
import io.github.tst_038.koth.config.KothFiles;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /kotha shop}: changes {@code shop.yml} by command, off the server thread and keeping its comments, then reloads
 * as {@code /kotha reload} does. An entry's item is saved in MiniGami as {@code koth-<id>}. Server thread only.
 */
public final class ShopEditor {

    private final FileEdits edits;
    private final Messages messages;
    private final MiniGamiSavedItems savedItems;
    private final Supplier<Shop> shop;
    private final Executor serverThread;
    private final Logger logger;

    public ShopEditor(FileEdits edits, Messages messages, MiniGamiSavedItems savedItems, Supplier<Shop> shop, Executor serverThread, Logger logger) {
        this.edits = edits;
        this.messages = messages;
        this.savedItems = savedItems;
        this.shop = shop;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    /** The name the entry's item is saved under. */
    public static String savedName(String id) {
        return "koth-" + id;
    }

    /** The ids of the shop in use, for completion. */
    public List<String> ids() {
        return shop.get().entries().stream().map(ShopEntry::id).toList();
    }

    /** Sells the held item: saved as the icon and given as the reward, as many as are held. */
    public void add(Player player, String id, int cost) {
        withHeldItem(player, id, (item, name) -> {
            var reward = new LinkedHashMap<String, Object>();
            reward.put("saved", savedName(id));
            if (item.getAmount() > 1) {
                reward.put("amount", item.getAmount());
            }
            return reward;
        }, cost);
    }

    /** Sells a command run from the console; the held item, saved, is the icon and gives the entry its name. */
    public void command(Player player, String id, int cost, String command) {
        withHeldItem(player, id, (item, name) -> {
            var reward = new LinkedHashMap<String, Object>();
            reward.put("command", command);
            reward.put("name", name);
            return reward;
        }, cost);
    }

    public void cost(Audience sender, String id, int cost) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.cost(file, id, cost), "shop-edit.cost-set",
                Placeholder.unparsed("id", id), Placeholder.unparsed("cost", String.valueOf(cost)));
    }

    public void slot(Audience sender, String id, int slot) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.slot(file, id, slot), "shop-edit.slot-set",
                Placeholder.unparsed("id", id), Placeholder.unparsed("slot", String.valueOf(slot)));
    }

    /** Removes the entry only; its saved item stays, as other things may use it. */
    public void remove(Audience sender, String id) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.remove(file, id), "shop-edit.removed",
                Placeholder.unparsed("id", id), Placeholder.unparsed("saved", savedName(id)));
    }

    public void list(Audience sender) {
        var entries = shop.get().entries();
        if (entries.isEmpty()) {
            messages.send(sender, "shop-edit.list-empty");
            return;
        }
        messages.send(sender, "shop-edit.list-header");
        entries.stream().sorted(Comparator.comparingInt(ShopEntry::slot)).forEach(entry -> messages.send(sender, "shop-edit.list-entry",
                Placeholder.unparsed("id", entry.id()), Placeholder.unparsed("slot", String.valueOf(entry.slot())),
                Placeholder.unparsed("cost", String.valueOf(entry.cost())), Placeholder.component("entry", messages.parse(entry.name()))));
    }

    private interface Reward {
        Map<String, Object> of(ItemStack item, String name);
    }

    /** Writes the entry, then saves the held item for it, so a refused entry never replaces a saved item. */
    private void withHeldItem(Player player, String id, Reward reward, int cost) {
        var saved = savedName(id);
        if (!MiniGamiSavedItems.isValidName(saved)) {
            messages.send(player, "shop-edit.bad-id");
            return;
        }
        var held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            messages.send(player, "shop-edit.no-item");
            return;
        }
        var item = held.clone();
        var name = MiniMessage.miniMessage().serialize(item.effectiveName());
        var icon = new LinkedHashMap<String, Object>();
        icon.put("saved", saved);
        // The buyer is told the icon's name, which would be the saved name without it.
        icon.put("name", name);
        var given = reward.of(item, name);
        TagResolver[] tags = {Placeholder.unparsed("id", id), Placeholder.unparsed("cost", String.valueOf(cost)), Placeholder.unparsed("saved", saved)};
        edits.edit(player, KothFiles.SHOP, file -> ShopFile.add(file, id, cost, icon, given), file -> List.of()).thenAccept(written -> {
            if (written) {
                savedItems.save(saved, item).whenCompleteAsync((ignored, failure) -> {
                    if (failure != null) {
                        // It stays saved until a restart, so the shop works meanwhile.
                        logger.log(Level.SEVERE, "Could not write the saved item " + saved, failure);
                        messages.send(player, "shop-edit.item-not-written", tags);
                    }
                    edits.done(player, "shop-edit.added", tags);
                }, serverThread);
            }
        });
    }
}
