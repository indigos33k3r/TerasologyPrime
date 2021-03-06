package com.gempukku.terasology.graphics.environment.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class MyShaderProvider implements ShaderProvider {

    public enum Mode {
        ENVIRONMENT, ENVIRONMENT_SHADOW
    }

    private EnvironmentShadowShader environmentShadowShader;
    private EnvironmentShader environmentShader;

    private Mode mode;
    private Matrix4 lightTrans;
    private Vector3 lightPosition;
    private Vector3 lightDirection;
    private float lightPlaneDistance;
    private float time;
    private Vector3 skyColor;
    private float ambientLight;
    private boolean night;
    private int shadowMapSize;

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public void setSkyColor(Vector3 skyColor) {
        this.skyColor = skyColor;
    }

    public void setAmbientLight(float ambientLight) {
        this.ambientLight = ambientLight;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public void setLightTrans(Matrix4 lightTrans) {
        this.lightTrans = lightTrans;
    }

    public void setLightPosition(Vector3 lightPosition) {
        this.lightPosition = lightPosition;
    }

    public void setLightDirection(Vector3 lightDirection) {
        this.lightDirection = lightDirection;
    }

    public void setLightPlaneDistance(float lightPlaneDistance) {
        this.lightPlaneDistance = lightPlaneDistance;
    }

    public void setNight(boolean night) {
        this.night = night;
    }

    public void setShadowMapSize(int shadowMapSize) {
        this.shadowMapSize = shadowMapSize;
    }

    @Override
    public Shader getShader(Renderable renderable) {
        if (mode == Mode.ENVIRONMENT_SHADOW) {
            if (environmentShadowShader == null)
                environmentShadowShader = createEnvironmentShadowShader(renderable);
            environmentShadowShader.setLightDirection(lightDirection);
            environmentShadowShader.setLightPlaneDistance(lightPlaneDistance);
            environmentShadowShader.setTime(time);
            return environmentShadowShader;
        } else if (mode == Mode.ENVIRONMENT) {
            if (environmentShader == null)
                environmentShader = createEnvironmentShader(renderable);
            environmentShader.setLightTrans(lightTrans);
            environmentShader.setLightPosition(lightPosition);
            environmentShader.setLightDirection(lightDirection);
            environmentShader.setLightPlaneDistance(lightPlaneDistance);
            environmentShader.setTime(time);
            environmentShader.setFogColor(skyColor);
            environmentShader.setAmbientLight(ambientLight);
            environmentShader.setNoDirectionalLight(night);
            environmentShader.setShadowMapSize(shadowMapSize);
            return environmentShader;
        } else {
            return null;
        }
    }

    @Override
    public void dispose() {
        if (environmentShadowShader != null)
            environmentShadowShader.dispose();
        if (environmentShader != null)
            environmentShader.dispose();
    }

    private EnvironmentShader createEnvironmentShader(Renderable renderable) {
        DefaultShader.Config config = new DefaultShader.Config(
                Gdx.files.internal("shader/environment.vert").readString(),
                Gdx.files.internal("shader/environment.frag").readString());
        EnvironmentShader environmentShader = new EnvironmentShader(renderable, config);
        environmentShader.init();
        return environmentShader;
    }

    private EnvironmentShadowShader createEnvironmentShadowShader(Renderable renderable) {
        DefaultShader.Config config = new DefaultShader.Config(
                Gdx.files.internal("shader/environmentShadow.vert").readString(),
                Gdx.files.internal("shader/environmentShadow.frag").readString());
        EnvironmentShadowShader environmentShadowShader = new EnvironmentShadowShader(renderable, config);
        environmentShadowShader.init();
        return environmentShadowShader;
    }
}
