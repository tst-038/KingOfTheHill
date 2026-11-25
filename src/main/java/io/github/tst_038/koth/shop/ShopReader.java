package io.github.tst_038.koth.shop;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import io.github.tst_038.minigami.api.item.ItemLook;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.menu.PageLayout;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.reward.Reward;
import io.github.tst_038.minigami.api.reward.RewardReader;
import io.github.tst_038.koth.config.KothFiles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Reads {@code shop.yml}: a menu of entries, each with a cost in points, an icon, the rewards it gives and optionally a
 * place (a {@code page} from 1 and a {@code slot} from 0 to 44); entries without one fill the free slots in file order.
 */
public final class ShopReader {

    private ShopReader() {
    }

    /**
     * @param shop the shop, if the file is usable; the problems are there either way
     * @param file the file as read, empty if it isn't YAML
     * @param keys the keys it has and the reader asked for, to complete in {@code /kotha shop config}
     */
    public record Loaded(Optional<Shop> shop, List<ConfigProblem> problems, ConfigurationSection file, SortedMap<String, List<String>> keys) {
    }

    /** Turns the text of {@code shop.yml} into a {@link Shop}, or into the problems that stop it. */
    public static Loaded load(String yaml, RewardReader rewards, MiniGamiSavedItems savedItems) {
        var section = new YamlConfiguration();
        try {
            section.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            return new Loaded(Optional.empty(), List.of(new ConfigProblem(KothFiles.SHOP, "", "can't be read: " + e.getMessage())), new YamlConfiguration(),
                    Collections.emptySortedMap());
        }
        return load(section, rewards, savedItems);
    }

    /** The file already read. */
    public static Loaded load(ConfigurationSection section, RewardReader rewards, MiniGamiSavedItems savedItems) {
        var root = ConfigReader.of(KothFiles.SHOP, section);
        var shop = read(root, rewards, savedItems);
        try {
            root.check();
            return new Loaded(Optional.of(shop), List.of(), section, YamlFiles.keys(section, root));
        } catch (InvalidConfigException e) {
            return new Loaded(Optional.empty(), e.problems(), section, YamlFiles.keys(section, root));
        }
    }

    /** Problems are collected in the reader, so the caller ends with {@link ConfigReader#check()}. */
    public static Shop read(ConfigReader root, RewardReader rewards, MiniGamiSavedItems savedItems) {
        var title = root.string("title");
        int newCost = Shop.NEW_COST;
        int costStep = Shop.COST_STEP;
        if (root.has("editor")) {
            var editor = root.section("editor");
            newCost = editor.has("new-cost") ? editor.integer("new-cost", 1, Integer.MAX_VALUE) : newCost;
            costStep = editor.has("cost-step") ? editor.integer("cost-step", 1, Integer.MAX_VALUE) : costStep;
        }
        var section = root.section("entries");
        var entries = new ArrayList<ShopEntry>();
        var places = new HashSet<Place>();
        for (String id : section.keys()) {
            var reader = section.section(id);
            var entry = entry(id, reader, rewards, savedItems);
            if (entry.place().filter(place -> !places.add(place)).isPresent()) {
                reader.problem("slot", "is used by another entry");
            }
            entries.add(entry);
        }
        if (entries.isEmpty() && root.has("entries")) {
            root.problem("entries", "needs at least one entry");
        }
        return new Shop(title, entries, newCost, costStep);
    }

    private static ShopEntry entry(String id, ConfigReader entry, RewardReader rewards, MiniGamiSavedItems savedItems) {
        var place = place(entry);
        int cost = entry.integer("cost", 1, Integer.MAX_VALUE);
        var icon = icon(entry.section("icon"), savedItems);
        var given = new ArrayList<Reward>();
        for (ConfigReader reward : entry.sections("rewards")) {
            rewards.reward(reward).ifPresent(given::add);
        }
        if (given.isEmpty() && entry.has("rewards")) {
            entry.problem("rewards", "needs at least one reward");
        }
        var owned = entry.has("owned-permission") ? Optional.of(entry.string("owned-permission")) : Optional.<String>empty();
        return new ShopEntry(id, place, cost, icon, given, owned);
    }

    /** {@code page} (from 1, default 1) and {@code slot}; no slot is no place. */
    private static Optional<Place> place(ConfigReader entry) {
        int page = entry.has("page") ? entry.integer("page", 1, Integer.MAX_VALUE) : 1;
        if (!entry.has("slot")) {
            if (entry.has("page")) {
                entry.problem("page", "needs a slot too; an entry without a slot takes the next free one");
            }
            return Optional.empty();
        }
        return Optional.of(new Place(page - 1, entry.integer("slot", 0, PageLayout.CONTENT_SLOTS - 1)));
    }

    /** {@code { item, name, lore }} or {@code { saved, name?, lore? }}; exactly one of {@code item} and {@code saved}. */
    private static ShopEntry.Icon icon(ConfigReader icon, MiniGamiSavedItems savedItems) {
        var amount = icon.has("amount") ? OptionalInt.of(icon.integer("amount", 1, 99)) : OptionalInt.empty();
        var look = ItemLook.read(icon);
        if (icon.has("saved")) {
            var saved = icon.string("saved");
            if (icon.has("item")) {
                icon.string("item");
                icon.problem("saved", "can't be given together with item; an icon is one or the other");
            } else if (!saved.isEmpty() && !savedItems.has(saved)) {
                icon.problem("saved", "no saved item '" + saved + "'; save the item you hold with /mga loot save " + saved);
            }
            var name = icon.has("name") ? Optional.of(icon.string("name")) : Optional.<String>empty();
            var lore = icon.has("lore") ? Optional.of(icon.strings("lore")) : Optional.<List<String>>empty();
            return new ShopEntry.Icon.Saved(saved, name, lore, amount, look);
        }
        var material = icon.material("item");
        var name = icon.string("name");
        var lore = icon.has("lore") ? icon.strings("lore") : List.<String>of();
        return new ShopEntry.Icon.Plain(material, name, lore, amount, look);
    }
}
