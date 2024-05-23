package dev.michaili.anvilregionanalyzer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class ProfilingRender {

    private static final int HUD_REGION_PIXEL_WIDTH = 3;
    // font size
    private static final int HUD_REGION_PIXEL_HEIGHT = 10;

    private static final int HUD_PADDING = 10;

    // Width in sectors
    private static final int HUD_REGION_MAP_WIDTH = 80;

    private static final int RENDER_HEIGHT = 64;
    private static final int SAVE_FLASH_DURATION = 2000;

    private static final Logger LOGGER = LoggerFactory.getLogger("anvilregionanalyzer");


    public static AnvilRegionProfileData lastReceivedData = null;
    public static boolean shouldRender = false;

    private static boolean hasJoinedServer = false;


    public static void onJoinWorld() {
        if (!hasJoinedServer)
            return;

        // Reset all our state, as that's only relevant for the previous world.
        shouldRender = false;
        lastReceivedData = null;
    }

    public static void init() {

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // As the server may send the anvil_profiling_start packet before the client actually joins the world,
            // we have to avoid the joinWorld mixin from setting shouldRender to false
            hasJoinedServer = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            shouldRender = false;
            hasJoinedServer = false;
            lastReceivedData = null;
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!shouldRender)
                return;
            // Don't render with debug menu open
            if (MinecraftClient.getInstance().getDebugHud().shouldShowDebugHud())
                return;
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

            int windowWidth = drawContext.getScaledWindowWidth();
            int windowHeight = drawContext.getScaledWindowHeight();


            assert MinecraftClient.getInstance().player != null;
            var pos = MinecraftClient.getInstance().player.getChunkPos();
            drawContext.drawText(textRenderer, "Chunk Pos: (" + pos.getRegionRelativeX() + ", " + pos.getRegionRelativeZ() + ") of r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca", HUD_PADDING, HUD_PADDING, 0xFFFFFF, true);

            if (lastReceivedData == null || lastReceivedData.getRegionX() != pos.getRegionX() || lastReceivedData.getRegionZ() != pos.getRegionZ()) {
                drawContext.drawText(textRenderer, "Waiting for data...", HUD_PADDING, HUD_PADDING + 10, 0xFFFFFF, true);
                return;
            }

            drawContext.drawText(textRenderer, "Region Size: " + humanReadableByteCountBin(lastReceivedData.getRegionFileSize()) + " / " + (int) Math.ceil((double) lastReceivedData.getRegionFileSize() / 4096L) + " sectors", HUD_PADDING, HUD_PADDING + 10, 0xFFFFFF, true);
            drawContext.drawText(textRenderer, "Chunk saves/s: " + lastReceivedData.getSavesInLastSecond() + " chunks/s", HUD_PADDING, HUD_PADDING + 20, 0xFFFFFF, true);

            drawContext.drawText(textRenderer, "Wasted space: " + humanReadableByteCountBin(lastReceivedData.getBytesWastedByChunkPadding() + (lastReceivedData.getUnusedSectors() * 4096L)), HUD_PADDING, HUD_PADDING + 40, 0xFFFFFF, true);
            drawContext.drawText(textRenderer, "Unused sectors: " + lastReceivedData.getUnusedSectors() + " (" + humanReadableByteCountBin(lastReceivedData.getUnusedSectors() * 4096L) + ')', HUD_PADDING, HUD_PADDING + 50, 0xFFFFFF, true);
            drawContext.drawText(textRenderer, "Chunk padding: " + humanReadableByteCountBin(lastReceivedData.getBytesWastedByChunkPadding()), HUD_PADDING, HUD_PADDING + 60, 0xFFFFFF, true);




            int mapHeight = (int) Math.ceil((double) (lastReceivedData.getRegionFileSize() - 4096) / 4096 / HUD_REGION_MAP_WIDTH);

            int mapWidthPixels = HUD_REGION_MAP_WIDTH * HUD_REGION_PIXEL_WIDTH;
            int mapHeightPixels = HUD_REGION_PIXEL_HEIGHT * mapHeight;

            drawContext.fill(windowWidth - mapWidthPixels - HUD_PADDING, windowHeight - mapHeightPixels - HUD_PADDING, windowWidth - HUD_PADDING, windowHeight - HUD_PADDING, 0xdd333333);

            for (int i = 0; i < mapHeight; i++) {
                String text = i * ((HUD_REGION_MAP_WIDTH * 4096) / 1024) + " KiB";
                int textWidth = textRenderer.getWidth(text);

                drawContext.drawText(textRenderer, text, windowWidth - mapWidthPixels - HUD_PADDING - textWidth, windowHeight - mapHeightPixels - HUD_PADDING + (i * HUD_REGION_PIXEL_HEIGHT), 0xFFFFFF, true);
            }

            var chunkData = lastReceivedData.getChunkData();

            for (int i = 0; i < chunkData.length; i++) {
                var chunkMetadata = chunkData[i];
                if (chunkMetadata == null)
                    continue;

                var isCurrentChunkPos = i == pos.getRegionRelativeX() + (pos.getRegionRelativeZ() * 32);

                for (int region = chunkMetadata.getRegionOffset() - 2; region < (chunkMetadata.getRegionOffset() - 2) + chunkMetadata.getRegionSize(); region++) {
                    int x = region % HUD_REGION_MAP_WIDTH;
                    int y = region / HUD_REGION_MAP_WIDTH;

                    drawContext.fill(
                            windowWidth - mapWidthPixels - HUD_PADDING + (x * HUD_REGION_PIXEL_WIDTH),
                            windowHeight - mapHeightPixels - HUD_PADDING + (y * HUD_REGION_PIXEL_HEIGHT),
                            windowWidth - mapWidthPixels - HUD_PADDING + (x * HUD_REGION_PIXEL_WIDTH) + HUD_REGION_PIXEL_WIDTH,
                            windowHeight - mapHeightPixels - HUD_PADDING  + (y * HUD_REGION_PIXEL_HEIGHT) + HUD_REGION_PIXEL_HEIGHT,
                            isCurrentChunkPos ? 0x8800ff00 : 0x880000ff
                    );

                    // draw orange square if chunk has been saved recently. Fade out over 2 seconds
                    drawContext.fill(
                            windowWidth - mapWidthPixels - HUD_PADDING + (x * HUD_REGION_PIXEL_WIDTH),
                            windowHeight - mapHeightPixels - HUD_PADDING + (y * HUD_REGION_PIXEL_HEIGHT),
                            windowWidth - mapWidthPixels - HUD_PADDING + (x * HUD_REGION_PIXEL_WIDTH) + HUD_REGION_PIXEL_WIDTH,
                            windowHeight - mapHeightPixels - HUD_PADDING  + (y * HUD_REGION_PIXEL_HEIGHT) + HUD_REGION_PIXEL_HEIGHT,
                            // use the last save time as the opacity
                            0x00ff8000 | ((chunkMetadata.getLastSaveTime() == -1 ? 0 : Math.max(0, 255 - (int) Math.ceil((float) chunkMetadata.getLastSaveTime() / SAVE_FLASH_DURATION * 255))) << 24)
                    );
                }
            }
        });

        WorldRenderEvents.LAST.register(context -> {
            if (!shouldRender)
                return;

            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);

            assert MinecraftClient.getInstance().player != null;
            var playerPos = MinecraftClient.getInstance().player.getChunkPos();

            drawRegionPillars(context, playerPos.getRegionX(), playerPos.getRegionZ());

            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    ChunkPos pos = new ChunkPos((playerPos.getRegionX() * 32) + x, (playerPos.getRegionZ() * 32) + z);
                    AnvilRegionProfileData.ChunkMetadata metadata = null;
                    if (lastReceivedData != null && lastReceivedData.getRegionX() == playerPos.getRegionX() && lastReceivedData.getRegionZ() == playerPos.getRegionZ())
                        metadata = lastReceivedData.getChunkDataAt(x, z);
                    renderChunkInfo(context, pos, metadata);
                }
            }

            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        });
    }

    private static void renderChunkInfo(WorldRenderContext context, ChunkPos pos, @Nullable AnvilRegionProfileData.ChunkMetadata metadata) {
        var camera = context.camera();
        MatrixStack matrixStack = new MatrixStack();
        //Vec3d targetPosition = new Vec3d(0, camera.getPos().y - 32, 0);
        {
            var opacity = 0.1f;

            if (metadata != null && metadata.getLastSaveTime() != -1) {
                // metadata.getLastSaveTime is the amount of milliseconds passed since last save. Turn the opacity to 1, then fade out for 2 seconds
                opacity = Math.max(0.1f, 1f - ((float) metadata.getLastSaveTime() / SAVE_FLASH_DURATION));
            }

            Vec3d targetPosition = new Vec3d(pos.x * 16, RENDER_HEIGHT, pos.z * 16);
            Vec3d transformedPosition = targetPosition.subtract(camera.getPos());


            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
            matrixStack.translate(transformedPosition.x, transformedPosition.y, transformedPosition.z);

            Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buffer.vertex(positionMatrix, 0, 0, 0).color(1f, .5f, 0f, opacity).next();
            buffer.vertex(positionMatrix, 16, 0, 0).color(1f, .5f, 0f, opacity).next();
            buffer.vertex(positionMatrix, 16, 0, 16).color(1f, .5f, 0f, opacity).next();
            buffer.vertex(positionMatrix, 0, 0, 16).color(1f, .5f, 0f, opacity).next();


            //RenderSystem.setShader(GameRenderer::getPositionColorTexProgram);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            //RenderSystem.setShaderTexture(0, new Identifier("anvilregionanalyzer", "icon.png"));

            //RenderSystem.setShaderColor(1f, 1f, 1f, 0.3f);

            tessellator.draw();
        }

        if (metadata != null) {

            matrixStack.push();

            matrixStack.translate(8, 0.01, 8);
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            matrixStack.scale(-0.1f, -0.1f, 0.1f);

            //RenderSystem.setShader(GameRenderer::getRenderTypeTextProgram);

            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            // convert Bytes into KB, allowing for 3 decimal places
            String chunkDataSize = humanReadableByteCountBin(metadata.getChunkDataSize());
            String sectorsActualSize = humanReadableByteCountBin(metadata.getRegionSize() * 4096L);
            String wastedSpace = humanReadableByteCountBin((metadata.getRegionSize() * 4096L) - metadata.getChunkDataSize());

            var textRenderer = MinecraftClient.getInstance().textRenderer;
            var chunkPosTextX = - textRenderer.getWidth(chunkDataSize) / 2;
            var sizeTextX = - textRenderer.getWidth(metadata.getRegionSize() + " sectors (" + sectorsActualSize + ")") / 2;
            var wastedTextX = - textRenderer.getWidth(wastedSpace + " wasted") / 2;

            textRenderer.draw(chunkDataSize, chunkPosTextX, -15, 0xFFFFFFFF, false, matrixStack.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x0, 0);
            textRenderer.draw(metadata.getRegionSize() + " sectors (" + sectorsActualSize + ")", sizeTextX, -5, 0xFFFFFFFF, false, matrixStack.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x0, 0);
            textRenderer.draw(wastedSpace + " wasted", wastedTextX, 15, 0xFFFFFFFF, false, matrixStack.peek().getPositionMatrix(), context.consumers(), TextRenderer.TextLayerType.SEE_THROUGH, 0x0, 0);

            matrixStack.pop();
        }

        {
            matrixStack.push();

            // draw debug line as a square
            Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(positionMatrix, 0, 0, 0).color(0f, 0f, 1f, 1f).next();
            buffer.vertex(positionMatrix, 16, 0, 0).color(0f, 0f, 1f, 1f).next();

            tessellator.draw();

            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(positionMatrix, 16, 0, 0).color(0f, 0f, 1f, 1f).next();
            buffer.vertex(positionMatrix, 16, 0, 16).color(0f, 0f, 1f, 1f).next();

            tessellator.draw();

            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(positionMatrix, 16, 0, 16).color(0f, 0f, 1f, 1f).next();
            buffer.vertex(positionMatrix, 0, 0, 16).color(0f, 0f, 1f, 1f).next();

            tessellator.draw();

            buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buffer.vertex(positionMatrix, 0, 0, 16).color(0f, 0f, 1f, 1f).next();
            buffer.vertex(positionMatrix, 0, 0, 0).color(0f, 0f, 1f, 1f).next();

            tessellator.draw();
            matrixStack.pop();
        }
    }

    private static void drawRegionPillars(WorldRenderContext context, int regionX, int regionZ) {
        int tRegionX = regionX * 32 * 16;
        int tRegionZ = regionZ * 32 * 16;
        drawRegionPillarLine(context, tRegionX, tRegionZ);
        drawRegionPillarLine(context, tRegionX + 32 * 16, tRegionZ);
        drawRegionPillarLine(context, tRegionX, tRegionZ + 32 * 16);
        drawRegionPillarLine(context, tRegionX + 32 * 16, tRegionZ + 32 * 16);
    }

    private static void drawRegionPillarLine(WorldRenderContext context, int x, int z) {
        var camera = context.camera();
        MatrixStack matrixStack = new MatrixStack();

        Vec3d targetPosition = new Vec3d(x, RENDER_HEIGHT, z);
        Vec3d transformedPosition = targetPosition.subtract(camera.getPos());


        matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        matrixStack.translate(transformedPosition.x, transformedPosition.y, transformedPosition.z);


        Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        buffer.vertex(positionMatrix, 0, 0, 0).color(1f, 0f, 0f, 1f).next();
        buffer.vertex(positionMatrix, 0, context.world().getTopY(), 0).color(1f, 0f, 0f, 1f).next();

        tessellator.draw();
    }

    // https://stackoverflow.com/a/3758880
    public static String humanReadableByteCountBin(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }
}
