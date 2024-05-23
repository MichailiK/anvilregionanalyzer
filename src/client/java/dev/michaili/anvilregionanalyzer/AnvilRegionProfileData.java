package dev.michaili.anvilregionanalyzer;

import net.minecraft.network.PacketByteBuf;

public class AnvilRegionProfileData {
    private int regionX;
    private int regionZ;
    private long regionFileSize;
    private ChunkMetadata[] chunkData;
    private long wastedBytesByChunkPadding = 0;
    private int unusedSectors = 0;
    private int savesInLastSecond = 0;

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    public long getRegionFileSize() {
        return regionFileSize;
    }

    public ChunkMetadata[] getChunkData() {
        return chunkData;
    }

    public ChunkMetadata getChunkDataAt(int x, int z) {
        return chunkData[x + (z * 32)];
    }

    public int getUnusedSectors() {
        return unusedSectors;
    }

    public long getBytesWastedByChunkPadding() {
        return wastedBytesByChunkPadding;
    }

    public int getSavesInLastSecond() {
        return savesInLastSecond;
    }

    public AnvilRegionProfileData(int regionX, int regionZ, long regionFileSize, ChunkMetadata[] chunkData) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.regionFileSize = regionFileSize;
        this.chunkData = chunkData;

        for (ChunkMetadata chunk : chunkData) {
            if (chunk == null) continue;
            // determine how many bytes are wasted due to padding in chunk data
            wastedBytesByChunkPadding += (chunk.getRegionSize() * 4096L) - chunk.chunkDataSize;
            // determine how many chunks were saved in the last second
            if (chunk.getLastSaveTime() != -1 && chunk.getLastSaveTime() <= 1000)
                savesInLastSecond++;
        }

        var regionBodySizeInSectors = (int) Math.ceil((regionFileSize / 4096d)) - 2;
        if (regionBodySizeInSectors > 0) {
            // determine unused sectors in the region file
            boolean[] usedSectors = new boolean[regionBodySizeInSectors];
            for (ChunkMetadata chunk : chunkData) {
                if (chunk == null) continue;
                for (int i = chunk.getRegionOffset() - 2; i < (chunk.getRegionOffset() - 2) + chunk.getRegionSize(); i++) {
                    if (i >= regionBodySizeInSectors)
                        return; // TODO should be a warning
                    usedSectors[i] = true;
                }
            }

            for (boolean usedSector : usedSectors) {
                if (!usedSector) this.unusedSectors++;
            }
        }
    }

    public static AnvilRegionProfileData parseFromBuffer(PacketByteBuf buf) {
        int regionX = buf.readInt();
        int regionZ = buf.readInt();
        long regionFileSize = buf.readLong();

        var chunkData = new AnvilRegionProfileData.ChunkMetadata[1024];

        for (int i = 0; i < 1024; i++) {
            int regionOffset = buf.readInt();
            if (regionOffset == 0) continue;

            int regionChunkSize = buf.readInt();

            int chunkLength = buf.readInt();
            byte compressionType = buf.readByte();

            int lastSaveTime = buf.readInt();
            long lastSaveDuration = buf.readLong();

            chunkData[i] = new AnvilRegionProfileData.ChunkMetadata(regionChunkSize, regionOffset, chunkLength, compressionType, lastSaveTime, lastSaveDuration);
        }

        return new AnvilRegionProfileData(regionX, regionZ, regionFileSize, chunkData);
    }


    public static class ChunkMetadata {
        private int regionSize;
        private int regionOffset;
        private long chunkDataSize;
        private byte compressionType;
        private int lastSaveTime;
        private long lastSaveDuration;

        public ChunkMetadata(int regionSize, int regionOffset, long chunkDataSize, byte compressionType, int lastSaveTime, long lastSaveDuration) {
            this.regionSize = regionSize;
            this.regionOffset = regionOffset;
            this.chunkDataSize = chunkDataSize;
            this.compressionType = compressionType;
            this.lastSaveTime = lastSaveTime;
            this.lastSaveDuration = lastSaveDuration;
        }

        public int getRegionSize() {
            return regionSize;
        }

        public int getRegionOffset() {
            return regionOffset;
        }

        public long getChunkDataSize() {
            return chunkDataSize;
        }

        public byte getCompressionType() {
            return compressionType;
        }

        public int getLastSaveTime() {
            return lastSaveTime;
        }

        public long getLastSaveDuration() {
            return lastSaveDuration;
        }
    }
}
