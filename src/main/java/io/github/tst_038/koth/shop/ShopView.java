package io.github.tst_038.koth.shop;

import io.github.tst_038.koth.ledger.Wallet;
import io.github.tst_038.minigami.api.menu.MenuContent;
import io.github.tst_038.minigami.api.menu.MenuItem;
import io.github.tst_038.minigami.api.message.Messages;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@code /koth shop}: the entries, a left click buying one, and the viewer's own head showing their points in the middle
 * of the bar. A click counts against the entry the player saw, even if the shop has been reloaded since. Server thread only.
 */
final class ShopView implements MenuContent {

    private final Player viewer;
    private final Supplier<Shop> shop;
    private final ShopIcons icons;
    private final Messages messages;
    private final Wallet wallet;
    private final Checkout checkout;

    ShopView(Player viewer, Supplier<Shop> shop, ShopIcons icons, Messages messages, Wallet wallet, Checkout checkout) {
        this.viewer = viewer;
        this.shop = shop;
        this.icons = icons;
        this.messages = messages;
        this.wallet = wallet;
        this.checkout = checkout;
    }

    @Override
    public Component title() {
        return messages.parse(shop.get().title());
    }

    @Override
    public List<MenuItem> items() {
        return shop.get().entries().stream().map(entry -> new MenuItem(icons.entry(entry, viewer, List.of()), entry.place(), click -> {
            if (click.type().isLeftClick()) {
                checkout.buy(click.player(), entry).thenRun(click.menu()::refresh);
            }
        })).toList();
    }

    @Override
    public Optional<MenuItem> centre() {
        var id = viewer.getUniqueId();
        var points = wallet.balances().of(id);
        if (points.isEmpty()) {
            // Not read yet, as just after joining; ask again.
            wallet.load(id);
        }
        var name = points.isPresent()
                ? messages.render("shop.balance", Placeholder.unparsed("points", String.valueOf(points.getAsLong())))
                : messages.render("shop.balance-loading");
        return Optional.of(MenuItem.of(icons.head(viewer, name)));
    }

    @Override
    public ItemStack previous(int page, int pages) {
        return icons.arrow("menu.previous", page - 1, pages);
    }

    @Override
    public ItemStack next(int page, int pages) {
        return icons.arrow("menu.next", page + 1, pages);
    }

    @Override
    public ItemStack filler() {
        return icons.filler();
    }
}
