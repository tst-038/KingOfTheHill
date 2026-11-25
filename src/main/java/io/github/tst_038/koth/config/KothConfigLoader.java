package io.github.tst_038.koth.config;

import io.github.tst_038.minigami.api.config.ConfigProblem;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.config.YamlFiles;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Turns the text of {@code config.yml} into a {@link KothConfig}, or into the problems that stop it. */
public final class KothConfigLoader {

    private KothConfigLoader() {
    }

    /**
     * @param config the config, if the file is usable; the problems are there either way
     * @param file the file as read, empty if it isn't YAML
     * @param keys the keys it has and the reader asked for, to complete in {@code /kotha config}
     */
    public record Loaded(Optional<KothConfig> config, List<ConfigProblem> problems, ConfigurationSection file, SortedMap<String, List<String>> keys) {
    }

    /** Looks up the world, so run it on the server thread. */
    public static Loaded load(String yaml, WorldLookup worlds) {
        var section = new YamlConfiguration();
        try {
            section.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            return new Loaded(Optional.empty(), List.of(new ConfigProblem(KothFiles.CONFIG, "", "can't be read: " + e.getMessage())), new YamlConfiguration(),
                    Collections.emptySortedMap());
        }
        return load(section, worlds);
    }

    /** The file already read. Looks up the world, so run it on the server thread unless the lookup needs no server. */
    public static Loaded load(ConfigurationSection section, WorldLookup worlds) {
        var reader = ConfigReader.of(KothFiles.CONFIG, section);
        try {
            var config = KothConfigReader.read(reader, worlds);
            return new Loaded(Optional.of(config), List.of(), section, YamlFiles.keys(section, reader));
        } catch (InvalidConfigException e) {
            return new Loaded(Optional.empty(), e.problems(), section, YamlFiles.keys(section, reader));
        }
    }
}
