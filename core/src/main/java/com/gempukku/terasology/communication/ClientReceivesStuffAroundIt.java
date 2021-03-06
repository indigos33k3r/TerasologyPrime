package com.gempukku.terasology.communication;

import com.badlogic.gdx.Gdx;
import com.gempukku.secsy.context.annotation.In;
import com.gempukku.secsy.context.annotation.NetProfiles;
import com.gempukku.secsy.context.annotation.RegisterSystem;
import com.gempukku.secsy.context.system.LifeCycleSystem;
import com.gempukku.secsy.context.util.PriorityCollection;
import com.gempukku.secsy.entity.EntityManager;
import com.gempukku.secsy.entity.EntityRef;
import com.gempukku.secsy.entity.dispatch.ReceiveEvent;
import com.gempukku.secsy.entity.event.AfterComponentUpdated;
import com.gempukku.secsy.entity.index.EntityIndex;
import com.gempukku.secsy.entity.index.EntityIndexManager;
import com.gempukku.secsy.network.server.ClientConnectedEvent;
import com.gempukku.secsy.network.server.ClientEntityRelevanceRule;
import com.gempukku.secsy.network.server.ClientEntityRelevancyRuleListener;
import com.gempukku.secsy.network.server.ClientManager;
import com.gempukku.terasology.world.WorldBlock;
import com.gempukku.terasology.world.chunk.ChunkBlocks;
import com.gempukku.terasology.world.chunk.ChunkBlocksProvider;
import com.gempukku.terasology.world.chunk.ChunkComponent;
import com.gempukku.terasology.world.chunk.event.AfterChunkLoadedEvent;
import com.gempukku.terasology.world.component.BlockComponent;
import com.gempukku.terasology.world.component.ClientComponent;
import com.gempukku.terasology.world.component.LocationComponent;
import com.gempukku.terasology.world.component.MultiverseComponent;
import com.gempukku.terasology.world.component.WorldComponent;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@RegisterSystem(
        profiles = NetProfiles.AUTHORITY)
public class ClientReceivesStuffAroundIt implements ClientEntityRelevanceRule, LifeCycleSystem {
    @In
    private EntityManager entityManager;
    @In
    private EntityIndexManager entityIndexManager;
    @In
    private ClientManager clientManager;
    @In
    private ChunkBlocksProvider chunkBlocksProvider;

    private PriorityCollection<ClientEntityRelevancyRuleListener> listeners = new PriorityCollection<>();

    private EntityIndex multiverseIndex;
    private EntityIndex worldIndex;
    private EntityIndex chunkIndex;
    private EntityIndex blockIndex;
    private EntityIndex sendToClientAndLocationIndex;
    private EntityIndex clientAndLocationIndex;

    // Client id to collection of ChunkBlocks
    private Multimap<String, ChunkBlocks> chunksClientHas = HashMultimap.create();

    @Override
    public void addClientEntityRelevancyRuleListener(ClientEntityRelevancyRuleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeClientEntityRelevancyRuleListener(ClientEntityRelevancyRuleListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void initialize() {
        clientManager.addClientEntityRelevanceRule(this);
        multiverseIndex = entityIndexManager.addIndexOnComponents(MultiverseComponent.class);
        worldIndex = entityIndexManager.addIndexOnComponents(WorldComponent.class);
        chunkIndex = entityIndexManager.addIndexOnComponents(ChunkComponent.class);
        blockIndex = entityIndexManager.addIndexOnComponents(BlockComponent.class, LocationComponent.class);
        sendToClientAndLocationIndex = entityIndexManager.addIndexOnComponents(SendToClientComponent.class, LocationComponent.class);
        clientAndLocationIndex = entityIndexManager.addIndexOnComponents(ClientComponent.class, LocationComponent.class);
    }

    private WorldBlock worldBlock = new WorldBlock();

    @Override
    public boolean isEntityRelevantForClient(EntityRef clientEntity, EntityRef entity) {
        if (entity.hasComponent(BlockComponent.class)) {
            LocationComponent location = clientEntity.getComponent(LocationComponent.class);
            LocationComponent blockLocation = entity.getComponent(LocationComponent.class);

            // If client is in different world
            if (!location.getWorldId().equals(blockLocation.getWorldId()))
                return false;

            ClientComponent client = clientEntity.getComponent(ClientComponent.class);

            worldBlock.set(location.getX(), location.getY(), location.getZ());
            int playerChunkX = worldBlock.getChunkX();
            int playerChunkY = worldBlock.getChunkY();
            int playerChunkZ = worldBlock.getChunkZ();

            worldBlock.set(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());

            return isWithinPlayerDistance(worldBlock.getChunkX(), worldBlock.getChunkY(), worldBlock.getChunkZ(), playerChunkX, playerChunkY, playerChunkZ, client);
        } else if (entity.hasComponent(ChunkComponent.class)) {
            LocationComponent clientLocation = clientEntity.getComponent(LocationComponent.class);
            worldBlock.set(clientLocation.getX(), clientLocation.getY(), clientLocation.getZ());

            ChunkComponent chunk = entity.getComponent(ChunkComponent.class);
            return isChunkInViewDistance(
                    chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ(),
                    clientLocation.getWorldId(), worldBlock.getChunkX(), worldBlock.getChunkY(), worldBlock.getChunkZ(),
                    clientEntity.getComponent(ClientComponent.class));
        } else if (entity.hasComponent(WorldComponent.class)) {
            WorldComponent world = entity.getComponent(WorldComponent.class);
            LocationComponent location = clientEntity.getComponent(LocationComponent.class);
            return location.getWorldId().equals(world.getWorldId());
        } else if (entity.hasComponent(MultiverseComponent.class)) {
            return true;
        } else if (entity.hasComponent(SendToClientComponent.class) && entity.hasComponent(LocationComponent.class)) {
            return true;
        }
        return false;
    }

    private boolean isChunkInViewDistance(String worldId, int chunkX, int chunkY, int chunkZ,
                                          String playerWorldId, int playerChunkX, int playerChunkY, int playerChunkZ,
                                          ClientComponent client) {
        // If client is in different world
        if (!playerWorldId.equals(worldId))
            return false;

        return isWithinPlayerDistance(chunkX, chunkY, chunkZ, playerChunkX, playerChunkY, playerChunkZ, client);
    }

    private boolean isWithinPlayerDistance(int chunkX, int chunkY, int chunkZ, int playerChunkX, int playerChunkY, int playerChunkZ, ClientComponent client) {
        int clientChunkHorizontalDistance = client.getChunkHorizontalDistance();
        return Math.abs(playerChunkX - chunkX) * Math.abs(playerChunkZ - chunkZ) <= (clientChunkHorizontalDistance * clientChunkHorizontalDistance)
                && Math.abs(playerChunkY - chunkY) <= client.getChunkVerticalDistance();
    }

    @ReceiveEvent
    public void clientConnected(ClientConnectedEvent event, EntityRef clientEntity, ClientComponent clientComponent, LocationComponent location) {
        List<EntityRef> entitiesToUpdate = new LinkedList<>();

        for (EntityRef multiverseEntity : multiverseIndex.getEntities()) {
            entitiesToUpdate.add(multiverseEntity);
        }

        String clientWorldId = location.getWorldId();
        for (EntityRef worldEntity : worldIndex.getEntities()) {
            if (clientWorldId.equals(worldEntity.getComponent(WorldComponent.class).getWorldId()))
                entitiesToUpdate.add(worldEntity);
        }

        List<StoreNewChunk> storeNewChunks = new LinkedList<>();

        worldBlock.set(location.getX(), location.getY(), location.getZ());
        int playerChunkX = worldBlock.getChunkX();
        int playerChunkY = worldBlock.getChunkY();
        int playerChunkZ = worldBlock.getChunkZ();
        for (EntityRef chunkEntity : chunkIndex.getEntities()) {
            ChunkComponent chunk = chunkEntity.getComponent(ChunkComponent.class);
            if (isChunkInViewDistance(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ(),
                    clientWorldId, playerChunkX, playerChunkY, playerChunkZ, clientComponent)) {
                entitiesToUpdate.add(chunkEntity);

                ChunkBlocks chunkBlocks = chunkBlocksProvider.getChunkBlocks(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ());
                short[] blocks = chunkBlocks.getBlocks();
                Gdx.app.debug("ClientReceivesBlocksAroundIt", "Sending chunk to client: " + chunk.getX() + "," + chunk.getY() + "," + chunk.getZ());
                storeNewChunks.add(new StoreNewChunk(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ(), blocks));
                chunksClientHas.put(clientComponent.getClientId(), chunkBlocks);
            }
        }

        for (EntityRef blockEntity : blockIndex.getEntities()) {
            LocationComponent blockLocation = blockEntity.getComponent(LocationComponent.class);
            if (blockLocation.getWorldId().equals(clientWorldId)) {
                worldBlock.set(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());

                if (isWithinPlayerDistance(worldBlock.getChunkX(), worldBlock.getChunkY(), worldBlock.getChunkZ(), playerChunkX, playerChunkY, playerChunkZ, clientComponent))
                    entitiesToUpdate.add(blockEntity);
            }
        }

        for (EntityRef entityRef : sendToClientAndLocationIndex.getEntities()) {
            LocationComponent entityLocation = entityRef.getComponent(LocationComponent.class);
            if (entityLocation.getWorldId().equals(clientWorldId)) {
                worldBlock.set(entityLocation.getX(), entityLocation.getY(), entityLocation.getZ());

                if (isWithinPlayerDistance(worldBlock.getChunkX(), worldBlock.getChunkY(), worldBlock.getChunkZ(), playerChunkX, playerChunkY, playerChunkZ, clientComponent))
                    entitiesToUpdate.add(entityRef);
            }
        }

        if (!entitiesToUpdate.isEmpty()) {
            for (ClientEntityRelevancyRuleListener listener : listeners) {
                listener.entityRelevancyChanged(clientComponent.getClientId(), entitiesToUpdate);
            }
        }

        for (StoreNewChunk storeNewChunk : storeNewChunks) {
            clientEntity.send(storeNewChunk);
        }
    }

    //TODO add code for client disconnecting

    @ReceiveEvent
    public void objectMoved(AfterComponentUpdated event, EntityRef entity, LocationComponent locationComponent) {
        ClientComponent client = entity.getComponent(ClientComponent.class);
        if (client != null) {
            LocationComponent oldLocation = event.getOldComponent(LocationComponent.class);
            LocationComponent newLocation = event.getNewComponent(LocationComponent.class);

            if (oldLocation != null && newLocation != null) {
                if (!oldLocation.getWorldId().equals(newLocation.getWorldId())) {
                    processPlayerMovedBetweenWorlds(entity, client, oldLocation, newLocation);
                } else {
                    processPlayerMovedWithinWorld(entity, client, oldLocation, newLocation);
                }
            }
        }

        if (entity.hasComponent(SendToClientComponent.class)) {
            LocationComponent oldLocation = event.getOldComponent(LocationComponent.class);
            LocationComponent newLocation = event.getNewComponent(LocationComponent.class);

            if (oldLocation != null && newLocation != null) {
                boolean changedWorld = !oldLocation.getWorldId().equals(newLocation.getWorldId());
                if (changedWorld) {
                    for (EntityRef clientEntity : clientAndLocationIndex.getEntities()) {
                        ClientComponent clientComp = clientEntity.getComponent(ClientComponent.class);
                        for (ClientEntityRelevancyRuleListener listener : listeners) {
                            listener.entityRelevancyChanged(clientComp.getClientId(), Arrays.asList(entity));
                        }
                    }
                } else {
                    worldBlock.set(oldLocation.getX(), oldLocation.getY(), oldLocation.getZ());
                    int oldChunkX = worldBlock.getChunkX();
                    int oldChunkY = worldBlock.getChunkY();
                    int oldChunkZ = worldBlock.getChunkZ();

                    worldBlock.set(newLocation.getX(), newLocation.getY(), newLocation.getZ());
                    int newChunkX = worldBlock.getChunkX();
                    int newChunkY = worldBlock.getChunkY();
                    int newChunkZ = worldBlock.getChunkZ();

                    // Object moved from chunk to chunk
                    if (oldChunkX != newChunkX || oldChunkY != newChunkY || oldChunkZ != newChunkZ) {
                        for (EntityRef clientEntity : clientAndLocationIndex.getEntities()) {
                            ClientComponent clientComp = clientEntity.getComponent(ClientComponent.class);
                            for (ClientEntityRelevancyRuleListener listener : listeners) {
                                listener.entityRelevancyChanged(clientComp.getClientId(), Arrays.asList(entity));
                            }
                        }
                    }
                }
            }
        }
    }

    @ReceiveEvent
    public void chunkLoaded(AfterChunkLoadedEvent event, EntityRef worldEntity) {
        String worldId = worldEntity.getComponent(WorldComponent.class).getWorldId();
        int chunkX = event.x;
        int chunkY = event.y;
        int chunkZ = event.z;
        EntityRef chunkEntity = getChunkEntity(worldId, chunkX, chunkY, chunkZ);

        for (EntityRef clientEntity : clientAndLocationIndex.getEntities()) {
            List<EntityRef> changeRelevanceEntities = new LinkedList<>();

            LocationComponent clientLocation = clientEntity.getComponent(LocationComponent.class);
            worldBlock.set(clientLocation.getX(), clientLocation.getY(), clientLocation.getZ());

            ClientComponent client = clientEntity.getComponent(ClientComponent.class);
            if (isChunkInViewDistance(worldId, chunkX, chunkY, chunkZ,
                    clientLocation.getWorldId(), worldBlock.getChunkX(), worldBlock.getChunkY(), worldBlock.getChunkZ(), client)) {
                changeRelevanceEntities.add(chunkEntity);

                for (EntityRef blockEntity : blockIndex.getEntities()) {
                    LocationComponent blockLocation = blockEntity.getComponent(LocationComponent.class);
                    if (blockLocation.getWorldId().equals(worldId)) {
                        worldBlock.set(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());
                        if (worldBlock.getChunkX() == chunkX && worldBlock.getChunkY() == chunkY && worldBlock.getChunkZ() == chunkZ)
                            changeRelevanceEntities.add(blockEntity);
                    }
                }

                ChunkBlocks chunkBlocks = chunkBlocksProvider.getChunkBlocks(worldId, chunkX, chunkY, chunkZ);
                short[] blocks = chunkBlocks.getBlocks();
                Gdx.app.debug("ClientReceivesBlocksAroundIt", "Sending chunk to client: " + chunkX + "," + chunkY + "," + chunkZ);
                clientEntity.send(new StoreNewChunk(worldId, chunkX, chunkY, chunkZ, blocks));
                chunksClientHas.put(client.getClientId(), chunkBlocks);
            }

            if (changeRelevanceEntities.size() > 0) {
                for (ClientEntityRelevancyRuleListener listener : listeners) {
                    listener.entityRelevancyChanged(client.getClientId(), changeRelevanceEntities);
                }
            }
        }
    }

    private EntityRef getChunkEntity(String worldId, int chunkX, int chunkY, int chunkZ) {
        for (EntityRef chunk : chunkIndex.getEntities()) {
            ChunkComponent chunkComp = chunk.getComponent(ChunkComponent.class);
            if (chunkComp.getWorldId().equals(worldId)
                    && chunkComp.getX() == chunkX
                    && chunkComp.getY() == chunkY
                    && chunkComp.getZ() == chunkZ) {
                return chunk;
            }
        }
        return null;
    }

    private void processPlayerMovedWithinWorld(EntityRef clientEntity, ClientComponent client, LocationComponent oldLocation, LocationComponent newLocation) {
        worldBlock.set(oldLocation.getX(), oldLocation.getY(), oldLocation.getZ());
        int oldChunkX = worldBlock.getChunkX();
        int oldChunkY = worldBlock.getChunkY();
        int oldChunkZ = worldBlock.getChunkZ();

        worldBlock.set(newLocation.getX(), newLocation.getY(), newLocation.getZ());
        int newChunkX = worldBlock.getChunkX();
        int newChunkY = worldBlock.getChunkY();
        int newChunkZ = worldBlock.getChunkZ();

        // Player moved from chunk to chunk
        if (oldChunkX != newChunkX || oldChunkY != newChunkY || oldChunkZ != newChunkZ) {
            List<EntityRef> entitiesToUpdate = new LinkedList<>();

            List<StoreNewChunk> storeNewChunks = new LinkedList<>();
            List<RemoveOldChunk> removeOldChunks = new LinkedList<>();

            for (EntityRef chunkEntity : chunkIndex.getEntities()) {
                ChunkComponent chunk = chunkEntity.getComponent(ChunkComponent.class);

                ChunkBlocks chunkBlocks = chunkBlocksProvider.getChunkBlocks(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ());
                if (chunkBlocks != null) {
                    boolean clientHasChunk = doesClientHaveChunk(client.getClientId(), newLocation.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ());

                    boolean clientShouldHaveChunk = isChunkInViewDistance(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ(),
                            newLocation.getWorldId(), newChunkX, newChunkY, newChunkZ, client);
                    if (clientHasChunk != clientShouldHaveChunk) {
                        entitiesToUpdate.add(chunkEntity);

                        appendObjectsInChunk(entitiesToUpdate, chunk, oldLocation.getWorldId());
                        if (clientHasChunk) {
                            if (!chunksClientHas.remove(client.getClientId(), chunkBlocks))
                                throw new RuntimeException("Failed to remove");
                            Gdx.app.debug("ClientReceivesBlocksAroundIt", "Removing chunk from client: " + chunk.getX() + "," + chunk.getY() + "," + chunk.getZ());
                            removeOldChunks.add(new RemoveOldChunk(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ()));
                        } else {
                            short[] blocks = chunkBlocks.getBlocks();
                            Gdx.app.debug("ClientReceivesBlocksAroundIt", "Sending chunk to client: " + chunk.getX() + "," + chunk.getY() + "," + chunk.getZ());
                            storeNewChunks.add(new StoreNewChunk(chunk.getWorldId(), chunk.getX(), chunk.getY(), chunk.getZ(), blocks));
                            chunksClientHas.put(client.getClientId(), chunkBlocks);
                        }
                    }
                }
            }

            if (!entitiesToUpdate.isEmpty()) {
                for (ClientEntityRelevancyRuleListener listener : listeners) {
                    listener.entityRelevancyChanged(client.getClientId(), entitiesToUpdate);
                }
            }

            for (RemoveOldChunk removeOldChunk : removeOldChunks) {
                clientEntity.send(removeOldChunk);
            }
            for (StoreNewChunk storeNewChunk : storeNewChunks) {
                clientEntity.send(storeNewChunk);
            }
        }
    }

    private boolean doesClientHaveChunk(String clientId, String worldId, int x, int y, int z) {
        for (ChunkBlocks chunkBlocks : chunksClientHas.get(clientId)) {
            if (chunkBlocks.worldId.equals(worldId)
                    && chunkBlocks.x == x && chunkBlocks.y == y && chunkBlocks.z == z)
                return true;
        }
        return false;
    }

    private void appendObjectsInChunk(List<EntityRef> entitiesToUpdate, ChunkComponent chunk, String worldId) {
        for (EntityRef blockEntity : blockIndex.getEntities()) {
            LocationComponent blockLocation = blockEntity.getComponent(LocationComponent.class);
            if (blockLocation.getWorldId().equals(worldId)) {
                worldBlock.set(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());
                if (worldBlock.getChunkX() == chunk.getX() && worldBlock.getChunkY() == chunk.getY() && worldBlock.getChunkZ() == chunk.getZ())
                    entitiesToUpdate.add(blockEntity);
            }
        }

        for (EntityRef entityRef : sendToClientAndLocationIndex.getEntities()) {
            LocationComponent entityLocation = entityRef.getComponent(LocationComponent.class);
            if (entityLocation.getWorldId().equals(worldId)) {
                worldBlock.set(entityLocation.getX(), entityLocation.getY(), entityLocation.getZ());
                if (worldBlock.getChunkX() == chunk.getX() && worldBlock.getChunkY() == chunk.getY() && worldBlock.getChunkZ() == chunk.getZ())
                    entitiesToUpdate.add(entityRef);
            }
        }
    }

    private void processPlayerMovedBetweenWorlds(EntityRef clientEntity, ClientComponent client, LocationComponent oldLocation, LocationComponent newLocation) {
        // TODO implement it correctly
    }
}
