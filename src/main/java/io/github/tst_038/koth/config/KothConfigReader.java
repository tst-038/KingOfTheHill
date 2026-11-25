package io.github.tst_038.koth.config;

import io.github.tst_038.minigami.api.config.ConfigLocations;
import io.github.tst_038.minigami.api.config.ConfigReader;
import io.github.tst_038.minigami.api.config.InvalidConfigException;
import io.github.tst_038.minigami.api.region.Region;
import io.github.tst_038.koth.hill.Scoring;
import io.github.tst_038.koth.schedule.Plan;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;

/** Reads {@code config.yml} into a {@link KothConfig}. */
public final class KothConfigReader {

    /** The most blocks the hill may cover. */
    static final int MAX_POINT_BLOCKS = 4096;
    /**
     * The most blocks the colour box may cover, such as 128 by 64 by 128. It is scanned in one go on the server thread at
     * each round start and load, so a mistyped corner must not stall the server for seconds.
     */
    static final int MAX_COLOUR_BLOCKS = 1_048_576;
    private static final int MAX_SECONDS = 3600;
    private static final int MAX_SIDEBAR_LINES = 15;
    private static final int DEFAULT_JOIN_LINK_MINUTES = 720;
    /** As long as the longest window, so a link works for all of it. */
    private static final int MAX_JOIN_LINK_MINUTES = 10_080;

    private KothConfigReader() {
    }

    /**
     * Looks up the world, so run it on the server thread.
     *
     * @param root a reader of the whole file; checked here, so it must not have been checked yet
     * @throws InvalidConfigException listing every problem in the file, which is then not usable at all
     */
    public static KothConfig read(ConfigReader root, WorldLookup worlds) throws InvalidConfigException {
        var worldSection = root.section("world");
        var world = ConfigLocations.world(worldSection, "name", name -> worlds.load(name).orElse(null));
        var spawn = worldSection.has("spawn")
                ? Optional.of(ConfigLocations.read(worldSection.section("spawn"), world))
                : Optional.<Location>empty();
        boolean respawnHere = !worldSection.has("respawn-here") || worldSection.bool("respawn-here");
        var leave = worldSection.has("leave")
                ? Optional.of(ConfigLocations.readWithWorld(worldSection.section("leave"), name -> worlds.load(name).orElse(null)))
                : Optional.<Location>empty();
        if (leave.isPresent() && world != null && world.equals(leave.get().getWorld())) {
            worldSection.problem("leave", "is in the KOTH world itself");
        }

        var schedule = root.section("schedule");
        var timetable = TimetableReader.read(schedule);
        var warnings = TimetableReader.warnings(schedule);
        var restrictions = RestrictionReader.read(root.sectionOrEmpty("restrictions"));

        var point = point(root.section("point"), world);
        var scoringSection = root.section("scoring");
        var scoring = new Scoring(scoringSection.integer("points", 1, Integer.MAX_VALUE), scoringSection.integer("every-seconds", 1, MAX_SECONDS));
        var sidebar = root.section("sidebar");
        var lines = new SidebarLines(sidebar.string("title"), sidebar.strings("inactive"), sidebar.strings("waiting"), sidebar.strings("active"));
        // The client shows no more than 15 lines of a sidebar.
        checkLineCount(sidebar, "inactive", lines.inactive());
        checkLineCount(sidebar, "waiting", lines.waiting());
        checkLineCount(sidebar, "active", lines.active());

        var announce = root.sectionOrEmpty("announce");
        var joinLink = Duration.ofMinutes(announce.has("join-link-minutes")
                ? announce.integer("join-link-minutes", 1, MAX_JOIN_LINK_MINUTES) : DEFAULT_JOIN_LINK_MINUTES);

        root.check();
        // check() passed, so nothing above went wrong and the timetable is there.
        return new KothConfig(world, spawn, respawnHere, leave, new Plan(timetable.orElseThrow(), warnings, restrictions), point, scoring, lines, joinLink);
    }

    private static void checkLineCount(ConfigReader sidebar, String key, List<String> texts) {
        if (texts.size() > MAX_SIDEBAR_LINES) {
            sidebar.problem(key, "has more than " + MAX_SIDEBAR_LINES + " lines");
        }
    }

    private static PointSettings point(ConfigReader section, World world) {
        var region = box(section, "region", world, MAX_POINT_BLOCKS);
        int captureSeconds = section.integer("capture-seconds", 1, MAX_SECONDS);
        var colourBlocks = section.has("colour-blocks")
                ? Optional.of(box(section, "colour-blocks", world, MAX_COLOUR_BLOCKS))
                : Optional.<Region>empty();
        var neutral = section.has("neutral-colour") ? section.choice("neutral-colour", DyeColor.class) : DyeColor.WHITE;
        return new PointSettings(region, captureSeconds, colourBlocks, neutral);
    }

    private static Region box(ConfigReader parent, String key, World world, int maxBlocks) {
        var region = Region.read(key, parent.section(key), world);
        // Each side is cut off at the cap, so the product can't overflow however far apart the corners are.
        long blocks = Math.min(region.maxX() - region.minX() + 1L, maxBlocks + 1L)
                * Math.min(region.maxY() - region.minY() + 1L, maxBlocks + 1L)
                * Math.min(region.maxZ() - region.minZ() + 1L, maxBlocks + 1L);
        if (blocks > maxBlocks) {
            parent.problem(key, "covers more than " + maxBlocks + " blocks");
        }
        return region;
    }
}
