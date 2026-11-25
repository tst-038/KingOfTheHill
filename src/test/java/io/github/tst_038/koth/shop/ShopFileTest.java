package io.github.tst_038.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.koth.config.FileEdits.Refusal;
import io.github.tst_038.minigami.api.reward.RewardReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ShopFileTest {

    private static final String SHOP = """
            title: "Shop"
            rows: 1
            entries:
              diamonds:
                slot: 0
                cost: 300
                icon: { item: diamond, name: "8 diamonds" }
                rewards:
                  - { item: diamond, amount: 8 }
              anvil:
                slot: 2
                cost: 5000
                icon: { item: anvil, name: "/anvil" }
                rewards:
                  - { command: "lp user %player% permission set essentials.anvil true", name: "/anvil" }
            """;

    private static YamlConfiguration file() throws Exception {
        var file = new YamlConfiguration();
        file.loadFromString(SHOP);
        return file;
    }

    private static ShopEntry readBack(YamlConfiguration file, String id) {
        var saved = new ShopReaderTest.SavedNames(Set.of("koth-key"));
        var loaded = ShopReader.load(file.saveToString(), new RewardReader(saved), saved);
        assertEquals(List.of(), loaded.problems());
        return loaded.shop().orElseThrow().entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElseThrow();
    }

    private static void refused(String key, Executable change) {
        assertEquals(key, assertThrows(Refusal.class, change).getMessage());
    }

    @Test
    void anAddedEntryTakesTheFirstFreeSlotAndReadsBack() throws Exception {
        var file = file();
        ShopFile.add(file, "key", 50, Map.of("saved", "koth-key", "name", "Key"), Map.of("saved", "koth-key", "amount", 2));
        var entry = readBack(file, "key");
        assertEquals(1, entry.slot());
        assertEquals(50, entry.cost());
        assertEquals("Key", entry.name());
    }

    @Test
    void anAddRefusesAnIdInUseOrAFullMenu() throws Exception {
        var file = file();
        refused("shop-edit.exists", () -> ShopFile.add(file, "anvil", 1, Map.of(), Map.of()));
        for (int slot = 0; slot < 9; slot++) {
            file.set("entries.e" + slot + ".slot", slot);
        }
        refused("shop-edit.full", () -> ShopFile.add(file, "key", 1, Map.of(), Map.of()));
    }

    @Test
    void costSlotAndRemoveChangeOnlyTheirEntry() throws Exception {
        var file = file();
        ShopFile.cost(file, "anvil", 10);
        ShopFile.slot(file, "anvil", 8);
        var anvil = readBack(file, "anvil");
        assertEquals(10, anvil.cost());
        assertEquals(8, anvil.slot());
        ShopFile.remove(file, "anvil");
        assertFalse(file.contains("entries.anvil"));
        assertTrue(file.contains("entries.diamonds"));
    }

    @Test
    void anUnknownIdOrABadSlotIsRefused() throws Exception {
        var file = file();
        refused("shop-edit.unknown", () -> ShopFile.cost(file, "nope", 1));
        refused("shop-edit.unknown", () -> ShopFile.remove(file, "nope"));
        refused("shop-edit.slot-outside", () -> ShopFile.slot(file, "anvil", 9));
        refused("shop-edit.slot-taken", () -> ShopFile.slot(file, "anvil", 0));
        ShopFile.slot(file, "anvil", 2);
    }
}
