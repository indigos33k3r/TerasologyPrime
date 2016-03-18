package com.gempukku.terasology.graphics.environment.renderer;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

public class EnvironmentShader extends DefaultShader {
    private final int u_depthMap = register("u_depthMap");
    private final int u_lightTrans = register("u_lightTrans");
    private final int u_lightDirection = register("u_lightDirection");
    private final int u_lightPosition = register("u_lightPosition");
    private final int u_lightPlaneDistance = register("u_lightPlaneDistance");
    private final int u_ambientLighting = register("u_ambientLighting");
    private final int u_fogColor = register("u_fogColor");
    private final int u_time = register("u_time");

    private Matrix4 lightTrans;
    private Vector3 lightDirection;
    private Vector3 lightPosition;
    private Vector3 fogColor;
    private float lightPlaneDistance;
    private float time;

    public EnvironmentShader(Renderable renderable, Config config) {
        super(renderable, config);
    }

    public void setFogColor(Vector3 fogColor) {
        this.fogColor = fogColor;
    }

    public void setTime(float time) {
        this.time = time;
    }

    public void setLightTrans(Matrix4 lightTrans) {
        this.lightTrans = lightTrans;
    }

    public void setLightDirection(Vector3 lightDirection) {
        this.lightDirection = lightDirection;
    }

    public void setLightPlaneDistance(float lightPlaneDistance) {
        this.lightPlaneDistance = lightPlaneDistance;
    }

    public void setLightPosition(Vector3 lightPosition) {
        this.lightPosition = lightPosition;
    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        super.begin(camera, context);

        set(u_lightTrans, lightTrans);
        set(u_lightPosition, lightPosition);
        set(u_lightDirection, lightDirection);
        set(u_lightPlaneDistance, lightPlaneDistance);
        set(u_depthMap, 2);
        set(u_ambientLighting, 0.2f);
        set(u_fogColor, fogColor);
        set(u_time, time);
    }
}
