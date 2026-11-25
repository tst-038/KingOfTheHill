package io.github.tst_038.koth.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tst_038.minigami.api.item.ItemLook;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.reward.RewardReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ShopReaderTest {

    private static final String ENTRY = """
            title: "Shop"
            entries:
              a:
                %s
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

    private ShopEntry only(String yaml) {
        return load(yaml).shop().orElseThrow().entries().getFirst();
    }

    private static String entry(String place) {
        return ENTRY.formatted(place, "[ { item: diamond } ]");
    }

    @Test
    void theBundledShopLoadsWithNoProblems() throws IOException {
        try (var in = getClass().getResourceAsStream("/shop.yml")) {
            var loaded = load(new String(Objects.requireNonNull(in, "bundled shop.yml").readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(List.of(), loaded.problems());
            var shop = loaded.shop().orElseThrow();
            assertEquals(List.of("diamonds", "anvil"), shop.entries().stream().map(ShopEntry::id).toList());
            assertEquals(Optional.of("essentials.anvil"), shop.entries().get(1).ownedPermission());
            assertEquals(Optional.of(new Place(0, 24)), shop.entries().get(1).place());
        }
    }

    @Test
    void aSlotIsOnTheFirstPageUnlessAPageIsGiven() {
        assertEquals(Optional.of(new Place(0, 3)), only(entry("slot: 3")).place());
        assertEquals(Optional.of(new Place(1, 44)), only(entry("page: 2\n    slot: 44")).place());
    }

    @Test
    void anEntryWithoutASlotHasNoPlace() {
        assertEquals(Optional.empty(), only(entry("")).place());
    }

    @Test
    void aPageWithoutASlotIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.page: needs a slot too; an entry without a slot takes the next free one"), problems(entry("page: 2")));
    }

    @Test
    void twoEntriesInOnePlaceAreAProblem() {
        var twice = entry("slot: 2") + entry("slot: 2").substring(ENTRY.indexOf("  a:")).replace("  a:", "  b:");

        assertEquals(List.of("shop.yml: entries.b.slot: is used by another entry"), problems(twice));
    }

    @Test
    void theSameSlotOnAnotherPageIsFine() {
        var two = entry("slot: 2") + entry("page: 2\n    slot: 2").substring(ENTRY.indexOf("  a:")).replace("  a:", "  b:");

        assertEquals(List.of(), problems(two));
    }

    @Test
    void aSlotInTheBarIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.slot: must be between 0 and 44"), problems(entry("slot: 45")));
    }

    @Test
    void anEntryWithNoRewardsIsAProblem() {
        assertEquals(List.of("shop.yml: entries.a.rewards: needs at least one reward"), problems(ENTRY.formatted("slot: 0", "[]")));
    }

    @Test
    void aSavedItemThatIsNotSavedIsReported() {
        var problems = problems(ENTRY.formatted("slot: 0", "[ { saved: koth-crate-key } ]"));

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().startsWith("shop.yml: entries.a.rewards[0].saved: no saved item 'koth-crate-key'"), problems.getFirst());
    }

    @Test
    void aFileWithProblemsLoadsNoShop() {
        assertTrue(load(ENTRY.formatted("slot: 45", "[]")).shop().isEmpty());
    }

    @Test
    void anOwnedPermissionIsOptional() {
        assertEquals(Optional.empty(), only(entry("slot: 0")).ownedPermission());
    }

    @Test
    void theEditorHasDefaultsAShopMayChange() {
        var plain = load(entry("")).shop().orElseThrow();
        var changed = load("editor: { new-cost: 250, cost-step: 50 }\n" + entry("")).shop().orElseThrow();

        assertEquals(List.of(Shop.NEW_COST, Shop.COST_STEP), List.of(plain.newCost(), plain.costStep()));
        assertEquals(List.of(250, 50), List.of(changed.newCost(), changed.costStep()));
    }

    private static final String ICON = """
            title: "Shop"
            entries:
              a:
                cost: 5
                icon: %s
                rewards: [ { item: diamond } ]
            """;

    private ShopEntry.Icon icon(String icon) {
        return only(ICON.formatted(icon)).icon();
    }

    @Test
    void aPlainIconHasAnItemANameAndLore() {
        assertEquals(new ShopEntry.Icon.Plain(Material.ANVIL, "Anvil", List.of("a"), OptionalInt.empty(), ItemLook.NONE),
                icon("{ item: anvil, name: Anvil, lore: [a] }"));
    }

    @Test
    void aSavedIconNeedsNoNameOrLore() {
        assertEquals(new ShopEntry.Icon.Saved("koth-crate-key", Optional.empty(), Optional.empty(), OptionalInt.empty(), ItemLook.NONE),
                icon("{ saved: koth-crate-key }"));
    }

    @Test
    void aSavedIconMayReplaceTheNameAndLore() {
        assertEquals(new ShopEntry.Icon.Saved("koth-crate-key", Optional.of("Key"), Optional.of(List.of("a")), OptionalInt.empty(), ItemLook.NONE),
                icon("{ saved: koth-crate-key, name: Key, lore: [a] }"));
    }

    @Test
    void anIconMayGlowHaveAModelAndAnAmount() {
        var look = new ItemLook(Optional.of(true), Optional.of(new NamespacedKey("myserver", "ruby")), Optional.of(2f));

        assertEquals(new ShopEntry.Icon.Plain(Material.DIAMOND, "Ruby", List.of(), OptionalInt.of(8), look),
                icon("{ item: diamond, name: Ruby, amount: 8, glow: true, item-model: 'myserver:ruby', custom-model-data: 2 }"));
        assertEquals(new ShopEntry.Icon.Saved("koth-crate-key", Optional.empty(), Optional.empty(), OptionalInt.empty(),
                new ItemLook(Optional.of(true), Optional.empty(), Optional.empty())), icon("{ saved: koth-crate-key, glow: true }"));
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
