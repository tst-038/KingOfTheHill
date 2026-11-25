package io.github.tst_038.koth.shop;

import io.github.tst_038.koth.config.FileEdits.Refusal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The changes {@code /kotha shop} makes to the text of {@code shop.yml}, keeping its shape: each refuses with a
 * {@link Refusal} rather than leave the file worse. Nothing else in the file is touched.
 */
final class ShopFile {

    private ShopFile() {
    }

    /** Adds an entry at the first free slot. */
    static void add(ConfigurationSection file, String id, int cost, Map<String, Object> icon, Map<String, Object> reward) {
        if (file.contains(path(id))) {
            throw new Refusal("shop-edit.exists", Placeholder.unparsed("id", id));
        }
        int slot = firstFree(file).orElseThrow(() -> new Refusal("shop-edit.full"));
        var entry = file.createSection(path(id));
        entry.set("slot", slot);
        entry.set("cost", cost);
        entry.createSection("icon", icon);
        entry.set("rewards", List.of(reward));
    }

    static void cost(ConfigurationSection file, String id, int cost) {
        entry(file, id).set("cost", cost);
    }

    /** Moves the entry to the slot, which must be in the menu and not another entry's. */
    static void slot(ConfigurationSection file, String id, int slot) {
        var entry = entry(file, id);
        int last = rows(file) * 9 - 1;
        if (slot > last) {
            throw new Refusal("shop-edit.slot-outside", Placeholder.unparsed("last", String.valueOf(last)));
        }
        var entries = file.getConfigurationSection("entries");
        for (String other : entries.getKeys(false)) {
            if (!other.equals(id) && entries.getInt(other + ".slot", -1) == slot) {
                throw new Refusal("shop-edit.slot-taken", Placeholder.unparsed("id", other));
            }
        }
        entry.set("slot", slot);
    }

    static void remove(ConfigurationSection file, String id) {
        entry(file, id);
        file.set(path(id), null);
    }

    /** The lowest slot of the menu no entry uses. */
    static OptionalInt firstFree(ConfigurationSection file) {
        var used = new HashSet<Integer>();
        var entries = file.getConfigurationSection("entries");
        if (entries != null) {
            entries.getKeys(false).forEach(id -> used.add(entries.getInt(id + ".slot", -1)));
        }
        int size = rows(file) * 9;
        for (int slot = 0; slot < size; slot++) {
            if (!used.contains(slot)) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    private static ConfigurationSection entry(ConfigurationSection file, String id) {
        var entry = file.getConfigurationSection(path(id));
        if (entry == null) {
            throw new Refusal("shop-edit.unknown", Placeholder.unparsed("id", id));
        }
        return entry;
    }

    /** As read, clamped to what a menu can have; a wrong value is reported by the reload anyway. */
    private static int rows(ConfigurationSection file) {
        return Math.clamp(file.getInt("rows", 6), 1, 6);
    }

    private static String path(String id) {
        return "entries." + id;
    }
}
