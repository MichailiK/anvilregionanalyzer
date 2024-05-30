package dev.michaili.anvilregionanalyzer;

public class AnvilRegionProfileData {
    private Networking.AnvilProfilingStream data;
    private long wastedBytesByChunkPadding = 0;
    private int unusedSectors = 0;
    private int savesInLastSecond = 0;

    public Networking.AnvilProfilingStream getData() {
        return data;
    }
    
    public Networking.AnvilProfilingStream.ChunkMetadata getChunkDataAt(int x, int z) {
        return data.chunkMetadata()[x + (z * 32)];
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

    public AnvilRegionProfileData(Networking.AnvilProfilingStream data) {
        this.data = data;

        for (var chunk : data.chunkMetadata()) {
            if (chunk == null) continue;
            // determine how many bytes are wasted due to padding in chunk data
            wastedBytesByChunkPadding += (chunk.regionSize() * 4096L) - chunk.chunkDataSize();
            // determine how many chunks were saved in the last second
            if (chunk.lastSaveTime() != -1 && chunk.lastSaveTime() <= 1000)
                savesInLastSecond++;
        }

        var regionBodySizeInSectors = (int) Math.ceil((data.regionFileSize() / 4096d)) - 2;
        if (regionBodySizeInSectors > 0) {
            // determine unused sectors in the region file
            boolean[] usedSectors = new boolean[regionBodySizeInSectors];
            for (var chunk : data.chunkMetadata()) {
                if (chunk == null) continue;
                for (int i = chunk.regionOffset() - 2; i < (chunk.regionOffset() - 2) + chunk.regionSize(); i++) {
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
}
