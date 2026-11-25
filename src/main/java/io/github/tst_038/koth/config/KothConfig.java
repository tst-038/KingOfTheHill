package io.github.tst_038.koth.config;

import io.github.tst_038.koth.hill.Scoring;
import io.github.tst_038.koth.schedule.Plan;
import java.time.Duration;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Everything {@code config.yml} sets, read and checked.
 *
 * @param world the KOTH world
 * @param spawn where players are put down, if the config says; otherwise the world's spawn
 * @param respawnHere whether players who die in the world come back to the spawn
 * @param leave where players sent out of the world go, in another world, if the config says; otherwise the main world's spawn
 * @param joinLinkLifetime how long the link in an announcement keeps working
 */
public record KothConfig(World world, Optional<Location> spawn, boolean respawnHere, Optional<Location> leave, Plan plan, PointSettings point,
        Scoring scoring, SidebarLines sidebar, Duration joinLinkLifetime) {

    public Location spawnOrDefault() {
        return spawn.orElseGet(world::getSpawnLocation);
    }
}
