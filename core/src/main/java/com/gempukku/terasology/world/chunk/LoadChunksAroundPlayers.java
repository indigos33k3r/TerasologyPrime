package com.gempukku.terasology.world.chunk;

import com.badlogic.gdx.math.Vector3;
import com.gempukku.secsy.context.annotation.In;
import com.gempukku.secsy.context.annotation.NetProfiles;
import com.gempukku.secsy.context.annotation.RegisterSystem;
import com.gempukku.secsy.context.system.LifeCycleSystem;
import com.gempukku.secsy.entity.EntityManager;
import com.gempukku.secsy.entity.EntityRef;
import com.gempukku.secsy.entity.dispatch.ReceiveEvent;
import com.gempukku.secsy.entity.event.AfterComponentAdded;
import com.gempukku.secsy.entity.event.BeforeComponentRemoved;
import com.gempukku.terasology.world.component.ClientComponent;
import com.gempukku.terasology.world.component.LocationComponent;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@RegisterSystem(
        profiles = NetProfiles.AUTHORITY)
public class LoadChunksAroundPlayers implements ChunkRelevanceRule, LifeCycleSystem {
    @In
    private ChunkRelevanceRuleRegistry chunkRelevanceRuleRegistry;
    @In
    private EntityManager entityManager;

    private int horizontalChunkDistance = 7;
    private int verticalChunkDistance = 3;

    private Set<EntityRef> connectedClients = new HashSet<>();

    @Override
    public void initialize() {
        chunkRelevanceRuleRegistry.registerChunkRelevanceRule(this);
    }

    @Override
    public Iterable<ChunkLocation> getRelevantChunks() {
        List<ChunkLocation> chunkLocationList = new LinkedList<>();

        for (EntityRef player : connectedClients) {
            if (player.hasComponent(LocationComponent.class)) {
                LocationComponent location = player.getComponent(LocationComponent.class);
                Vector3 chunkLocation = getChunkLocation(location.getX(), location.getY(), location.getZ());
                String worldId = location.getWorldId();

                for (int x = -horizontalChunkDistance; x <= horizontalChunkDistance; x++) {
                    for (int y = -verticalChunkDistance; y <= verticalChunkDistance; y++) {
                        for (int z = -horizontalChunkDistance; z <= horizontalChunkDistance; z++) {
                            chunkLocationList.add(new ChunkLocationImpl(worldId, Math.round(chunkLocation.x + x), Math.round(chunkLocation.y + y), Math.round(chunkLocation.z + z)));
                        }
                    }
                }
            }
        }

        return chunkLocationList;
    }

    @ReceiveEvent
    public void afterClientConnected(AfterComponentAdded event, EntityRef entity, ClientComponent client, LocationComponent location) {
        connectedClients.add(entity);
    }

    @ReceiveEvent
    public void beforeClientDisconnected(BeforeComponentRemoved event, EntityRef entity, ClientComponent client, LocationComponent location) {
        for (EntityRef connectedClient : connectedClients) {
            if (entityManager.isSameEntity(entity, connectedClient)) {
                connectedClients.remove(connectedClient);
                break;
            }
        }
    }

    @Override
    public boolean isChunkRelevant(ChunkLocation chunk) {
        for (EntityRef player : connectedClients) {
            if (player.hasComponent(LocationComponent.class)) {
                LocationComponent location = player.getComponent(LocationComponent.class);
                Vector3 chunkLocation = getChunkLocation(location.getX(), location.getY(), location.getZ());
                String worldId = location.getWorldId();

                if (worldId.equals(chunk.getWorldId())) {
                    int diffX = Math.abs(chunk.getX() - Math.round(chunkLocation.x));
                    int diffY = Math.abs(chunk.getY() - Math.round(chunkLocation.y));
                    int diffZ = Math.abs(chunk.getZ() - Math.round(chunkLocation.z));

                    if (diffX <= horizontalChunkDistance && diffY <= verticalChunkDistance && diffZ <= horizontalChunkDistance)
                        return true;
                }
            }
        }
        return false;
    }

    private Vector3 tempVec = new Vector3();

    private Vector3 getChunkLocation(float x, float y, float z) {
        tempVec.set(
                (float) Math.floor(x / ChunkSize.X),
                (float) Math.floor(y / ChunkSize.Y),
                (float) Math.floor(z / ChunkSize.Z));
        return tempVec;
    }

    private static class ChunkLocationImpl implements ChunkLocation {
        private String worldId;
        private int x;
        private int y;
        private int z;

        public ChunkLocationImpl(String worldId, int x, int y, int z) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String getWorldId() {
            return worldId;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getZ() {
            return z;
        }
    }
}
