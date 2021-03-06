package com.gempukku.terasology.graphics.environment.event;

import com.gempukku.secsy.entity.event.Event;

public class BeforeChunkGeometryRemoved extends Event {
    public final String worldId;
    public final int x;
    public final int y;
    public final int z;

    public BeforeChunkGeometryRemoved(String worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
