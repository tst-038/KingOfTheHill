package io.github.tst_038.koth.setup;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tst_038.koth.config.FileEdits;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class KothSetupTest {

    @Test
    void onlyThePlayersInTheKothWorldCanSetItUp() {
        var file = new YamlConfiguration();
        file.set("world.name", "koth");

        assertDoesNotThrow(() -> KothSetup.requireWorld(file, "koth"));
        assertThrows(FileEdits.Refusal.class, () -> KothSetup.requireWorld(file, "world"));
        assertThrows(FileEdits.Refusal.class, () -> KothSetup.requireWorld(new YamlConfiguration(), "koth"));
    }
}
