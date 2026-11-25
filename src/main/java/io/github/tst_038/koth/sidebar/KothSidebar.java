package io.github.tst_038.koth.sidebar;

import io.github.tst_038.minigami.api.board.BoardClaim;
import io.github.tst_038.minigami.api.board.BoardPriority;
import io.github.tst_038.minigami.api.board.MiniGamiBoards;
import io.github.tst_038.minigami.api.message.Messages;
import io.github.tst_038.minigami.api.presence.MiniGamiPlayers;
import io.github.tst_038.koth.config.KothConfig;
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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

/**
 * The sidebar of the players in the KOTH world: that KOTH isn't running and when it next starts, that it waits for
 * players, or who holds the hill and how long is left. One board is shared by everyone, shown through a claim on
 * MiniGami's boards, which is released when the player leaves the world or the plugin stops, so they get back whatever
 * board they should see. Players MiniGami holds in a match keep the match's board. Server thread only, driven once a
 * second by {@link #update}.
 */
public final class KothSidebar {

    private static final String OBJECTIVE = "koth";

    private final Plugin plugin;
    private final Server server;
    private final MiniGamiBoards boards;
    private final Messages messages;
    private final MiniGamiPlayers players;
    private final Clock clock;
    private final Map<UUID, BoardClaim> claims = new HashMap<>();
    private Scoreboard board;
    private int lineCount = -1;

    public KothSidebar(Plugin plugin, MiniGamiBoards boards, Messages messages, MiniGamiPlayers players, Clock clock) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.boards = boards;
        this.messages = messages;
        this.players = players;
        this.clock = clock;
    }

    /** Refreshes the lines, and gives the board to those who should have it and takes it from those who shouldn't. */
    public void update(Optional<KothConfig> config, KothStatus status) {
        if (config.isEmpty()) {
            shutdown();
            return;
        }
        var lines = config.get().sidebar();
        var resolver = TagResolver.resolver(StatusResolver.of(messages, status, LocalDateTime.now(clock)),
                status.waiting().map(wait -> StatusResolver.of(wait.refusal())).orElse(TagResolver.empty()));
        var texts = status.running().isPresent() ? lines.active() : status.waiting().isPresent() ? lines.waiting() : lines.inactive();
        draw(messages.parse(lines.title(), resolver), texts.stream().map(text -> messages.parse(text, resolver)).toList());
        var world = config.get().world();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getWorld().equals(world) && !players.isInMatch(player.getUniqueId())) {
                give(player);
            } else {
                release(player.getUniqueId());
            }
        }
        // MiniGami dropped the claims of those who quit.
        claims.keySet().removeIf(id -> server.getPlayer(id) == null);
    }

    /** Releases every claim, so everyone gets back the board they should see. */
    public void shutdown() {
        claims.values().forEach(BoardClaim::release);
        claims.clear();
    }

    private void draw(Component title, List<Component> lines) {
        if (board == null) {
            board = server.getScoreboardManager().getNewScoreboard();
        }
        var objective = board.getObjective(OBJECTIVE);
        if (objective == null || lines.size() != lineCount) {
            if (objective != null) {
                objective.unregister();
            }
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
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

    /** Claims the player's scoreboard, again if the claim was dropped. */
    private void give(Player player) {
        var claim = claims.get(player.getUniqueId());
        if (claim == null || !claim.isActive()) {
            claims.put(player.getUniqueId(), boards.claim(plugin, player, board, BoardPriority.PLUGIN));
        }
    }

    private void release(UUID id) {
        var claim = claims.remove(id);
        if (claim != null) {
            claim.release();
        }
    }
}
