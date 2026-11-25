package io.github.tst_038.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.koth.config.FileEdits.Refusal;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.reward.RewardReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ShopFileTest {

    private static final String SHOP = """
            title: "Shop"
            entries:
              diamonds:
                slot: 0
                cost: 300
                icon: { item: diamond, name: "8 diamonds" }
                rewards:
                  - { item: diamond, amount: 8 }
              anvil:
                page: 2
                slot: 2
                cost: 5000
                icon: { item: anvil, name: "/anvil" }
                rewards:
                  - { command: "lp user %player% permission set essentials.anvil true", name: "/anvil" }
              cookie:
                cost: 1
                icon: { item: cookie, name: "Cookie" }
                rewards:
                  - { item: cookie }
            """;

    private static final Map<String, Object> ICON = Map.of("saved", "koth-key", "name", "Key");
    private static final Map<String, Object> REWARD = Map.of("saved", "koth-key", "amount", 2);

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
    void anEntryAddedWithoutAPlaceTakesTheNextFreeSlot() throws Exception {
        var file = file();
        ShopFile.add(file, "key", 50, ICON, REWARD, Optional.empty());
        var entry = readBack(file, "key");
        assertEquals(Optional.empty(), entry.place());
        assertEquals(50, entry.cost());
        assertEquals("Key", entry.name());
    }

    @Test
    void anEntryAddedAtAPlaceKeepsIt() throws Exception {
        var file = file();
        ShopFile.add(file, "key", 50, ICON, REWARD, Optional.of(new Place(2, 7)));
        assertEquals(Optional.of(new Place(2, 7)), readBack(file, "key").place());
    }

    @Test
    void anAddRefusesAnIdInUseOrAnotherEntrysPlace() throws Exception {
        var file = file();
        refused("shop-edit.exists", () -> ShopFile.add(file, "anvil", 1, ICON, REWARD, Optional.empty()));
        refused("shop-edit.slot-taken", () -> ShopFile.add(file, "key", 1, ICON, REWARD, Optional.of(new Place(1, 2))));
    }

    @Test
    void aMoveToPageOneLeavesThePageOut() throws Exception {
        var file = file();
        ShopFile.place(file, "anvil", new Place(0, 8));
        assertFalse(file.contains("entries.anvil.page"));
        assertEquals(Optional.of(new Place(0, 8)), readBack(file, "anvil").place());
        ShopFile.place(file, "cookie", new Place(3, 1));
        assertEquals(4, file.getInt("entries.cookie.page"));
    }

    @Test
    void aMoveRefusesAnotherEntrysPlaceButNotItsOwn() throws Exception {
        var file = file();
        refused("shop-edit.slot-taken", () -> ShopFile.place(file, "anvil", new Place(0, 0)));
        ShopFile.place(file, "anvil", new Place(1, 2));
        // The same slot on another page is free.
        ShopFile.place(file, "cookie", new Place(1, 0));
    }

    @Test
    void aSwapGivesEachEntryTheOthersPlace() throws Exception {
        var file = file();
        // The cookie has no slot; it is swapped from where it showed.
        ShopFile.swap(file, "diamonds", new Place(0, 0), "cookie", new Place(0, 1));
        assertEquals(Optional.of(new Place(0, 1)), readBack(file, "diamonds").place());
        assertEquals(Optional.of(new Place(0, 0)), readBack(file, "cookie").place());
    }

    @Test
    void aCostChangeStopsAtOne() throws Exception {
        var file = file();
        assertEquals(310, ShopFile.addCost(file, "diamonds", 10));
        assertEquals(1, ShopFile.addCost(file, "diamonds", -1000));
        assertEquals(1, readBack(file, "diamonds").cost());
    }

    @Test
    void costAndRemoveChangeOnlyTheirEntry() throws Exception {
        var file = file();
        ShopFile.cost(file, "anvil", 10);
        assertEquals(10, readBack(file, "anvil").cost());
        ShopFile.remove(file, "anvil");
        assertFalse(file.contains("entries.anvil"));
        assertTrue(file.contains("entries.diamonds"));
    }

    @Test
    void anUnknownIdIsRefused() throws Exception {
        var file = file();
        refused("shop-edit.unknown", () -> ShopFile.cost(file, "nope", 1));
        refused("shop-edit.unknown", () -> ShopFile.addCost(file, "nope", 1));
        refused("shop-edit.unknown", () -> ShopFile.place(file, "nope", new Place(0, 5)));
        refused("shop-edit.unknown", () -> ShopFile.swap(file, "anvil", new Place(1, 2), "nope", new Place(0, 5)));
        refused("shop-edit.unknown", () -> ShopFile.remove(file, "nope"));
    }
}
