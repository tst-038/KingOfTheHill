package io.github.tst_038.koth.config;

import io.github.tst_038.minigami.api.region.Region;
import java.util.Optional;
import org.bukkit.DyeColor;

/**
 * The hill as set in the config.
 *
 * @param region the blocks a player stands on to take the hill
 * @param captureSeconds how long a player must stand on it alone to take it
 * @param colourBlocks the box whose dyed blocks change colour with each capture, if the file sets one
 * @param neutral the colour they have while nobody holds the hill and KOTH isn't running
 */
public record PointSettings(Region region, int captureSeconds, Optional<Region> colourBlocks, DyeColor neutral) {

    /** The colour box in use: the one the file sets, or else the hill and the layer under it, the floor players stand on. */
    public Region colourBox() {
        return colourBlocks.orElseGet(() -> new Region("colour-blocks", region.world(), region.minX(), region.minY() - 1, region.minZ(),
                region.maxX(), region.maxY(), region.maxZ()));
    }
}
