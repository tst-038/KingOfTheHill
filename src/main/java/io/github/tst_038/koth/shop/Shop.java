package io.github.tst_038.koth.shop;

import java.util.List;

/**
 * What the {@code /koth shop} menu sells.
 *
 * @param title as MiniMessage text
 * @param newCost what an entry added in the shop editor costs
 * @param costStep how much a shift-click in the shop editor raises or lowers a cost
 */
public record Shop(String title, List<ShopEntry> entries, int newCost, int costStep) {

    public static final int NEW_COST = 100;
    public static final int COST_STEP = 10;

    /** No shop: before one has been read, or when its file has never been usable. */
    public static final Shop NONE = new Shop("", List.of(), NEW_COST, COST_STEP);

    public Shop {
        entries = List.copyOf(entries);
    }
}
