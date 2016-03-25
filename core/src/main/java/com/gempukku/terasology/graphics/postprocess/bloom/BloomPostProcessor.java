package com.gempukku.terasology.graphics.postprocess.bloom;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.gempukku.secsy.context.annotation.In;
import com.gempukku.secsy.context.annotation.NetProfiles;
import com.gempukku.secsy.context.annotation.RegisterSystem;
import com.gempukku.secsy.context.system.LifeCycleSystem;
import com.gempukku.secsy.entity.EntityRef;
import com.gempukku.terasology.graphics.environment.renderer.RenderingBuffer;
import com.gempukku.terasology.graphics.postprocess.PostProcessingRenderer;
import com.gempukku.terasology.graphics.postprocess.PostProcessingRendererRegistry;

@RegisterSystem(
        profiles = NetProfiles.CLIENT)
public class BloomPostProcessor implements PostProcessingRenderer, LifeCycleSystem {
    @In
    private PostProcessingRendererRegistry postProcessingRendererRegistry;

    private ModelBatch modelBatch;

    private BloomShaderProvider bloomShaderProvider;
    private ModelInstance modelInstance;
    private Model model;

    @Override
    public void preInitialize() {
        bloomShaderProvider = new BloomShaderProvider();

        modelBatch = new ModelBatch(bloomShaderProvider);
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();
        MeshPartBuilder backgroundBuilder = modelBuilder.part("screen", GL20.GL_TRIANGLES, VertexAttributes.Usage.Position, new Material());
        backgroundBuilder.rect(
                -1, 1, 1,
                -1, -1, 1,
                1, -1, 1,
                1, 1, 1,
                0, 0, 1);
        model = modelBuilder.end();

        modelInstance = new ModelInstance(model);
    }

    @Override
    public void initialize() {
        postProcessingRendererRegistry.registerPostProcessingRenderer(this);
    }

    @Override
    public boolean isEnabled(EntityRef observerEntity) {
        return observerEntity.hasComponent(BloomComponent.class);
    }

    @Override
    public void render(EntityRef observerEntity, RenderingBuffer renderingBuffer, Camera camera,
                       int sourceBoundColorTexture, int sourceBoundDepthTexture) {
        BloomComponent bloom = observerEntity.getComponent(BloomComponent.class);
        bloomShaderProvider.setSourceTextureIndex(sourceBoundColorTexture);
        bloomShaderProvider.setBlurRadius(bloom.getBlurRadius());
        bloomShaderProvider.setMinimalBrightness(bloom.getMinimalBrightness());
        bloomShaderProvider.setBloomStrength(bloom.getBloomStrength());

        renderingBuffer.begin();

        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        modelBatch.render(modelInstance);
        modelBatch.end();
        renderingBuffer.end();
    }

    @Override
    public void postDestroy() {
        modelBatch.dispose();
        model.dispose();
    }

}
