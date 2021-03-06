package com.gempukku.libgdx.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.gempukku.terasology.TerasologyApplication;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.backgroundFPS = 0;
		config.foregroundFPS = 0;
		config.vSyncEnabled = false;
		new LwjglApplication(new TerasologyApplication(), config);
	}
}
