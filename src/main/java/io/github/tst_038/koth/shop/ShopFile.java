package io.github.tst_038.koth.shop;

import io.github.tst_038.minigami.api.config.FileEditor.Refusal;
import io.github.tst_038.minigami.api.menu.Place;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The changes {@code /kotha shop} and the shop editor make to the text of {@code shop.yml}, keeping its shape: each
 * refuses with a {@link Refusal} rather than leave the file worse. Nothing else in the file is touched.
 */
final class ShopFile {

    private ShopFile() {
    }

    /** Adds an entry at the place, which must not be another entry's, or without one, so it takes the next free slot. */
    static void add(ConfigurationSection file, String id, int cost, Map<String, Object> icon, Map<String, Object> reward, Optional<Place> place) {
        if (file.contains(path(id))) {
            throw new Refusal("shop-edit.exists", Placeholder.unparsed("id", id));
        }
        place.ifPresent(wanted -> refuseTaken(file, id, wanted));
        var entry = file.createSection(path(id));
        place.ifPresent(wanted -> put(entry, wanted));
        entry.set("cost", cost);
        entry.createSection("icon", icon);
        entry.set("rewards", List.of(reward));
    }

    static void cost(ConfigurationSection file, String id, int cost) {
        entry(file, id).set("cost", cost);
    }

    /** Raises the cost by {@code change}, or lowers it, though not below 1. @return the new cost */
    static int addCost(ConfigurationSection file, String id, int change) {
        var entry = entry(file, id);
        int cost = Math.clamp((long) entry.getInt("cost", 1) + change, 1, Integer.MAX_VALUE);
        entry.set("cost", cost);
        return cost;
    }

    /** Moves the entry to the place, which must not be another entry's. */
    static void place(ConfigurationSection file, String id, Place place) {
        var entry = entry(file, id);
        refuseTaken(file, id, place);
        put(entry, place);
    }

    /** Puts each entry where the other one is. */
    static void swap(ConfigurationSection file, String id, Place place, String other, Place otherPlace) {
        var entry = entry(file, id);
        var second = entry(file, other);
        put(entry, otherPlace);
        put(second, place);
    }

    static void remove(ConfigurationSection file, String id) {
        entry(file, id);
        file.set(path(id), null);
    }

    private static void refuseTaken(ConfigurationSection file, String id, Place place) {
        var entries = file.getConfigurationSection("entries");
        if (entries == null) {
            return;
        }
        for (String other : entries.getKeys(false)) {
            var entry = entries.getConfigurationSection(other);
            if (!other.equals(id) && entry != null && entry.contains("slot")
                    && entry.getInt("slot") == place.slot() && entry.getInt("page", 1) - 1 == place.page()) {
                throw new Refusal("shop-edit.slot-taken", Placeholder.unparsed("id", other));
            }
        }
    }

    /** Page 1, the usual one, is left out. */
    private static void put(ConfigurationSection entry, Place place) {
        entry.set("page", place.page() == 0 ? null : place.page() + 1);
        entry.set("slot", place.slot());
    }

    private static ConfigurationSection entry(ConfigurationSection file, String id) {
        var entry = file.getConfigurationSection(path(id));
        if (entry == null) {
            throw new Refusal("shop-edit.unknown", Placeholder.unparsed("id", id));
        }
        return entry;
    }

    private static String path(String id) {
        return "entries." + id;
    }
}
