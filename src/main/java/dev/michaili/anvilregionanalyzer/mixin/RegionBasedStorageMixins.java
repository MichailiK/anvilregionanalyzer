package dev.michaili.anvilregionanalyzer.mixin;

import dev.michaili.anvilregionanalyzer.AnvilRegionProfiler;
import dev.michaili.anvilregionanalyzer.WorldAware;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(RegionBasedStorage.class)
public class RegionBasedStorageMixins implements WorldAware {

    private ServerWorld world;

    // Thread-safe map, where key is chunk and value is timestamp of when it began writing.
    private ConcurrentHashMap<ChunkPos, Long> writingChunks = new ConcurrentHashMap<>();


    @Inject(at = @At("HEAD"), method = "write", locals = LocalCapture.CAPTURE_FAILSOFT)
    private void writeChunk(ChunkPos pos, NbtCompound nbt, CallbackInfo ci) {
        writingChunks.put(pos, System.nanoTime());
    }

    @Inject(at = @At("RETURN"), method = "write", locals = LocalCapture.CAPTURE_FAILSOFT)
    private void writeChunkReturn(ChunkPos pos, NbtCompound nbt, CallbackInfo ci) {
        if (!writingChunks.containsKey(pos)) {
            return;
        }

        long time = System.nanoTime() - writingChunks.get(pos);
        AnvilRegionProfiler.onChunkWrite(pos, world, time);
        writingChunks.remove(pos);
    }

    @Override
    public ServerWorld getWorld() {
        return world;
    }

    @Override
    public void setWorld(ServerWorld world) {
        this.world = world;
    }
}
