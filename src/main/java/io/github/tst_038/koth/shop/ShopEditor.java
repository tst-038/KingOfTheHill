package io.github.tst_038.koth.shop;

import io.github.tst_038.koth.config.FileEdits;
import io.github.tst_038.koth.config.KothFiles;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.menu.PageLayout;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /kotha shop} and the shop editor: change {@code shop.yml} off the server thread, keeping its comments, then
 * reload as {@code /kotha reload} does. An entry's item is saved in MiniGami as {@code koth-<id>}. Server thread only.
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

    /** {@code base}, or the first of {@code base-2}, {@code base-3}, ... that isn't taken. */
    static String freeId(String base, Predicate<String> taken) {
        var id = base;
        for (int n = 2; taken.test(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    /** The ids of the shop in use, for completion. */
    public List<String> ids() {
        return shop.get().entries().stream().map(ShopEntry::id).toList();
    }

    /** Sells the held item: saved as the icon and given as the reward, as many as are held. */
    public void add(Player player, String id, int cost) {
        add(player, id, cost, Optional.empty());
    }

    /**
     * Sells the held item at the place, for the shop's new-entry cost, under an id made from the item's type that no
     * entry or saved item has yet.
     */
    public void addHeld(Player player, Place place) {
        var ids = ids();
        var base = player.getInventory().getItemInMainHand().getType().getKey().getKey();
        var id = freeId(base, candidate -> ids.contains(candidate) || savedItems.has(savedName(candidate)));
        add(player, id, shop.get().newCost(), Optional.of(place));
    }

    private void add(Player player, String id, int cost, Optional<Place> place) {
        withHeldItem(player, id, (item, name) -> {
            var reward = new LinkedHashMap<String, Object>();
            reward.put("saved", savedName(id));
            if (item.getAmount() > 1) {
                reward.put("amount", item.getAmount());
            }
            return reward;
        }, cost, place);
    }

    /** Sells a command run from the console; the held item, saved, is the icon and gives the entry its name. */
    public void command(Player player, String id, int cost, String command) {
        withHeldItem(player, id, (item, name) -> {
            var reward = new LinkedHashMap<String, Object>();
            reward.put("command", command);
            reward.put("name", name);
            return reward;
        }, cost, Optional.empty());
    }

    public void cost(Audience sender, String id, int cost) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.cost(file, id, cost), "shop-edit.cost-set",
                Placeholder.unparsed("id", id), Placeholder.unparsed("cost", String.valueOf(cost)));
    }

    /** Raises the cost by {@code change}, or lowers it, though not below 1; counted from the file, so quick clicks add up. */
    public void changeCost(Audience sender, String id, int change) {
        var cost = new AtomicInteger();
        edits.edit(sender, KothFiles.SHOP, file -> cost.set(ShopFile.addCost(file, id, change)), file -> List.of()).thenAccept(written -> {
            if (written) {
                edits.done(sender, "shop-edit.cost-set", Placeholder.unparsed("id", id), Placeholder.unparsed("cost", String.valueOf(cost.get())));
            }
        });
    }

    public void place(Audience sender, String id, Place place) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.place(file, id, place), "shop-edit.slot-set",
                Placeholder.unparsed("id", id), page(place), slot(place));
    }

    /** Puts each entry at the other's place, as they were shown. */
    public void swap(Audience sender, String id, Place place, String other, Place otherPlace) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.swap(file, id, place, other, otherPlace), "shop-edit.swapped",
                Placeholder.unparsed("id", id), Placeholder.unparsed("other", other));
    }

    /** Removes the entry only; its saved item stays, as other things may use it. */
    public void remove(Audience sender, String id) {
        edits.editAndReload(sender, KothFiles.SHOP, file -> ShopFile.remove(file, id), "shop-edit.removed",
                Placeholder.unparsed("id", id), Placeholder.unparsed("saved", savedName(id)));
    }

    /** The entries in menu order, each with where it shows, placed or not. */
    public void list(Audience sender) {
        var entries = shop.get().entries();
        if (entries.isEmpty()) {
            messages.send(sender, "shop-edit.list-empty");
            return;
        }
        messages.send(sender, "shop-edit.list-header");
        var layout = PageLayout.of(entries.stream().map(ShopEntry::place).toList());
        IntStream.range(0, entries.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> layout.placeOf(i).page()).thenComparing(i -> layout.placeOf(i).slot()))
                .forEach(i -> {
                    var entry = entries.get(i);
                    messages.send(sender, "shop-edit.list-entry", Placeholder.unparsed("id", entry.id()), page(layout.placeOf(i)),
                            slot(layout.placeOf(i)), Placeholder.unparsed("cost", String.valueOf(entry.cost())),
                            Placeholder.component("entry", messages.parse(entry.name())));
                });
    }

    private static TagResolver page(Place place) {
        return Placeholder.unparsed("page", String.valueOf(place.page() + 1));
    }

    private static TagResolver slot(Place place) {
        return Placeholder.unparsed("slot", String.valueOf(place.slot()));
    }

    private interface Reward {
        Map<String, Object> of(ItemStack item, String name);
    }

    /** Writes the entry, then saves the held item for it, so a refused entry never replaces a saved item. */
    private void withHeldItem(Player player, String id, Reward reward, int cost, Optional<Place> place) {
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
        edits.edit(player, KothFiles.SHOP, file -> ShopFile.add(file, id, cost, icon, given, place), file -> List.of()).thenAccept(written -> {
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
