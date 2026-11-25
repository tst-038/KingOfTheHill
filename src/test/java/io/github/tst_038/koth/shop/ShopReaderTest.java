package io.github.tst_038.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.reward.RewardReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ShopReaderTest {

    private static final String ENTRY = """
            title: "Shop"
            rows: 1
            entries:
              a:
                slot: %d
                cost: 5
                icon: { item: diamond, name: "Diamond" }
                rewards: %s
            """;

    private final MiniGamiSavedItems saved = new SavedNames(Set.of("koth-crate-key"));
    private final RewardReader rewards = new RewardReader(new SavedNames(Set.of()));

    /** Reading only asks whether an item exists; making an item needs a server. */
    record SavedNames(Set<String> names) implements MiniGamiSavedItems {

        @Override
        public Optional<ItemStack> find(String name) {
            return Optional.empty();
        }

        @Override
        public boolean has(String name) {
            return names.contains(name);
        }

        @Override
        public CompletableFuture<Void> save(String name, ItemStack item) {
            throw new UnsupportedOperationException();
        }
    }

    private ShopReader.Loaded load(String yaml) {
        return ShopReader.load(yaml, rewards, saved);
    }

    private List<String> problems(String yaml) {
        return load(yaml).problems().stream().map(Object::toString).toList();
    }

    @Test
    void theBundledShopLoadsWithNoProblems() throws IOException {
        try (var in = getClass().getResourceAsStream("/shop.yml")) {
            var loaded = load(new String(Objects.requireNonNull(in, "bundled shop.yml").readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(List.of(), loaded.problems());
            var shop = loaded.shop().orElseThrow();
            assertEquals(3, shop.rows());
            assertEquals(List.of("diamonds", "anvil"), shop.entries().stream().map(ShopEntry::id).toList());
            assertEquals(Optional.of("essentials.anvil"), shop.at(15).orElseThrow().ownedPermission());
        }
    }

    @Test
    void anEntryIsFoundByItsSlot() {
        var shop = load(ENTRY.formatted(3, "[ { item: diamond } ]")).shop().orElseThrow();

        assertEquals(Material.DIAMOND, ((ShopEntry.Icon.Plain) shop.at(3).orElseThrow().icon()).material());
        assertEquals(5, shop.at(3).orElseThrow().cost());
        assertTrue(shop.at(4).isEmpty());
    }

    @Test
    void twoEntriesInOneSlotAreAProblem() {
        var twice = ENTRY.formatted(2, "[ { item: diamond } ]") + ENTRY.substring(ENTRY.indexOf("  a:")).replace("  a:", "  b:").formatted(2, "[ { item: diamond } ]");

        assertEquals(List.of("shop.yml: entries.b.slot: is used by another entry"), problems(twice));
    }

    @Test
    void aSlotPastTheRowsIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.slot: must be between 0 and 8"), problems(ENTRY.formatted(9, "[ { item: diamond } ]")));
    }

    @Test
    void anEntryWithNoRewardsIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.rewards: needs at least one reward"), problems(ENTRY.formatted(0, "[]")));
    }

    @Test
    void aSavedItemThatIsNotSavedIsReported() {
        var problems = problems(ENTRY.formatted(0, "[ { saved: koth-crate-key } ]"));

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().startsWith("shop.yml: entries.a.rewards[0].saved: no saved item 'koth-crate-key'"), problems.getFirst());
    }

    @Test
    void aFileWithProblemsLoadsNoShop() {
        assertTrue(load(ENTRY.formatted(9, "[]")).shop().isEmpty());
    }

    @Test
    void anOwnedPermissionIsOptional() {
        var shop = load(ENTRY.formatted(0, "[ { item: diamond } ]")).shop().orElseThrow();

        assertEquals(Optional.empty(), shop.at(0).orElseThrow().ownedPermission());
    }

    private static final String ICON = """
            title: "Shop"
            rows: 1
            entries:
              a:
                slot: 0
                cost: 5
                icon: %s
                rewards: [ { item: diamond } ]
            """;

    private ShopEntry.Icon icon(String icon) {
        return load(ICON.formatted(icon)).shop().orElseThrow().at(0).orElseThrow().icon();
    }

    @Test
    void aPlainIconHasAnItemANameAndLore() {
        assertEquals(new ShopEntry.Icon.Plain(Material.ANVIL, "Anvil", List.of("a")), icon("{ item: anvil, name: Anvil, lore: [a] }"));
    }

    @Test
    void aSavedIconNeedsNoNameOrLore() {
        assertEquals(new ShopEntry.Icon.Saved("koth-crate-key", Optional.empty(), Optional.empty()), icon("{ saved: koth-crate-key }"));
    }

    @Test
    void aSavedIconMayReplaceTheNameAndLore() {
        assertEquals(new ShopEntry.Icon.Saved("koth-crate-key", Optional.of("Key"), Optional.of(List.of("a"))),
                icon("{ saved: koth-crate-key, name: Key, lore: [a] }"));
    }

    @Test
    void anIconWithBothItemAndSavedIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.icon.saved: can't be given together with item; an icon is one or the other"),
                problems(ICON.formatted("{ item: anvil, name: Anvil, saved: koth-crate-key }")));
    }

    @Test
    void aSavedIconThatIsNotSavedIsAProblem() {
        var problems = problems(ICON.formatted("{ saved: gone }"));

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().startsWith("shop.yml: entries.a.icon.saved: no saved item 'gone'"), problems.getFirst());
    }

    @Test
    void anIconWithNeitherItemNorSavedIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.icon.item: is missing"), problems(ICON.formatted("{ name: Anvil }")));
    }
}
