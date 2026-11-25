package io.github.tst_038.koth.setup;

import io.github.tst_038.minigami.api.config.ConfigLocations;
import io.github.tst_038.minigami.api.config.FileEditor;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.minigami.api.setup.MiniGamiSetup;
import io.github.tst_038.koth.config.KothConfig;
import io.github.tst_038.koth.config.KothConfigLoader;
import io.github.tst_038.koth.config.KothFiles;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * What {@code /kotha setup} does: sets the hill, the colour box, the spawn and where players are sent out to in
 * {@code config.yml} from where the player is, and selects or shows the boxes that are set. Server thread only.
 */
public final class KothSetup {

    /** A box of {@code config.yml} set from a WorldEdit selection. */
    public enum Box {
        POINT("point.region", "setup.point"),
        COLOURS("point.colour-blocks", "setup.colours");

        private final String path;
        private final String name;

        Box(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private final MiniGamiSetup setup;
    private final FileEditor edits;
    private final Messages messages;
    private final Supplier<Optional<KothConfig>> config;

    /** @param config the config in use, if any */
    public KothSetup(MiniGamiSetup setup, FileEditor edits, Messages messages, Supplier<Optional<KothConfig>> config) {
        this.setup = setup;
        this.edits = edits;
        this.messages = messages;
        this.config = config;
    }

    /** Sets the box to the player's WorldEdit selection. */
    public void set(Player player, Box box) {
        var selected = setup.selection(player, box.path);
        if (selected.isEmpty()) {
            messages.send(player, setup.hasWorldEdit() ? "setup.no-selection" : "setup.no-worldedit");
            return;
        }
        var region = selected.get().toConfig();
        writeInWorld(player, file -> file.set(box.path, region), "setup.box-set", name(box));
    }

    /** Makes the box, as it is in use, the player's WorldEdit selection. */
    public void select(Player player, Box box) {
        if (!setup.hasWorldEdit()) {
            messages.send(player, "setup.no-worldedit");
            return;
        }
        inUse(player, box).ifPresent(region -> {
            setup.select(player, region);
            messages.send(player, "setup.box-selected", name(box));
        });
    }

    /** Outlines the box, as it is in use, for the player. */
    public void show(Player player, Box box) {
        inUse(player, box).ifPresent(region -> {
            if (!player.getWorld().equals(region.world())) {
                messages.send(player, "setup.wrong-world", Placeholder.unparsed("world", region.world().getName()));
                return;
            }
            setup.show(player, region);
            messages.send(player, "setup.box-showing", name(box), Formatter.number("seconds", MiniGamiSetup.OUTLINE_SECONDS));
        });
    }

    /** Sets the spawn of {@code /koth join} to where the player stands. */
    public void spawn(Player player) {
        var spawn = ConfigLocations.write(player.getLocation());
        writeInWorld(player, file -> file.set("world.spawn", spawn), "setup.spawn-set");
    }

    /** Sets where players sent out of the KOTH world go to where the player stands, which must be in another world. */
    public void leave(Player player) {
        var here = player.getWorld();
        var leave = ConfigLocations.writeWithWorld(player.getLocation());
        var kothWorld = config.get().map(KothConfig::world);
        // Checked off the server thread, so against the worlds known now: the player's and the KOTH world in use.
        edits.editAndReload(player, KothFiles.CONFIG, file -> file.set("world.leave", leave),
                file -> KothConfigLoader.load(file, name -> name.equals(here.getName()) ? Optional.of(here)
                        : kothWorld.filter(world -> world.getName().equals(name))).problems(), "setup.leave-set");
    }

    private Optional<Region> inUse(Player player, Box box) {
        var current = config.get();
        if (current.isEmpty()) {
            messages.send(player, "setup.not-loaded");
            return Optional.empty();
        }
        var point = current.get().point();
        if (box == Box.POINT) {
            return Optional.of(point.region());
        }
        if (point.colourBlocks().isEmpty()) {
            messages.send(player, "setup.default-colours");
        }
        return Optional.of(point.colourBox());
    }

    /** Writes the change if the player is in the file's KOTH world, and only if the file is usable afterwards. */
    private void writeInWorld(Player player, Consumer<YamlConfiguration> change, String done, TagResolver... tags) {
        var world = player.getWorld();
        var worldName = world.getName();
        edits.editAndReload(player, KothFiles.CONFIG, file -> {
            requireWorld(file, worldName);
            change.accept(file);
        }, file -> KothConfigLoader.load(file, name -> name.equals(worldName) ? Optional.of(world) : Optional.empty()).problems(), done, tags);
    }

    /** Refuses a change made from another world than the file's KOTH world. */
    static void requireWorld(ConfigurationSection file, String playerWorld) {
        var kothWorld = file.getString("world.name", "");
        if (!kothWorld.equals(playerWorld)) {
            throw new FileEditor.Refusal("setup.wrong-world", Placeholder.unparsed("world", kothWorld));
        }
    }

    private TagResolver name(Box box) {
        return Placeholder.component("box", messages.render(box.name));
    }
}
