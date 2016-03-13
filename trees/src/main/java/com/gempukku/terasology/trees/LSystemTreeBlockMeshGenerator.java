package com.gempukku.terasology.trees;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;
import com.gempukku.secsy.context.annotation.In;
import com.gempukku.secsy.context.annotation.RegisterSystem;
import com.gempukku.secsy.context.system.LifeCycleSystem;
import com.gempukku.secsy.entity.EntityRef;
import com.gempukku.terasology.graphics.TextureAtlasProvider;
import com.gempukku.terasology.graphics.TextureAtlasRegistry;
import com.gempukku.terasology.graphics.environment.BlockMeshGenerator;
import com.gempukku.terasology.graphics.environment.BlockMeshGeneratorRegistry;
import com.gempukku.terasology.graphics.environment.ChunkMeshGeneratorCallback;
import com.gempukku.terasology.graphics.shape.ShapeDef;
import com.gempukku.terasology.procedural.FastRandom;
import com.gempukku.terasology.procedural.PDist;
import com.gempukku.terasology.world.WorldStorage;
import com.gempukku.terasology.world.chunk.ChunkBlocks;
import com.gempukku.terasology.world.chunk.ChunkSize;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@RegisterSystem(
        profiles = "generateChunkMeshes")
public class LSystemTreeBlockMeshGenerator implements BlockMeshGenerator, LifeCycleSystem {
    @In
    private BlockMeshGeneratorRegistry blockMeshGeneratorRegistry;
    @In
    private TextureAtlasRegistry textureAtlasRegistry;
    @In
    private TextureAtlasProvider textureAtlasProvider;
    @In
    private WorldStorage worldStorage;

    private TextureRegion oakBarkTexture;
    private TextureRegion oakLeafTexture;
    private ShapeDef cubeShape;

    @Override
    public void initialize() {
        textureAtlasRegistry.registerTextures(
                Arrays.asList(
                        "blockTiles/plant/Tree/OakBark.png",
                        "blockTiles/plant/leaf/GreenLeaf.png"));
        blockMeshGeneratorRegistry.registerBlockMeshGenerator("trees:tree", this);
    }

    @Override
    public void generateMeshForBlockFromAtlas(ChunkMeshGeneratorCallback callback,
                                              FloatArray vertices, ShortArray indices, Texture texture,
                                              ChunkBlocks chunkBlocks, int xInChunk, int yInChunk, int zInChunk) {
        init();

        WorldStorage.EntityRefAndCommonBlockId entityAndBlockId = worldStorage.getBlockEntityAndBlockIdAt(chunkBlocks.worldId,
                chunkBlocks.x * ChunkSize.X + xInChunk,
                chunkBlocks.y * ChunkSize.Y + yInChunk,
                chunkBlocks.z * ChunkSize.Z + zInChunk);

        TreeDefinition treeDefinition = createTreeDefinition(entityAndBlockId.entityRef);

        if (texture == oakBarkTexture.getTexture()) {
            generateTrunk(treeDefinition, vertices, indices,
                    chunkBlocks.x * ChunkSize.X + xInChunk,
                    chunkBlocks.y * ChunkSize.Y + yInChunk,
                    chunkBlocks.z * ChunkSize.Z + zInChunk);
        }
        if (texture == oakLeafTexture.getTexture()) {
            generateLeaves(treeDefinition, vertices, indices,
                    chunkBlocks.x * ChunkSize.X + xInChunk,
                    chunkBlocks.y * ChunkSize.Y + yInChunk,
                    chunkBlocks.z * ChunkSize.Z + zInChunk);
        }
    }

    private TreeDefinition createTreeDefinition(EntityRef entity) {
        int generation = 6;
        int seed = 0;
        FastRandom rnd = new FastRandom();
        PDist newSegmentLength = new PDist(0.8f, 0.2f, PDist.Type.normal);
        PDist newSegmentRadius = new PDist(0.02f, 0.005f, PDist.Type.normal);

        int maxBranchesPerSegment = 2;
        PDist branchInitialAngleY = new PDist(180f, 180f, PDist.Type.uniform);
        PDist branchInitialAngleZ = new PDist(60, 20, PDist.Type.normal);
        PDist branchInitialLength = new PDist(0.5f, 0.1f, PDist.Type.normal);
        PDist branchInitialRadius = new PDist(0.01f, 0.003f, PDist.Type.normal);

        PDist segmentRotateX = new PDist(0, 15, PDist.Type.normal);
        PDist segmentRotateZ = new PDist(0, 15, PDist.Type.normal);

        PDist branchCurveAngleZ = new PDist(-8, 2, PDist.Type.normal);

        float trunkRotation = rnd.nextFloat();

        TreeDefinition tree = new TreeDefinition(trunkRotation);
        for (int i = 0; i < generation; i++) {
            // Grow existing segments and their branches
            for (TrunkSegmentDefinition segment : tree.segments) {
                segment.length += 0.2f;
                segment.radius += 0.03f;
                for (BranchDefinition branch : segment.branches) {
                    for (BranchSegmentDefinition branchSegment : branch.branchSegments) {
                        branchSegment.length += 0.08f;
                        branchSegment.radius += 0.01f;
                    }

                    branch.branchSegments.add(new BranchSegmentDefinition(
                            branchInitialLength.getValue(rnd), branchInitialRadius.getValue(rnd), branchCurveAngleZ.getValue(rnd)));
                }
            }

            int branchCount = rnd.nextInt(maxBranchesPerSegment);

            List<BranchDefinition> branches = new LinkedList<>();
            for (int branch = 0; branch < branchCount; branch++) {
                BranchDefinition branchDef = new BranchDefinition(
                        branchInitialAngleY.getValue(rnd), branchInitialAngleZ.getValue(rnd));
                branchDef.branchSegments.add(
                        new BranchSegmentDefinition(
                                branchInitialLength.getValue(rnd), branchInitialRadius.getValue(rnd), 0));
                branches.add(branchDef);
            }

            // Add new segment
            TrunkSegmentDefinition segment = new TrunkSegmentDefinition(branches,
                    newSegmentLength.getValue(rnd), newSegmentRadius.getValue(rnd),
                    segmentRotateX.getValue(rnd), segmentRotateZ.getValue(rnd));
            tree.addSegment(segment);
        }

        return tree;
    }

    private void generateTrunk(TreeDefinition treeDefinition, FloatArray vertices, ShortArray indices, int x, int y, int z) {
        Matrix4 goingMatrix = new Matrix4().translate(x + 0.5f, y, z + 0.5f);
        goingMatrix.rotate(new Vector3(0, 1, 0), treeDefinition.trunkRotationY);

        int vertexIndex = (short) (vertices.size / 8);

        Iterator<TrunkSegmentDefinition> segmentIterator = treeDefinition.segments.iterator();
        TrunkSegmentDefinition lastSegment = segmentIterator.next();
        Vector3 first = new Vector3(1, 0, 1).scl(lastSegment.radius).mul(goingMatrix);
        Vector3 second = new Vector3(-1, 0, 1).scl(lastSegment.radius).mul(goingMatrix);
        Vector3 third = new Vector3(-1, 0, -1).scl(lastSegment.radius).mul(goingMatrix);
        Vector3 fourth = new Vector3(1, 0, -1).scl(lastSegment.radius).mul(goingMatrix);
        while (segmentIterator.hasNext()) {
            TrunkSegmentDefinition thisSegment = segmentIterator.next();

            goingMatrix.rotate(new Vector3(0, 0, 1), thisSegment.rotateZ);
            goingMatrix.rotate(new Vector3(1, 0, 0), thisSegment.rotateX);
            goingMatrix.translate(0, lastSegment.length / 2, 0);
            Matrix4 branchMatrix = goingMatrix.cpy();
            for (BranchDefinition branch : lastSegment.branches) {
                vertexIndex = generateBranch(vertexIndex, branch, branchMatrix, vertices, indices);
            }

            goingMatrix.translate(0, lastSegment.length / 2, 0);

            Vector3 firstTop = new Vector3(1, 0, 1).scl(thisSegment.radius).mul(goingMatrix);
            Vector3 secondTop = new Vector3(-1, 0, 1).scl(thisSegment.radius).mul(goingMatrix);
            Vector3 thirdTop = new Vector3(-1, 0, -1).scl(thisSegment.radius).mul(goingMatrix);
            Vector3 fourthTop = new Vector3(1, 0, -1).scl(thisSegment.radius).mul(goingMatrix);

            vertexIndex = addQuad(vertexIndex, vertices, indices, first, firstTop, secondTop, second);
            vertexIndex = addQuad(vertexIndex, vertices, indices, second, secondTop, thirdTop, third);
            vertexIndex = addQuad(vertexIndex, vertices, indices, third, thirdTop, fourthTop, fourth);
            vertexIndex = addQuad(vertexIndex, vertices, indices, fourth, fourthTop, firstTop, first);

            first.set(firstTop);
            second.set(secondTop);
            third.set(thirdTop);
            fourth.set(fourthTop);

            lastSegment = thisSegment;
        }

        goingMatrix.translate(0, lastSegment.length, 0);

        Vector3 firstTop = new Vector3(0, 0, 0).mul(goingMatrix);
        Vector3 secondTop = new Vector3(0, 0, 0).mul(goingMatrix);
        Vector3 thirdTop = new Vector3(0, 0, 0).mul(goingMatrix);
        Vector3 fourthTop = new Vector3(0, 0, 0).mul(goingMatrix);

        vertexIndex = addQuad(vertexIndex, vertices, indices, first, firstTop, secondTop, second);
        vertexIndex = addQuad(vertexIndex, vertices, indices, second, secondTop, thirdTop, third);
        vertexIndex = addQuad(vertexIndex, vertices, indices, third, thirdTop, fourthTop, fourth);
        vertexIndex = addQuad(vertexIndex, vertices, indices, fourth, fourthTop, firstTop, first);
    }

    private int generateBranch(int vertexIndex, BranchDefinition branch, Matrix4 branchMatrix, FloatArray vertices, ShortArray indices) {
        branchMatrix.rotate(new Vector3(0, 1, 0), branch.angleY);
        branchMatrix.rotate(new Vector3(0, 0, 1), branch.angleZ);

        Iterator<BranchSegmentDefinition> segmentIterator = branch.branchSegments.iterator();
        BranchSegmentDefinition lastSegment = segmentIterator.next();
        Vector3 first = new Vector3(1, 0, 1).scl(lastSegment.radius).mul(branchMatrix);
        Vector3 second = new Vector3(-1, 0, 1).scl(lastSegment.radius).mul(branchMatrix);
        Vector3 third = new Vector3(-1, 0, -1).scl(lastSegment.radius).mul(branchMatrix);
        Vector3 fourth = new Vector3(1, 0, -1).scl(lastSegment.radius).mul(branchMatrix);
        while (segmentIterator.hasNext()) {
            BranchSegmentDefinition thisSegment = segmentIterator.next();

            branchMatrix.rotate(new Vector3(0, 0, 1), thisSegment.rotateZ);
            branchMatrix.translate(0, lastSegment.length, 0);

            Vector3 firstTop = new Vector3(1, 0, 1).scl(thisSegment.radius).mul(branchMatrix);
            Vector3 secondTop = new Vector3(-1, 0, 1).scl(thisSegment.radius).mul(branchMatrix);
            Vector3 thirdTop = new Vector3(-1, 0, -1).scl(thisSegment.radius).mul(branchMatrix);
            Vector3 fourthTop = new Vector3(1, 0, -1).scl(thisSegment.radius).mul(branchMatrix);

            vertexIndex = addQuad(vertexIndex, vertices, indices, first, firstTop, secondTop, second);
            vertexIndex = addQuad(vertexIndex, vertices, indices, second, secondTop, thirdTop, third);
            vertexIndex = addQuad(vertexIndex, vertices, indices, third, thirdTop, fourthTop, fourth);
            vertexIndex = addQuad(vertexIndex, vertices, indices, fourth, fourthTop, firstTop, first);

            first.set(firstTop);
            second.set(secondTop);
            third.set(thirdTop);
            fourth.set(fourthTop);

            lastSegment = thisSegment;
        }

        branchMatrix.translate(0, lastSegment.length, 0);

        Vector3 firstTop = new Vector3(0, 0, 0).mul(branchMatrix);
        Vector3 secondTop = new Vector3(0, 0, 0).mul(branchMatrix);
        Vector3 thirdTop = new Vector3(0, 0, 0).mul(branchMatrix);
        Vector3 fourthTop = new Vector3(0, 0, 0).mul(branchMatrix);

        vertexIndex = addQuad(vertexIndex, vertices, indices, first, firstTop, secondTop, second);
        vertexIndex = addQuad(vertexIndex, vertices, indices, second, secondTop, thirdTop, third);
        vertexIndex = addQuad(vertexIndex, vertices, indices, third, thirdTop, fourthTop, fourth);
        vertexIndex = addQuad(vertexIndex, vertices, indices, fourth, fourthTop, firstTop, first);

        return vertexIndex;
    }

    private int addQuad(int vertexIndex, FloatArray vertices, ShortArray indices, Vector3 first, Vector3 second, Vector3 third, Vector3 fourth) {
        vertices.add(first.x);
        vertices.add(first.y);
        vertices.add(first.z);
        vertices.add(0);
        vertices.add(1);
        vertices.add(0);
        vertices.add(oakBarkTexture.getU() + 1 * (oakBarkTexture.getU2() - oakBarkTexture.getU()));
        vertices.add(oakBarkTexture.getV() + 0 * (oakBarkTexture.getV2() - oakBarkTexture.getV()));

        vertices.add(second.x);
        vertices.add(second.y);
        vertices.add(second.z);
        vertices.add(0);
        vertices.add(1);
        vertices.add(0);
        vertices.add(oakBarkTexture.getU() + 1 * (oakBarkTexture.getU2() - oakBarkTexture.getU()));
        vertices.add(oakBarkTexture.getV() + 1 * (oakBarkTexture.getV2() - oakBarkTexture.getV()));

        vertices.add(third.x);
        vertices.add(third.y);
        vertices.add(third.z);
        vertices.add(0);
        vertices.add(1);
        vertices.add(0);
        vertices.add(oakBarkTexture.getU() + 0 * (oakBarkTexture.getU2() - oakBarkTexture.getU()));
        vertices.add(oakBarkTexture.getV() + 1 * (oakBarkTexture.getV2() - oakBarkTexture.getV()));

        vertices.add(fourth.x);
        vertices.add(fourth.y);
        vertices.add(fourth.z);
        vertices.add(0);
        vertices.add(1);
        vertices.add(0);
        vertices.add(oakBarkTexture.getU() + 0 * (oakBarkTexture.getU2() - oakBarkTexture.getU()));
        vertices.add(oakBarkTexture.getV() + 0 * (oakBarkTexture.getV2() - oakBarkTexture.getV()));

        indices.add(vertexIndex);
        indices.add(vertexIndex + 1);
        indices.add(vertexIndex + 2);
        indices.add(vertexIndex + 2);
        indices.add(vertexIndex + 3);
        indices.add(vertexIndex);

        return vertexIndex + 4;
    }

    private void generateLeaves(TreeDefinition treeDefinition, FloatArray vertices, ShortArray indices, int x, int y, int z) {
        // TODO
    }

    private void init() {
        if (oakBarkTexture == null) {
            oakBarkTexture = textureAtlasProvider.getTexture("blockTiles/plant/Tree/OakBark");
            oakLeafTexture = textureAtlasProvider.getTexture("blockTiles/plant/leaf/GreenLeaf");
        }
    }
}
