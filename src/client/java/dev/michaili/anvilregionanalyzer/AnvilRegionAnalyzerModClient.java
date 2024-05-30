package dev.michaili.anvilregionanalyzer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AnvilRegionAnalyzerModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		ProfilingRender.init();

		ClientPlayNetworking.registerGlobalReceiver(Networking.AnvilProfilingStart.ID, (payload, context) -> {
			addAnvilMessage(Text.literal("Server has indicated that it is profiling Anvil in this dimension."));

			ProfilingRender.shouldRender = true;
		});

		ClientPlayNetworking.registerGlobalReceiver(Networking.AnvilProfilingStop.ID, (payload, context) -> {
			addAnvilMessage(Text.literal("Server has indicated that profiling Anvil in this dimension has stopped."));
			ProfilingRender.shouldRender = false;
			ProfilingRender.lastReceivedData = null;
		});

		ClientPlayNetworking.registerGlobalReceiver(Networking.AnvilProfilingStream.ID, (payload, context) -> {
			ProfilingRender.lastReceivedData = new AnvilRegionProfileData(payload);
		});
	}

	private void addAnvilMessage(Text text) {
		MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.empty().append(Text.literal("[Anvil Region Analyzer] ").formatted(Formatting.YELLOW, Formatting.BOLD)).append(text));
	}
}
