package dev.michaili.anvilregionanalyzer.mixin.client;

import dev.michaili.anvilregionanalyzer.ProfilingRender;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("anvilregionanalyzer");

    @Inject(at = @At("HEAD"), method = "joinWorld")
    private void run(CallbackInfo info) {
        // LOGGER.info("World change detected");
        ProfilingRender.onJoinWorld();
    }
}
