package dev.michaili.anvilregionanalyzer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class Networking {
    public static final String NAMESPACE = "anvilregionanalyzer";

    public static final Identifier ANVIL_PROFILING_REGION_STREAM = new Identifier(NAMESPACE, "anvil_profiling_region_stream");

    public static void init() {
        PayloadTypeRegistry.playS2C().register(AnvilProfilingStart.ID, AnvilProfilingStart.CODEC);
        PayloadTypeRegistry.playS2C().register(AnvilProfilingStop.ID, AnvilProfilingStop.CODEC);
        PayloadTypeRegistry.playS2C().register(AnvilProfilingStream.ID, AnvilProfilingStream.CODEC);
    }

    public record AnvilProfilingStart() implements CustomPayload {
        public static final CustomPayload.Id<AnvilProfilingStart> ID = new CustomPayload.Id<>(new Identifier(NAMESPACE, "anvil_profiling_start"));
        public static final PacketCodec<PacketByteBuf, AnvilProfilingStart> CODEC = PacketCodec.unit(new AnvilProfilingStart());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record AnvilProfilingStop() implements CustomPayload {
        public static final CustomPayload.Id<AnvilProfilingStop> ID = new CustomPayload.Id<>(new Identifier(NAMESPACE, "anvil_profiling_stop"));
        public static final PacketCodec<PacketByteBuf, AnvilProfilingStop> CODEC = PacketCodec.unit(new AnvilProfilingStop());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }


    public record AnvilProfilingStream(
            int regionX,
            int regionZ,
            long regionFileSize,
            ChunkMetadata[] chunkMetadata
    ) implements CustomPayload {
        public static final CustomPayload.Id<AnvilProfilingStream> ID = new CustomPayload.Id<>(new Identifier(NAMESPACE, "anvil_profiling_region_stream"));
        public static final PacketCodec<PacketByteBuf, AnvilProfilingStream> CODEC = PacketCodec.of(AnvilProfilingStream::write, AnvilProfilingStream::read);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }

        public void write(PacketByteBuf buf) {
            buf.writeInt(regionX);
            buf.writeInt(regionZ);
            buf.writeLong(regionFileSize);

            int written = 0;
            for (ChunkMetadata metadata : chunkMetadata) {
                written++;
                if (metadata == null) {
                    buf.writeInt(0);
                    continue;
                }
                metadata.write(buf);
            }
            if (written != 1024)
                throw new IllegalStateException("Expected exactly 1024 chunks, but wrote " + written);

        }

        public static AnvilProfilingStream read(PacketByteBuf buf) {
            int regionX = buf.readInt();
            int regionZ = buf.readInt();
            long regionFileSize = buf.readLong();

            ChunkMetadata[] chunkMetadata = new ChunkMetadata[1024];
            for (int i = 0; i < 1024; i++) {
                int regionOffset = buf.readInt();
                if (regionOffset == 0) continue;
                int regionSize = buf.readInt();
                long chunkDataSize = buf.readLong();
                byte compressionType = buf.readByte();
                int lastSaveTime = buf.readInt();
                long lastSaveDuration = buf.readLong();
                chunkMetadata[i] = new ChunkMetadata(regionOffset, regionSize, chunkDataSize, compressionType, lastSaveTime, lastSaveDuration);
            }

            return new AnvilProfilingStream(regionX, regionZ, regionFileSize, chunkMetadata);
        }


        public record ChunkMetadata(
                int regionOffset,
                int regionSize,
                long chunkDataSize,
                byte compressionType,
                // -1 if never saved
                int lastSaveTime,
                // -1 if never saved
                long lastSaveDuration
        ) {

            public void write(PacketByteBuf buf) {
                buf.writeInt(regionOffset);
                buf.writeInt(regionSize);
                buf.writeLong(chunkDataSize);
                buf.writeByte(compressionType);
                buf.writeInt(lastSaveTime);
                buf.writeLong(lastSaveDuration);
            }
        }

    }


}
