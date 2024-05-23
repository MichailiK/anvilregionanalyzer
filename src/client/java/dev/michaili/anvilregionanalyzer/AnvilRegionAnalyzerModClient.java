package dev.michaili.anvilregionanalyzer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public class AnvilRegionAnalyzerModClient implements ClientModInitializer {

	private static Text anvilRegionPrefix = Text.literal("[Anvil Region Analyzer] ").formatted(Formatting.YELLOW, Formatting.BOLD);

	@Override
	public void onInitializeClient() {
		ProfilingRender.init();

		ClientPlayNetworking.registerGlobalReceiver(NetworkingConstants.ANVIL_PROFILING_START, (client, handler, buf, responseSender) -> {

			addAnvilMessage(Text.literal("Server has indicated that it is profiling Anvil in this dimension."));

			ProfilingRender.shouldRender = true;
		});

		ClientPlayNetworking.registerGlobalReceiver(NetworkingConstants.ANVIL_PROFILING_STOP, (client, handler, buf, responseSender) -> {
			addAnvilMessage(Text.literal("Server has indicated that profiling Anvil in this dimension has stopped."));
			ProfilingRender.shouldRender = false;
			ProfilingRender.lastReceivedData = null;
		});

		ClientPlayNetworking.registerGlobalReceiver(NetworkingConstants.ANVIL_PROFILING_REGION_STREAM, ((client, handler, buf, responseSender) -> {
			ProfilingRender.lastReceivedData = AnvilRegionProfileData.parseFromBuffer(buf);
			//client.execute(() -> addAnvilMessage(Text.literal("Received region data from server.")));
		}));
	}

	private void addAnvilMessage(Text text) {
		MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("[Anvil Region Analyzer] ").formatted(Formatting.YELLOW, Formatting.BOLD)).append(text));
	}
}
