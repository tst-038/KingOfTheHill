package io.github.tst_038.koth.shop;

import io.github.tst_038.minigami.api.item.ItemLook;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.reward.Reward;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.Material;

/**
 * One thing the shop sells.
 *
 * @param place where its icon sits in the menu; empty takes the next free slot
 * @param cost in points, at least 1
 * @param icon what the menu shows for it
 * @param rewards all of them are given on a purchase
 * @param ownedPermission whoever has it already can't buy the entry again
 */
public record ShopEntry(String id, Optional<Place> place, int cost, Icon icon, List<Reward> rewards, Optional<String> ownedPermission) {

    public ShopEntry {
        rewards = List.copyOf(rewards);
    }

    /** The icon's name, as MiniMessage text; also what the buyer is told they bought. */
    public String name() {
        return icon.name();
    }

    /** What an entry looks like in the menu. */
    public sealed interface Icon {

        /** MiniMessage text. */
        String name();

        /** How many the icon shows; empty keeps the item's own. */
        OptionalInt amount();

        /** Glow and model, on top of the item. */
        ItemLook look();

        /** A plain item with its own name and extra lines, as MiniMessage text. */
        record Plain(Material material, String name, List<String> lore, OptionalInt amount, ItemLook look) implements Icon {

            public Plain {
                lore = List.copyOf(lore);
            }
        }

        /**
         * An item saved in MiniGami, shown with the name and extra lines it was saved with unless these replace them.
         * Where there is no name override, the buyer is told the saved name.
         */
        record Saved(String saved, Optional<String> nameOverride, Optional<List<String>> loreOverride, OptionalInt amount, ItemLook look)
                implements Icon {

            public Saved {
                loreOverride = loreOverride.map(List::copyOf);
            }

            @Override
            public String name() {
                return nameOverride.orElse(saved);
            }
        }
    }
}
