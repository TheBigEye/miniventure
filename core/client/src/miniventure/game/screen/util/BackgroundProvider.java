package miniventure.game.screen.util;

import miniventure.game.client.ClientCore;
import miniventure.game.screen.MenuScreen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.viewport.Viewport;

public abstract class BackgroundProvider extends MenuScreen {
	
	private final boolean clearGdxBackground;
	private final boolean externallyCleared;
	
	public BackgroundProvider(boolean clearGdxBackground) { this(clearGdxBackground, false); }
	public BackgroundProvider(boolean clearGdxBackground, Viewport viewport) { this(clearGdxBackground, false, viewport); }
	public BackgroundProvider(boolean clearGdxBackground, boolean externallyCleared) {
		super(false);
		this.clearGdxBackground = clearGdxBackground;
		this.externallyCleared = externallyCleared;
	}
	public BackgroundProvider(boolean clearGdxBackground, boolean externallyCleared, Viewport viewport) {
		super(false, viewport, ClientCore.getBatch());
		this.clearGdxBackground = clearGdxBackground;
		this.externallyCleared = externallyCleared;
	}
	
	public abstract void renderBackground();
	
	public abstract void resizeBackground(int width, int height);
	
	@Override
	public void resize(int width, int height) {
		super.resize(width, height);
		resizeBackground(width, height);
	}
	
	@Override
	public boolean usesWholeScreen() { return clearGdxBackground || externallyCleared; }
	
	@Override
	public void draw() {
		if(clearGdxBackground)
			Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		renderBackground();
		super.draw();
	}
}
