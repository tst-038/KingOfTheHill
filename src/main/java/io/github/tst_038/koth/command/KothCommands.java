package io.github.tst_038.koth.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.tst_038.minigami.api.menu.PageLayout;
import io.github.tst_038.minigami.api.menu.Place;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.koth.ledger.Wallet;
import io.github.tst_038.koth.message.StatusResolver;
import io.github.tst_038.koth.round.Joining;
import io.github.tst_038.koth.round.KothRuntime;
import io.github.tst_038.koth.setup.KothSetup;
import io.github.tst_038.koth.setup.KothSetup.Box;
import io.github.tst_038.koth.shop.Shop;
import io.github.tst_038.koth.shop.ShopEditor;
import io.github.tst_038.koth.shop.ShopMenus;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /koth [shop|balance|join]} for players, and {@code /kotha reload|start [minutes]|stop|status|points give|take|show <player> [amount]}
 * and {@code /kotha setup point|colours set|select|show}, {@code /kotha setup spawn} and
 * {@code /kotha shop [add|command|cost|slot|remove|list]} for staff, the shop editor without a subcommand. Server thread only.
 */
public final class KothCommands {

    public static final String ADMIN = "koth.admin";

    private enum Points {
        SHOW("admin.points-shown"), GIVE("admin.points-given"), TAKE("admin.points-taken");

        private final String message;

        Points(String message) {
            this.message = message;
        }
    }

    private final Server server;
    private final Messages messages;
    private final KothRuntime runtime;
    private final Joining joining;
    private final Wallet wallet;
    private final Supplier<Shop> shop;
    private final Consumer<Audience> reload;
    private final KothSetup setup;
    private final ShopEditor shopEditor;
    private final ShopMenus shopMenus;
    private final Clock clock;
    private final Executor serverThread;
    private final Logger logger;

    public KothCommands(Server server, Messages messages, KothRuntime runtime, Joining joining, Wallet wallet, Supplier<Shop> shop,
            Consumer<Audience> reload, KothSetup setup, ShopEditor shopEditor, ShopMenus shopMenus, Clock clock, Executor serverThread, Logger logger) {
        this.server = server;
        this.messages = messages;
        this.runtime = runtime;
        this.joining = joining;
        this.wallet = wallet;
        this.shop = shop;
        this.reload = reload;
        this.setup = setup;
        this.shopEditor = shopEditor;
        this.shopMenus = shopMenus;
        this.clock = clock;
        this.serverThread = serverThread;
        this.logger = logger;
    }

    public LiteralCommandNode<CommandSourceStack> playerRoot() {
        return Commands.literal("koth")
                .requires(source -> source.getSender().hasPermission(Joining.PLAY))
                .executes(bySender(this::info))
                .then(Commands.literal("shop").requires(KothCommands::isPlayer).executes(byPlayer(shopMenus::openShop)))
                .then(Commands.literal("balance").requires(KothCommands::isPlayer).executes(byPlayer(this::balance)))
                .then(Commands.literal("join").requires(KothCommands::isPlayer).executes(byPlayer(joining::join)))
                .build();
    }

    public LiteralCommandNode<CommandSourceStack> adminRoot() {
        return Commands.literal("kotha")
                .requires(source -> source.getSender().hasPermission(ADMIN))
                .then(Commands.literal("reload").executes(bySender(reload::accept)))
                .then(Commands.literal("start")
                        .executes(bySender(sender -> start(sender, Optional.empty())))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 10080))
                                .executes(context -> {
                                    start(context.getSource().getSender(), Optional.of(Duration.ofMinutes(IntegerArgumentType.getInteger(context, "minutes"))));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("stop").executes(bySender(this::stop)))
                .then(Commands.literal("status").executes(bySender(this::adminStatus)))
                .then(Commands.literal("points")
                        .then(Commands.literal("show").then(target().executes(context -> points(context, Points.SHOW, 0))))
                        .then(Commands.literal("give").then(target().then(amount().executes(context -> points(context, Points.GIVE, amountOf(context))))))
                        .then(Commands.literal("take").then(target().then(amount().executes(context -> points(context, Points.TAKE, amountOf(context)))))))
                .then(Commands.literal("setup").requires(KothCommands::isPlayer)
                        .then(box("point", Box.POINT))
                        .then(box("colours", Box.COLOURS))
                        .then(Commands.literal("spawn").executes(byPlayer(setup::spawn))))
                .then(Commands.literal("shop")
                        .executes(context -> {
                            // The editor is a menu; the console gets the list instead.
                            if (context.getSource().getExecutor() instanceof Player player) {
                                shopMenus.openEditor(player);
                            } else {
                                shopEditor.list(context.getSource().getSender());
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("add").requires(KothCommands::isPlayer).then(newId().then(cost().executes(context -> {
                            shopEditor.add((Player) context.getSource().getExecutor(), idOf(context), costOf(context));
                            return Command.SINGLE_SUCCESS;
                        }))))
                        .then(Commands.literal("command").requires(KothCommands::isPlayer).then(newId().then(cost()
                                .then(Commands.argument("command", StringArgumentType.greedyString()).executes(context -> {
                                    shopEditor.command((Player) context.getSource().getExecutor(), idOf(context), costOf(context),
                                            StringArgumentType.getString(context, "command"));
                                    return Command.SINGLE_SUCCESS;
                                })))))
                        .then(Commands.literal("cost").then(entryId().then(cost().executes(context -> {
                            shopEditor.cost(context.getSource().getSender(), idOf(context), costOf(context));
                            return Command.SINGLE_SUCCESS;
                        }))))
                        .then(Commands.literal("slot").then(entryId().then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .then(Commands.argument("slot", IntegerArgumentType.integer(0, PageLayout.CONTENT_SLOTS - 1)).executes(context -> {
                                    shopEditor.place(context.getSource().getSender(), idOf(context),
                                            new Place(IntegerArgumentType.getInteger(context, "page") - 1, IntegerArgumentType.getInteger(context, "slot")));
                                    return Command.SINGLE_SUCCESS;
                                })))))
                        .then(Commands.literal("remove").then(entryId().executes(context -> {
                            shopEditor.remove(context.getSource().getSender(), idOf(context));
                            return Command.SINGLE_SUCCESS;
                        })))
                        .then(Commands.literal("list").executes(bySender(shopEditor::list))))
                .build();
    }

    // Players.

    private void info(CommandSender sender) {
        if (runtime.config().isEmpty()) {
            messages.send(sender, "status.not-configured");
        } else {
            var status = runtime.status();
            var resolver = StatusResolver.of(messages, status, LocalDateTime.now(clock));
            if (status.waiting().isPresent()) {
                messages.send(sender, "status.waiting", resolver, Placeholder.component("reason", StatusResolver.reason(messages, status.waiting().get().refusal())));
            } else {
                messages.send(sender, status.running().isPresent() ? "status.active" : "status.inactive", resolver);
            }
        }
    }

    private void balance(Player player) {
        var id = player.getUniqueId();
        var cached = wallet.balances().of(id);
        if (cached.isPresent()) {
            messages.send(player, "balance.own", Placeholder.unparsed("points", String.valueOf(cached.getAsLong())));
        } else {
            // Not read yet, as just after joining; ask again.
            wallet.load(id);
            messages.send(player, "balance.loading");
        }
    }

    // Staff.

    private void start(CommandSender sender, Optional<Duration> length) {
        if (runtime.config().isEmpty()) {
            messages.send(sender, "status.not-configured");
        } else {
            messages.send(sender, runtime.forceStart(length) ? "admin.started" : "admin.already-running");
        }
    }

    private void stop(CommandSender sender) {
        if (runtime.config().isEmpty()) {
            messages.send(sender, "status.not-configured");
        } else {
            messages.send(sender, runtime.forceStop() ? "admin.stopped" : "admin.not-running");
        }
    }

    private void adminStatus(CommandSender sender) {
        var status = runtime.status();
        var config = runtime.config();
        var taker = status.taker().map(server::getPlayer).map(Player::getName);
        messages.send(sender, "admin.status", StatusResolver.of(messages, status, LocalDateTime.now(clock)),
                Placeholder.component("state", messages.render(status.running().isPresent() ? "admin.state-running" : status.waiting().isPresent() ? "admin.state-waiting" : "admin.state-idle")),
                Placeholder.component("taker", taker.isPresent() ? Component.text(taker.get()) : messages.render("placeholder.no-holder")),
                Placeholder.unparsed("progress", String.valueOf(status.progress())),
                Placeholder.unparsed("capture", String.valueOf(config.map(found -> found.point().captureSeconds()).orElse(0))),
                Placeholder.unparsed("online", String.valueOf(server.getOnlinePlayers().size())),
                Placeholder.component("config", messages.render(config.isPresent() ? "admin.loaded" : "admin.not-loaded")),
                Placeholder.component("shop", messages.render(shop.get() != Shop.NONE ? "admin.loaded" : "admin.not-loaded")));
    }

    private int points(CommandContext<CommandSourceStack> context, Points what, long amount) {
        var sender = context.getSource().getSender();
        var name = StringArgumentType.getString(context, "player");
        // From the user cache only, so it never blocks.
        Player online = server.getPlayerExact(name);
        OfflinePlayer target = online != null ? online : server.getOfflinePlayerIfCached(name);
        if (target == null) {
            messages.send(sender, "admin.unknown-player", Placeholder.unparsed("player", name));
            return Command.SINGLE_SUCCESS;
        }
        var id = target.getUniqueId();
        CompletableFuture<Long> result = switch (what) {
            case SHOW -> wallet.balance(id);
            case GIVE -> wallet.give(id, amount);
            case TAKE -> wallet.take(id, amount);
        };
        result.whenCompleteAsync((points, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not " + what.name().toLowerCase(Locale.ROOT) + " the points of " + name, failure);
                messages.send(sender, "admin.storage-failed");
            } else {
                messages.send(sender, what.message, Placeholder.unparsed("player", target.getName() != null ? target.getName() : name),
                        Placeholder.unparsed("amount", String.valueOf(amount)), Placeholder.unparsed("points", String.valueOf(points)));
            }
        }, serverThread);
        return Command.SINGLE_SUCCESS;
    }

    // Nodes.

    private LiteralArgumentBuilder<CommandSourceStack> box(String name, Box box) {
        return Commands.literal(name)
                .then(Commands.literal("set").executes(byPlayer(player -> setup.set(player, box))))
                .then(Commands.literal("select").executes(byPlayer(player -> setup.select(player, box))))
                .then(Commands.literal("show").executes(byPlayer(player -> setup.show(player, box))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> target() {
        return Commands.argument("player", StringArgumentType.word()).suggests((context, builder) -> {
            server.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> newId() {
        return Commands.argument("id", StringArgumentType.word());
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> entryId() {
        return newId().suggests((context, builder) -> {
            shopEditor.ids().stream().filter(id -> id.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
            return builder.buildFuture();
        });
    }

    private static String idOf(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "id");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> cost() {
        return Commands.argument("cost", IntegerArgumentType.integer(1));
    }

    private static int costOf(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "cost");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Long> amount() {
        return Commands.argument("amount", LongArgumentType.longArg(1, Integer.MAX_VALUE));
    }

    private static long amountOf(CommandContext<CommandSourceStack> context) {
        return LongArgumentType.getLong(context, "amount");
    }

    private static boolean isPlayer(CommandSourceStack source) {
        return source.getExecutor() instanceof Player;
    }

    private static Command<CommandSourceStack> bySender(Consumer<CommandSender> action) {
        return context -> {
            action.accept(context.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        };
    }

    /** Only for nodes that require a player. */
    private static Command<CommandSourceStack> byPlayer(Consumer<Player> action) {
        return context -> {
            action.accept((Player) context.getSource().getExecutor());
            return Command.SINGLE_SUCCESS;
        };
    }
}
