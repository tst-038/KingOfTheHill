package io.github.tst_038.koth.sidebar;

import io.github.tst_038.koth.text.Numbers;
import io.github.tst_038.minigami.api.board.BoardClaim;
import io.github.tst_038.minigami.api.board.BoardPriority;
import io.github.tst_038.minigami.api.board.MiniGamiBoards;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.koth.config.KothConfig;
import io.github.tst_038.koth.ledger.Balances;
import io.github.tst_038.koth.message.StatusResolver;
import io.github.tst_038.koth.status.KothStatus;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The sidebar of the players in the KOTH world: that KOTH isn't running and when it next starts, that it waits for
 * players, or who holds the hill, for how long, how long is left and the player's own points. Each player has a board
 * of their own, shown through a claim on MiniGami's boards, which is released when the player leaves the world or the
 * plugin stops, so they get back whatever board they should see. Players MiniGami holds in a match keep the match's
 * board. The points come from the {@link Balances} cache, never from storage. Server thread only, driven once a second
 * by {@link #update}.
 */
public final class KothSidebar {

    private static final String OBJECTIVE = "koth";

    private final Plugin plugin;
    private final Server server;
    private final MiniGamiBoards boards;
    private final Messages messages;
    private final MiniGamiPlayers players;
    private final Balances balances;
    private final Clock clock;
    private final Map<UUID, Board> shown = new HashMap<>();

    public KothSidebar(Plugin plugin, MiniGamiBoards boards, Messages messages, MiniGamiPlayers players, Balances balances, Clock clock) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.boards = boards;
        this.messages = messages;
        this.players = players;
        this.balances = balances;
        this.clock = clock;
    }

    /** Refreshes the lines, and gives a board to those who should have one and takes it from those who shouldn't. */
    public void update(Optional<KothConfig> config, KothStatus status) {
        if (config.isEmpty()) {
            shutdown();
            return;
        }
        var lines = config.get().sidebar();
        var resolver = TagResolver.resolver(StatusResolver.of(messages, status, LocalDateTime.now(clock)),
                status.waiting().map(wait -> StatusResolver.of(wait.refusal())).orElse(TagResolver.empty()));
        var texts = status.running().isPresent() ? lines.active() : status.waiting().isPresent() ? lines.waiting() : lines.inactive();
        var world = config.get().world();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && !players.isInMatch(player.getUniqueId())) {
                var own = TagResolver.resolver(resolver, balance(player.getUniqueId()));
                var board = give(player);
                board.draw(messages.parse(lines.title(), own), texts.stream().map(text -> messages.parse(text, own)).toList());
            } else {
                release(player.getUniqueId());
            }
        }
        // MiniGami dropped the claims of those who quit.
        shown.keySet().removeIf(id -> server.getPlayer(id) == null);
    }

    /** Releases every claim, so everyone gets back the board they should see. */
    public void shutdown() {
        shown.values().forEach(board -> board.claim.release());
        shown.clear();
    }

    private TagResolver balance(UUID player) {
        var points = balances.of(player);
        return points.isPresent()
                ? Placeholder.unparsed("balance", Numbers.format(points.getAsLong()))
                : Placeholder.component("balance", messages.render("placeholder.balance-loading"));
    }

    /** The player's board, claiming their scoreboard for it, again if the claim was dropped. */
    private Board give(Player player) {
        var board = shown.computeIfAbsent(player.getUniqueId(), id -> new Board(server.getScoreboardManager().getNewScoreboard()));
        if (board.claim == null || !board.claim.isActive()) {
            board.claim = boards.claim(plugin, player, board.scoreboard, BoardPriority.PLUGIN);
        }
        return board;
    }

    private void release(UUID id) {
        var board = shown.remove(id);
        if (board != null) {
            board.claim.release();
        }
    }

    private static final class Board {

        private final Scoreboard scoreboard;
        private BoardClaim claim;
        private int lineCount = -1;

        private Board(Scoreboard scoreboard) {
            this.scoreboard = scoreboard;
        }

        private void draw(Component title, List<Component> lines) {
            var objective = scoreboard.getObjective(OBJECTIVE);
            if (objective == null || lines.size() != lineCount) {
                if (objective != null) {
                    objective.unregister();
                }
                objective = scoreboard.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.numberFormat(NumberFormat.blank());
                lineCount = lines.size();
            }
            objective.displayName(title);
            // The entries are never shown, only their custom names.
            for (int i = 0; i < lines.size(); i++) {
                var score = objective.getScore("line-" + i);
                score.setScore(lines.size() - i);
                score.customName(lines.get(i));
            }
        }
    }
}
