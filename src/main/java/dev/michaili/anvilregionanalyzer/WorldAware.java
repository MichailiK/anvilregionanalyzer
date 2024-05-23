package dev.michaili.anvilregionanalyzer;

import dev.michaili.anvilregionanalyzer.mixin.RegionBasedStorageMixins;
import net.minecraft.server.world.ServerWorld;

/**
 * Interface that's only implemented by the {@link RegionBasedStorageMixins} mixin.
 * Exposes a world getter & setter, so the RegionBasedStorage can be made aware of what world it is operating on.
 * It's required internally, for the profiler to attribute chunk writes to the correct world.
 */
public interface WorldAware {
    ServerWorld getWorld();
    void setWorld(ServerWorld world);
}
