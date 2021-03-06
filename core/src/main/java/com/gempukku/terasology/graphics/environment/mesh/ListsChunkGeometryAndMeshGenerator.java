package com.gempukku.terasology.graphics.environment.mesh;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ShortArray;
import com.gempukku.secsy.context.annotation.In;
import com.gempukku.secsy.context.annotation.RegisterSystem;
import com.gempukku.secsy.context.system.LifeCycleSystem;
import com.gempukku.secsy.entity.io.ComponentData;
import com.gempukku.secsy.entity.io.EntityData;
import com.gempukku.terasology.graphics.TextureAtlasProvider;
import com.gempukku.terasology.graphics.TextureAtlasRegistry;
import com.gempukku.terasology.graphics.component.GeneratedBlockMeshComponent;
import com.gempukku.terasology.graphics.shape.BlockSide;
import com.gempukku.terasology.graphics.shape.ShapeDef;
import com.gempukku.terasology.graphics.shape.ShapePartDef;
import com.gempukku.terasology.graphics.shape.ShapeProvider;
import com.gempukku.terasology.prefab.PrefabManager;
import com.gempukku.terasology.world.CommonBlockManager;
import com.gempukku.terasology.world.chunk.ChunkBlocks;
import com.gempukku.terasology.world.chunk.ChunkBlocksProvider;
import com.gempukku.terasology.world.chunk.ChunkSize;
import com.gempukku.terasology.world.chunk.geometry.BlockGeometryGenerator;
import com.gempukku.terasology.world.chunk.geometry.BlockGeometryGeneratorRegistry;
import com.gempukku.terasology.world.chunk.geometry.ChunkGeometryGenerator;
import com.gempukku.terasology.world.chunk.geometry.ListsChunkGeometry;
import com.gempukku.terasology.world.component.CommonBlockComponent;
import com.gempukku.terasology.world.component.ShapeAndTextureComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterSystem(
        profiles = "generateChunkGeometry", shared = {ChunkGeometryGenerator.class, ChunkMeshGenerator.class,
        BlockGeometryGeneratorRegistry.class})
public class ListsChunkGeometryAndMeshGenerator implements ChunkGeometryGenerator<ListsChunkGeometry>, ChunkMeshGenerator<ListsChunkGeometry>,
        ChunkMeshGeneratorCallback, BlockGeometryGeneratorRegistry, LifeCycleSystem {
    @In
    private ChunkBlocksProvider chunkBlocksProvider;
    @In
    private CommonBlockManager commonBlockManager;
    @In
    private TextureAtlasProvider textureAtlasProvider;
    @In(optional = true)
    private TextureAtlasRegistry textureAtlasRegistry;
    @In
    private ShapeProvider shapeProvider;
    @In
    private PrefabManager prefabManager;

    private ShortArrayThreadLocal shorts = new ShortArrayThreadLocal();
    private FloatArrayThreadLocal floats = new FloatArrayThreadLocal();

    private ShapeDef[] shapesByBlockId;
    private Map<String, String>[] texturesByBlockId;
    private boolean[] opaqueByBlockId;
    private String[] blockMeshGenerators;

    private Map<String, BlockGeometryGenerator> registeredBlockMeshGenerators = new HashMap<>();

    private final int[][] blockSector = new int[][]
            {
                    {-1, -1, -1}, {-1, -1, 0}, {-1, -1, 1},
                    {-1, 0, -1}, {-1, 0, 0}, {-1, 0, 1},
                    {-1, 1, -1}, {-1, 1, 0}, {-1, 1, 1},
                    {0, -1, -1}, {0, -1, 0}, {0, -1, 1},
                    {0, 0, -1}, {0, 0, 0}, {0, 0, 1},
                    {0, 1, -1}, {0, 1, 0}, {0, 1, 1},
                    {1, -1, -1}, {1, -1, 0}, {1, -1, 1},
                    {1, 0, -1}, {1, 0, 0}, {1, 0, 1},
                    {1, 1, -1}, {1, 1, 0}, {1, 1, 1}
            };

    @Override
    public void initialize() {
        if (textureAtlasRegistry != null) {
            Set<String> texturePaths = new HashSet<>();

            for (EntityData prefabData : prefabManager.findPrefabsWithComponents(CommonBlockComponent.class, ShapeAndTextureComponent.class)) {
                for (String partTexture : ((Map<String, String>) prefabData.getComponent(ShapeAndTextureComponent.class).getFields().get("parts")).values()) {
                    texturePaths.add(partTexture);
                }
            }
            textureAtlasRegistry.registerTextures(ChunkMeshGenerator.CHUNK_ATLAS_NAME, texturePaths);
        }
    }

    @Override
    public void registerBlockMeshGenerator(String generatorType, BlockGeometryGenerator generator) {
        registeredBlockMeshGenerators.put(generatorType, generator);
    }

    private void init() {
        if (shapesByBlockId == null) {
            int commonBlockCount = commonBlockManager.getCommonBlockCount();
            shapesByBlockId = new ShapeDef[commonBlockCount];
            texturesByBlockId = new Map[commonBlockCount];
            opaqueByBlockId = new boolean[commonBlockCount];
            blockMeshGenerators = new String[commonBlockCount];
            for (short i = 0; i < commonBlockCount; i++) {
                EntityData commonBlockData = commonBlockManager.getCommonBlockById(i);
                ComponentData shapeAndTextureComponent = commonBlockData.getComponent(ShapeAndTextureComponent.class);
                if (shapeAndTextureComponent != null) {
                    shapesByBlockId[i] = shapeProvider.getShapeById((String) shapeAndTextureComponent.getFields().get("shapeId"));
                    texturesByBlockId[i] = (Map<String, String>) shapeAndTextureComponent.getFields().get("parts");
                    opaqueByBlockId[i] = (Boolean) shapeAndTextureComponent.getFields().get("opaque");
                }
                ComponentData generatedBlockMeshComponent = commonBlockData.getComponent(GeneratedBlockMeshComponent.class);
                if (generatedBlockMeshComponent != null) {
                    blockMeshGenerators[i] = (String) generatedBlockMeshComponent.getFields().get("generatorType");
                }
            }
        }
    }

    @Override
    public boolean canPrepareChunkData(String worldId, int x, int y, int z) {
        for (int[] surroundingChunk : blockSector) {
            if (chunkBlocksProvider.getChunkBlocks(worldId, x + surroundingChunk[0], y + surroundingChunk[1], z + surroundingChunk[2]) == null)
                return false;
        }
        return true;
    }

    @Override
    public ListsChunkGeometry prepareChunkGeometryOffThread(String worldId, int x, int y, int z) {
        init();
        List<Texture> textures = textureAtlasProvider.getTextures(CHUNK_ATLAS_NAME);

        ChunkBlocks[] chunkSector = new ChunkBlocks[blockSector.length];

        for (int i = 0; i < blockSector.length; i++) {
            ChunkBlocks chunkBlocks = chunkBlocksProvider.getChunkBlocks(worldId,
                    x + blockSector[i][0], y + blockSector[i][1], z + blockSector[i][2]);
            if (chunkBlocks == null)
                return null;
            chunkSector[i] = chunkBlocks;
        }

        int chunkX = x * ChunkSize.X;
        int chunkY = y * ChunkSize.Y;
        int chunkZ = z * ChunkSize.Z;

        int textureCount = textures.size();

        int[][] blocksPerTexture = new int[textureCount][];
        float[][] verticesPerTexture = new float[textureCount][];
        short[][] indicesPerTexture = new short[textureCount][];

        for (int i = 0; i < textureCount; i++) {
            Texture texture = textures.get(i);

            IntArray blocks = new IntArray();

            FloatArray vertices = floats.get();
            vertices.clear();

            ShortArray indices = shorts.get();
            indices.clear();

            BlockGeometryGenerator.BlockVertexOutput vertexOutput = new ArrayBlockVertexOutput(blocks, vertices, indices);

            for (int dx = 0; dx < ChunkSize.X; dx++) {
                for (int dy = 0; dy < ChunkSize.Y; dy++) {
                    for (int dz = 0; dz < ChunkSize.Z; dz++) {
                        generateMeshForBlockFromAtlas(vertexOutput, texture, chunkSector,
                                chunkX, chunkY, chunkZ,
                                dx, dy, dz);
                    }
                }
            }
            blocksPerTexture[i] = blocks.toArray();
            verticesPerTexture[i] = vertices.toArray();
            indicesPerTexture[i] = indices.toArray();
        }

        return new ListsChunkGeometry(9, verticesPerTexture, blocksPerTexture, indicesPerTexture);
    }

    @Override
    public Array<MeshPart> generateMeshParts(ListsChunkGeometry chunkGeometry) {
        Array<MeshPart> result = new Array<>();
        int textureCount = chunkGeometry.verticesPerTexture.length;
        for (int i = 0; i < textureCount; i++) {
            float[] vertices = chunkGeometry.verticesPerTexture[i];
            short[] indices = chunkGeometry.indicesPerTexture[i];

            if (indices.length > 0) {
                VertexAttribute customVertexInformation = new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_flag");

                Mesh mesh = new Mesh(true, vertices.length / chunkGeometry.floatsPerVertex, indices.length,
                        VertexAttribute.Position(), VertexAttribute.Normal(), VertexAttribute.TexCoords(0),
                        customVertexInformation);
                mesh.setVertices(vertices);
                mesh.setIndices(indices);

                MeshPart meshPart = new MeshPart("chunk", mesh, 0, indices.length, GL20.GL_TRIANGLES);

                result.add(meshPart);
            } else {
                result.add(null);
            }
        }
        return result;
    }

    private void generateMeshForBlockFromAtlas(BlockGeometryGenerator.BlockVertexOutput vertexOutput, Texture texture, ChunkBlocks[] chunkSector,
                                               int chunkX, int chunkY, int chunkZ,
                                               int x, int y, int z) {
        int blockX = chunkX + x;
        int blockY = chunkY + y;
        int blockZ = chunkZ + z;

        vertexOutput.setBlock(blockX, blockY, blockZ);

        short block = chunkSector[13].getCommonBlockAt(x, y, z);

        if (shapesByBlockId[block] != null && texturesByBlockId[block] != null) {
            Map<String, String> availableTextures = texturesByBlockId[block];

            ShapeDef shape = shapesByBlockId[block];
            for (ShapePartDef shapePart : shape.getShapeParts()) {
                BlockSide blockSide = shapePart.getSide();

                if (blockSide != null) {
                    // We need to check if block next to it is full (covers whole block side)
                    if (isNeighbourBlockCoveringSide(chunkSector, x, y, z, blockSide))
                        continue;
                }

                List<String> textureIds = shapePart.getTextures();

                String textureToUse = findFirstTexture(textureIds, availableTextures);
                TextureRegion textureRegion = textureAtlasProvider.getTexture(ChunkMeshGenerator.CHUNK_ATLAS_NAME, textureToUse);
                if (textureRegion.getTexture() == texture) {
                    int vertexCount = shapePart.getVertices().size();

                    // This array will store indexes of vertices in the resulting Mesh
                    short[] vertexMapping = new short[vertexCount];
                    for (int i = 0; i < vertexCount; i++) {
                        Float[] vertexCoords = shapePart.getVertices().get(i);
                        Float[] normalValues = shapePart.getNormals().get(i);
                        Float[] textureCoords = shapePart.getUvs().get(i);

                        vertexOutput.setPosition(
                                chunkX + x + vertexCoords[0],
                                chunkY + y + vertexCoords[1],
                                chunkZ + z + vertexCoords[2]);
                        vertexOutput.setNormal(
                                normalValues[0],
                                normalValues[1],
                                normalValues[2]);
                        vertexOutput.setTextureCoordinate(
                                textureRegion.getU() + textureCoords[0] * (textureRegion.getU2() - textureRegion.getU()),
                                textureRegion.getV() + textureCoords[1] * (textureRegion.getV2() - textureRegion.getV()));

                        vertexMapping[i] = vertexOutput.finishVertex();

                    }
                    for (short index : shapePart.getIndices()) {
                        vertexOutput.addVertexIndex(vertexMapping[index]);
                    }
                }
            }
        } else if (blockMeshGenerators[block] != null) {
            BlockGeometryGenerator blockGeometryGenerator = registeredBlockMeshGenerators.get(blockMeshGenerators[block]);
            blockGeometryGenerator.generateGeometryForBlockFromAtlas(this, vertexOutput, texture, chunkSector[13],
                    x, y, z);
        }
    }

    @Override
    public boolean isNeighbourBlockCoveringSide(ChunkBlocks[] chunkSector, int x, int y, int z, BlockSide blockSide) {
        int resultX = x + blockSide.getNormalX();
        int resultY = y + blockSide.getNormalY();
        int resultZ = z + blockSide.getNormalZ();

        int chunkPositionInSector = 13;

        if (resultX < 0) {
            chunkPositionInSector -= 9;
            resultX += ChunkSize.X;
        } else if (resultX >= ChunkSize.X) {
            chunkPositionInSector += 9;
            resultX -= ChunkSize.X;
        }
        if (resultY < 0) {
            chunkPositionInSector -= 3;
            resultY += ChunkSize.Y;
        } else if (resultY >= ChunkSize.Y) {
            chunkPositionInSector += 3;
            resultY -= ChunkSize.Y;
        }
        if (resultZ < 0) {
            chunkPositionInSector -= 1;
            resultZ += ChunkSize.Z;
        } else if (resultZ >= ChunkSize.Z) {
            chunkPositionInSector += 1;
            resultZ -= ChunkSize.Z;
        }

        short neighbouringBlock = chunkSector[chunkPositionInSector].getCommonBlockAt(resultX, resultY, resultZ);
        if (shapesByBlockId[neighbouringBlock] != null) {
            if (opaqueByBlockId[neighbouringBlock]) {
                ShapeDef neighbourShapeDef = shapesByBlockId[neighbouringBlock];
                if (neighbourShapeDef.getFullParts().contains(blockSide.getOpposite())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String findFirstTexture(List<String> textureIds, Map<String, String> availableTextures) {
        for (String textureId : textureIds) {
            String texture = availableTextures.get(textureId);
            if (texture != null)
                return texture;
        }

        return null;
    }

    private static class ShortArrayThreadLocal extends ThreadLocal<ShortArray> {
        @Override
        protected ShortArray initialValue() {
            return new ShortArray(1024);
        }
    }

    private static class FloatArrayThreadLocal extends ThreadLocal<FloatArray> {
        @Override
        protected FloatArray initialValue() {
            return new FloatArray(1024);
        }
    }
}
