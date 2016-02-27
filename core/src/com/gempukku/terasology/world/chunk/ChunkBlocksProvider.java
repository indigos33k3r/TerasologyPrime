package com.gempukku.terasology.world.chunk;

public interface ChunkBlocksProvider {
    String getCommonBlockAt(String worldId, int x, int y, int z);
    boolean isChunkLoaded(String worldId, int x, int y, int z);
}
