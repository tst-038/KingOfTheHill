package io.github.tst_038.koth.shop;

import io.github.tst_038.koth.ledger.Wallet;
import io.github.tst_038.minigami.api.loot.MiniGamiSavedItems;
import io.github.tst_038.minigami.api.menu.Menu;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;

/** Opens the shop and the shop editor, MiniGami menus both, and redraws the open ones when the shop changes. Server thread only. */
public final class ShopMenus {

    private final Server server;
    private final Supplier<Shop> shop;
    private final Messages messages;
    private final ShopIcons icons;
    private final Wallet wallet;
    private final MiniGamiPlayers players;
    private final Checkout checkout;
    private final ShopEditor editor;
    private final String editPermission;

    public ShopMenus(Server server, Supplier<Shop> shop, Messages messages, MiniGamiSavedItems savedItems, Wallet wallet, MiniGamiPlayers players,
            Checkout checkout, ShopEditor editor, String editPermission, Logger logger) {
        this.server = server;
        this.shop = shop;
        this.messages = messages;
        this.icons = new ShopIcons(messages, savedItems, logger);
        this.wallet = wallet;
        this.players = players;
        this.checkout = checkout;
        this.editor = editor;
        this.editPermission = editPermission;
    }

    public void openShop(Player player) {
        if (shop.get() == Shop.NONE) {
            messages.send(player, "shop.unavailable");
        } else if (players.isInMatch(player.getUniqueId())) {
            messages.send(player, "shop.in-match");
        } else {
            Menu.open(player, new ShopView(player, shop, icons, messages, wallet, checkout));
        }
    }

    /** The editor works on the shop in use, so a shop.yml with problems has to be fixed by hand first. */
    public void openEditor(Player player) {
        if (shop.get() == Shop.NONE) {
            messages.send(player, "shop-edit.not-loaded");
        } else {
            Menu.open(player, new ShopEditorView(player, shop, icons, messages, editor, editPermission));
        }
    }

    /** After a reload: every open shop and editor shows the shop now in use. */
    public void refreshOpen() {
        for (Player player : server.getOnlinePlayers()) {
            Menu.of(player).filter(menu -> menu.content() instanceof ShopView || menu.content() instanceof ShopEditorView).ifPresent(Menu::refresh);
        }
    }
}
