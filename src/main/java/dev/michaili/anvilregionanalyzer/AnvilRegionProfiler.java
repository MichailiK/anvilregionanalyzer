package dev.michaili.anvilregionanalyzer;

import dev.michaili.anvilregionanalyzer.mixin.RegionBasedStorageMixins;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.storage.StorageIoWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AnvilRegionProfiler {
    public static final Logger LOGGER = LoggerFactory.getLogger("anvilregionanalyzer");

    private static final Map<ServerWorld, List<ServerPlayerEntity>> worldsProfiling = new HashMap<>();
    private static final ConcurrentMap<ServerWorld, ConcurrentHashMap<Long, ChunkProfilingMetadata>> chunkMetadata = new ConcurrentHashMap<>();

    public static void startProfiling(ServerWorld world) {
        if (worldsProfiling.containsKey(world))
            return;
        worldsProfiling.put(world, new ArrayList<>());
        chunkMetadata.put(world, new ConcurrentHashMap<>());
        var worker = tryGetStorageIoWorker(world);
        ((WorldAware)(Object) worker.storage).setWorld(world);
    }

    public static void stopProfiling(ServerWorld world) {
        if (!worldsProfiling.containsKey(world))
            return;
        for (ServerPlayerEntity player : worldsProfiling.get(world)) {
            if (player.getServerWorld() == world)
                ServerPlayNetworking.send(player, NetworkingConstants.ANVIL_PROFILING_STOP, PacketByteBufs.empty());
        }
        worldsProfiling.remove(world);
        chunkMetadata.remove(world);
        var worker = tryGetStorageIoWorker(world);
        ((WorldAware)(Object) worker.storage).setWorld(null);
    }

    public static boolean toggleProfiling(ServerWorld world) {
        if (worldsProfiling.containsKey(world)) {
            stopProfiling(world);
            return false;
        } else {
            startProfiling(world);
            return true;
        }
    }

    /** Called by the {@link RegionBasedStorageMixins} mixin to make the profiler aware that a chunk was saved */
    public static void onChunkWrite(ChunkPos pos, ServerWorld world, long saveDuration) {
        if (!worldsProfiling.containsKey(world))
            return;
        var lastSaveTime = chunkMetadata.get(world);
        var chunkLong = ChunkPos.toLong(pos.x, pos.z);
        lastSaveTime.put(chunkLong, new ChunkProfilingMetadata(System.nanoTime(), saveDuration));
    }

    public static void init() {
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (!worldsProfiling.containsKey(world))
                return;
            LOGGER.info("Stopping Anvil profiling for {} due to world being unloaded", world.getRegistryKey().getValue().toString());
            stopProfiling(world);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {

            worldsProfiling.forEach((world, playerList) -> {

                var scannedPlayers = new ArrayList<>(playerList.size());
                world.getPlayers().forEach(player -> {

                    // Notify (newly joined) clients that profiling is active if it is active
                    if (!playerList.contains(player) && player.hasPermissionLevel(4)) {
                        playerList.add(player);
                        ServerPlayNetworking.send(player, NetworkingConstants.ANVIL_PROFILING_START, PacketByteBufs.empty());
                    }

                    // Notify (deopped) clients that profiling has stopped if they no longer have permission to use it
                    if (playerList.contains(player) && !player.hasPermissionLevel(4)) {
                        playerList.remove(player);
                        ServerPlayNetworking.send(player, NetworkingConstants.ANVIL_PROFILING_STOP, PacketByteBufs.empty());
                    }

                    // At this point, profiling is enabled, the player has permissions for it, and the player client should be aware of it
                    scannedPlayers.add(player);

                    var playerLoc = player.getChunkPos();
                    var playerRegionLong = ChunkPos.toLong(playerLoc.getRegionX(), playerLoc.getRegionZ());
                    var worker = tryGetStorageIoWorker(world);

                    var regionFile = worker.storage.cachedRegionFiles.get(playerRegionLong);

                    if (regionFile == null)
                        // May happen when player is travelling to freshly generated chunks with auto-saving off.
                        // We'll not send any data to the player and hope that the region will be loaded & cached soon.
                        return;

                    long currentTime = System.nanoTime();

                    // Let's get packing now!
                    var buf = PacketByteBufs.create();
                    buf.writeInt(playerLoc.getRegionX());
                    buf.writeInt(playerLoc.getRegionZ());
                    try {
                        buf.writeLong(regionFile.channel.size());
                    } catch (IOException e) {
                        LOGGER.error("Failed to get size of region location (" + playerLoc.getRegionX() + " " + playerLoc.getRegionZ() + ")", e);
                        return;
                    }

                    for (int x = 0; x < 32; x++) {
                        for (int y = 0; y < 32; y++) {
                            var index = x + (y * 32);

                            var globalChunkPos = new ChunkPos(playerLoc.getRegionX() * 32 + x, playerLoc.getRegionZ() * 32 + y);

                            int sectorData = regionFile.sectorData.get(index);

                            if (sectorData == 0) {
                                // no data available, send a 0 & continue
                                buf.writeInt(0);
                                continue;
                            }

                            // Offset is expected to start at 2, as the first 2 sectors are reserved for the header.
                            int regionOffset = RegionFile.getOffset(sectorData);
                            int regionSize = RegionFile.getSize(sectorData);

                            buf.writeInt(regionOffset);
                            buf.writeInt(regionSize);

                            ByteBuffer chunkHeaderBuffer = ByteBuffer.allocate(5);
                            try {
                                regionFile.channel.read(chunkHeaderBuffer, regionOffset * 4096L);
                            } catch (IOException e) {
                                LOGGER.error("Failed to read chunk header at offset " + regionOffset + " in region location (" + playerLoc.getRegionX() + " " + playerLoc.getRegionZ() + ")", e);
                                return;
                            }
                            chunkHeaderBuffer.flip();
                            int chunkLength = chunkHeaderBuffer.getInt();
                            byte chunkCompressionType = chunkHeaderBuffer.get();

                            buf.writeInt(chunkLength);
                            buf.writeByte(chunkCompressionType);

                            var chunkProfilingMetadata = AnvilRegionProfiler.chunkMetadata.get(world);
                            var chunkMetadataEntry = chunkProfilingMetadata.get(globalChunkPos.toLong());
                            if (chunkMetadataEntry == null) {
                                // Chunk has never been saved here.
                                buf.writeInt(-1);
                                buf.writeLong(-1);
                            } else {
                                // convert nanoseconds to milliseconds
                                buf.writeInt((int) Math.min(Integer.MAX_VALUE, (currentTime - chunkMetadataEntry.lastSaveTime) / 1_000_000L));
                                buf.writeLong(currentTime - chunkMetadataEntry.lastSaveDuration);
                            }
                        }
                    }

                    ServerPlayNetworking.send(player, NetworkingConstants.ANVIL_PROFILING_REGION_STREAM, buf);

                });

                // Remove players that are no longer in the world or server
                playerList.removeIf(player -> !scannedPlayers.contains(player));
            });
        });
    }

    private static StorageIoWorker tryGetStorageIoWorker(ServerWorld world) {
        var worker = world.getChunkManager().threadedAnvilChunkStorage.getWorker();
        if (!(worker instanceof StorageIoWorker))
            throw new RuntimeException("AnvilRegionAnalyzer: Could not get StorageIoWorker from world");

        return (StorageIoWorker) worker;
    }

    // TODO might wanna extend this to not just be last save time, but also last (de-)compression durations?
    private record ChunkProfilingMetadata(long lastSaveTime, long lastSaveDuration) {}
}
