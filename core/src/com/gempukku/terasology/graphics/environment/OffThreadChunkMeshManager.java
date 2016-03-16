package com.gempukku.terasology.graphics.environment;

import com.gempukku.secsy.context.annotation.In;
import com.gempukku.secsy.context.annotation.RegisterSystem;
import com.gempukku.secsy.context.system.LifeCycleSystem;
import com.gempukku.secsy.entity.EntityManager;
import com.gempukku.secsy.entity.EntityRef;
import com.gempukku.secsy.entity.dispatch.ReceiveEvent;
import com.gempukku.secsy.entity.game.GameLoop;
import com.gempukku.secsy.entity.game.GameLoopListener;
import com.gempukku.terasology.component.TerasologyComponentManager;
import com.gempukku.terasology.graphics.TextureAtlasProvider;
import com.gempukku.terasology.graphics.environment.event.AfterChunkMeshCreated;
import com.gempukku.terasology.graphics.environment.event.BeforeChunkMeshRemoved;
import com.gempukku.terasology.graphics.shape.ShapeProvider;
import com.gempukku.terasology.world.CommonBlockManager;
import com.gempukku.terasology.world.chunk.ChunkBlocksProvider;
import com.gempukku.terasology.world.chunk.event.AfterChunkLoadedEvent;
import com.gempukku.terasology.world.chunk.event.BeforeChunkUnloadedEvent;
import com.gempukku.terasology.world.component.WorldComponent;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@RegisterSystem(
        profiles = "generateChunkMeshes",
        shared = ChunkMeshManager.class)
public class OffThreadChunkMeshManager implements ChunkMeshManager, LifeCycleSystem, GameLoopListener {
    @In
    private ChunkBlocksProvider chunkBlocksProvider;
    @In
    private CommonBlockManager commonBlockManager;
    @In
    private TextureAtlasProvider textureAtlasProvider;
    @In
    private TerasologyComponentManager terasologyComponentManager;
    @In
    private ShapeProvider shapeProvider;
    @In
    private GameLoop gameLoop;
    @In
    private EntityManager entityManager;
    @In
    private ChunkMeshGenerationOrder chunkMeshGenerationOrder;
    @In
    private ChunkMeshGenerator chunkMeshGenerator;

    private final int offlineThreadCount = 3;

    private Multimap<String, ChunkMesh> chunkMeshesInWorld = HashMultimap.create();
    private OfflineProcessingThread[] offlineProcessingThread;

    private List<ChunkMesh> notReadyChunks = new LinkedList<>();

    @Override
    public void initialize() {
        gameLoop.addGameLoopListener(this);

        offlineProcessingThread = new OfflineProcessingThread[offlineThreadCount];
        for (int i = 0; i < offlineThreadCount; i++) {
            offlineProcessingThread[i] = new OfflineProcessingThread();
            Thread thr = new Thread(offlineProcessingThread[i]);
            thr.setName("Chunk-mesh-generation-" + i);
            thr.start();
        }
    }

    @Override
    public ChunkMesh getChunkMesh(String worldId, int x, int y, int z) {
        for (ChunkMesh chunkMesh : chunkMeshesInWorld.get(worldId)) {
            if (chunkMesh.x == x && chunkMesh.y == y && chunkMesh.z == z)
                return chunkMesh;
        }
        return null;
    }

    @Override
    public void update(long delta) {
        Iterator<ChunkMesh> notReadyMeshes = notReadyChunks.iterator();
        while (notReadyMeshes.hasNext()) {
            ChunkMesh notReadyMesh = notReadyMeshes.next();
            if (chunkMeshGenerator.canPrepareChunkData(notReadyMesh.worldId,
                    notReadyMesh.x, notReadyMesh.y, notReadyMesh.z)) {
                notReadyMeshes.remove();
                synchronized (chunkMeshesInWorld) {
                    chunkMeshesInWorld.put(notReadyMesh.worldId, notReadyMesh);
                }
            }
        }

        for (ChunkMesh renderableChunk : chunkMeshesInWorld.values()) {
            boolean updated = renderableChunk.updateModelIfNeeded(chunkMeshGenerator);
            if (updated) {
                EntityRef worldEntity = findWorldEntity(renderableChunk.worldId);
                worldEntity.send(new AfterChunkMeshCreated(
                        renderableChunk.worldId, renderableChunk.x, renderableChunk.y, renderableChunk.z));
            }
        }
    }

    private EntityRef findWorldEntity(String worldId) {
        for (EntityRef worldEntity : entityManager.getEntitiesWithComponents(WorldComponent.class)) {
            if (worldId.equals(worldEntity.getComponent(WorldComponent.class).getWorldId()))
                return worldEntity;
        }
        return null;
    }

    @ReceiveEvent
    public void chunkLoaded(AfterChunkLoadedEvent event, EntityRef worldEntity, WorldComponent worldComponent) {
        String worldId = worldComponent.getWorldId();
        int x = event.x;
        int y = event.y;
        int z = event.z;

        ChunkMesh chunkMesh = new ChunkMesh(worldId, x, y, z);
        notReadyChunks.add(chunkMesh);
    }

    @ReceiveEvent
    public void chunkUnloaded(BeforeChunkUnloadedEvent event, EntityRef worldEntity, WorldComponent worldComponent) {
        String worldId = worldComponent.getWorldId();
        int x = event.x;
        int y = event.y;
        int z = event.z;

        Iterator<ChunkMesh> notReadyMeshes = notReadyChunks.iterator();
        while (notReadyMeshes.hasNext()) {
            ChunkMesh notReadyMesh = notReadyMeshes.next();
            if (notReadyMesh.worldId.equals(worldId)
                    && notReadyMesh.x == x && notReadyMesh.y == y && notReadyMesh.z == z)
                notReadyMeshes.remove();
        }

        synchronized (chunkMeshesInWorld) {
            Collection<ChunkMesh> chunkMeshes = chunkMeshesInWorld.get(worldId);
            ChunkMesh chunk = null;
            for (ChunkMesh chunkMesh : chunkMeshes) {
                if (chunkMesh.x == x && chunkMesh.y == y && chunkMesh.z == z) {
                    chunk = chunkMesh;
                    break;
                }
            }
            if (chunk != null) {
                if (chunk.getMeshParts() != null) {
                    worldEntity.send(new BeforeChunkMeshRemoved(worldId, x, y, z));
                }

                chunkMeshes.remove(chunk);
                chunk.dispose();
            }
        }
    }

    private class OfflineProcessingThread implements Runnable {
        public void run() {
            while (true) {
                ChunkMesh chunkToProcess = getChunkToProcess();
                if (chunkToProcess != null) {
                    chunkToProcess.processOffLine(chunkMeshGenerator, textureAtlasProvider.getTextures());
                } else {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException exp) {
                        // Ignore
                    }
                }
            }
        }

        private ChunkMesh getChunkToProcess() {
            synchronized (chunkMeshesInWorld) {
                return chunkMeshGenerationOrder.getChunkMeshToProcess(chunkMeshesInWorld.values());
            }
        }
    }
}
