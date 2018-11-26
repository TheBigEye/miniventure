package miniventure.game.client;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashMap;

import miniventure.game.GameCore;
import miniventure.game.GameProtocol.Message;
import miniventure.game.chat.InfoMessage;
import miniventure.game.item.InventoryScreen;
import miniventure.game.screen.ErrorScreen;
import miniventure.game.screen.LoadingScreen;
import miniventure.game.screen.MainMenu;
import miniventure.game.screen.MenuScreen;
import miniventure.game.screen.util.BackgroundInheritor;
import miniventure.game.screen.util.BackgroundProvider;
import miniventure.game.util.MyUtils;
import miniventure.game.util.function.ValueFunction;
import miniventure.game.world.tile.ClientTileType;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.kotcrab.vis.ui.VisUI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** @noinspection StaticNonFinalField*/
public class ClientCore extends ApplicationAdapter {
	
	public static final int DEFAULT_SCREEN_WIDTH = 800;
	public static final int DEFAULT_SCREEN_HEIGHT = 450;
	private static GameScreen gameScreen;
	private static ClientWorld clientWorld;
	
	public static boolean viewedInstructions = false;
	
	private static Music song;
	private static final HashMap<String, Sound> soundEffects = new HashMap<>();
	
	public static final InputHandler input = new InputHandler();
	
	private static boolean hasMenu = false;
	private static MenuScreen menuScreen;
	
	private static final Object screenLock = new Object();
	private static SpriteBatch batch;
	private static FreeTypeFontGenerator fontGenerator;
	private static GlyphLayout layout = new GlyphLayout();
	private static Skin skin;
	private static HashMap<Integer, BitmapFont> fonts = new HashMap<>();
	
	private final ServerManager serverStarter;
	
	public static boolean PLAY_MUSIC = false;
	
	public static final ValueFunction<Throwable> exceptionNotifier = throwable -> {
		StringWriter string = new StringWriter();
		PrintWriter printer = new PrintWriter(string);
		throwable.printStackTrace(printer);
		
		JTextArea errorDisplay = new JTextArea(string.toString());
		errorDisplay.setEditable(false);
		JScrollPane errorPane = new JScrollPane(errorDisplay);
		JOptionPane.showMessageDialog(null, errorPane, "An error has occurred", JOptionPane.ERROR_MESSAGE);
	};
	
	public static final UncaughtExceptionHandler exceptionHandler = (thread, throwable) -> {
		exceptionNotifier.act(throwable);
		
		throwable.printStackTrace();
	};
	
	public ClientCore(ServerManager serverStarter) {
		this.serverStarter = serverStarter;
	}
	
	@Override
	public void create () {
		VisUI.load(Gdx.files.internal("skins/visui/uiskin.json"));
		
		LoadingScreen loader = new LoadingScreen();
		loader.pushMessage("Initializing...", true);
		setScreen(loader);
		//System.out.println("start delay");
		MyUtils.delay(0, () -> Gdx.app.postRunnable(() -> {
			//System.out.println("end delay");
			GameCore.initGdxTextures();
			if(batch == null)
				batch = new SpriteBatch();
			fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/arial.ttf"));
			skin = new Skin(Gdx.files.internal("skins/visui/uiskin.json"));
			
			getFont(); // initialize default font
			ClientTileType.init();
			
			gameScreen = new GameScreen();
			clientWorld = new ClientWorld(serverStarter, gameScreen);
			
			setScreen(new MainMenu());
		}));
	}
	
	@Override
	public void dispose () {
		batch.dispose();
		skin.dispose();
		fontGenerator.dispose();
		for(BitmapFont font: fonts.values())
			font.dispose();
		fonts.clear();
		
		if(gameScreen != null)
			gameScreen.dispose();
		
		if(menuScreen != null)
			menuScreen.dispose();
		
		GameCore.dispose();
	}
	
	@Override
	public void render() {
		input.update();
		
		getBatch().setColor(new Color(1, 1, 1, 1));
		
		if (clientWorld != null && clientWorld.worldLoaded())
			clientWorld.update(GameCore.getDeltaTime()); // renders as well
		
		synchronized (screenLock) {
			hasMenu = menuScreen != null;
			
			if(menuScreen != null)
				menuScreen.act();
			if(menuScreen != null)
				menuScreen.draw();
		}
	}
	
	public static void setScreen(@Nullable MenuScreen screen) {
		synchronized (screenLock) {
			if(menuScreen instanceof InventoryScreen) {
				((InventoryScreen) menuScreen).close();
			}
			
			if(menuScreen != null && screen != null && screen == menuScreen.getParent()) {
				backToParentScreen();
				return;
			}
			
			if(menuScreen instanceof MainMenu && screen instanceof ErrorScreen)
				return; // ignore it.
			
			if(menuScreen instanceof BackgroundProvider && screen instanceof BackgroundInheritor)
				((BackgroundInheritor) screen).setBackground((BackgroundProvider) menuScreen);
			
			if(screen != null) {
				// error and loading (and chat) screens can have parents, but cannot be parents.
				screen.setParent(menuScreen);
				screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
			} else if(menuScreen != null && (gameScreen == null || menuScreen != gameScreen.chatScreen))
				menuScreen.dispose();
			
			System.out.println("setting screen to " + screen);
			
			if(gameScreen != null) {
				if(screen instanceof MainMenu) {
					gameScreen.chatScreen.reset();
					gameScreen.chatOverlay.reset();
				}
			}
			
			input.resetDelay();
			menuScreen = screen;
			if(menuScreen != null) menuScreen.focus();
			if(gameScreen == null) {
				Gdx.input.setInputProcessor(menuScreen == null ? input : new InputMultiplexer(input, menuScreen));
			} else {
				Gdx.input.setInputProcessor(menuScreen == null ? new InputMultiplexer(gameScreen.getGuiStage(), input) : new InputMultiplexer(input.repressDelay(.1f), menuScreen, gameScreen.getGuiStage()));
			}
		}
	}
	public static void backToParentScreen() {
		synchronized (screenLock) {
			if(menuScreen != null && menuScreen.getParent() != null) {
				MenuScreen screen = menuScreen.getParent();
				System.out.println("setting screen back to " + screen);
				if(gameScreen == null || menuScreen != gameScreen.chatScreen)
					menuScreen.dispose(false);
				screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
				menuScreen = screen;
				Gdx.input.setInputProcessor(menuScreen);
				input.reset();
			}
		}
	}
	
	public static void playSound(String soundName) {
		if(!soundEffects.containsKey(soundName))
			soundEffects.put(soundName, Gdx.audio.newSound(Gdx.files.internal("audio/effects/"+soundName+".wav")));
		
		Sound s = soundEffects.get(soundName);
		//System.out.println("playing sound "+soundName+": "+s);
		if(s != null)
			s.play();
	}
	
	public static Music setMusicTrack(@NotNull FileHandle file) {
		stopMusic();
		song = Gdx.audio.newMusic(file);
		return song;
	}
	public static void stopMusic() {
		if(song != null) {
			song.stop();
			song.dispose();
		}
		song = null;
	}
	
	static void addMessage(Message msg) {
		gameScreen.chatOverlay.addMessage(msg);
		gameScreen.chatScreen.addMessage(msg);
	}
	static void addMessage(InfoMessage msg) {
		gameScreen.chatOverlay.addMessage(msg);
		gameScreen.chatScreen.addMessage(msg);
	}
	
	@Override
	public void resize(int width, int height) {
		if(gameScreen != null)
			gameScreen.resize(width, height);
		
		MenuScreen menu = getScreen();
		if(menu != null)
			menu.resize(width, height);
	}
	
	
	public static Skin getSkin() { return skin; }
	
	public static SpriteBatch getBatch() {
		if(batch == null) batch = new SpriteBatch();
		return batch;
	}
	
	public static GlyphLayout getTextLayout(String text) {
		if(fontGenerator != null)
			layout.setText(getFont(), text);
		return layout;
	}
	
	private static FreeTypeFontParameter getDefaultFontConfig(int size) {
		FreeTypeFontParameter params = new FreeTypeFontParameter();
		params.size = size;
		params.color = Color.WHITE;
		params.borderColor = Color.BLACK;
		params.borderWidth = 1;
		params.spaceX = -1;
		//params.magFilter = TextureFilter.Linear;
		params.shadowOffsetX = 1;
		params.shadowOffsetY = 1;
		params.shadowColor = Color.BLACK;
		return params;
	}
	
	public static BitmapFont getFont(int size) {
		if(!fonts.containsKey(size)) {
			BitmapFont font = fontGenerator.generateFont(getDefaultFontConfig(size));
			font.setUseIntegerPositions(true);
			fonts.put(size, font);
		}
		
		BitmapFont font = fonts.get(size);
		font.setColor(Color.WHITE);
		return font;
	}
	
	public static BitmapFont getFont() {
		return getFont(15);
	}
	
	public static boolean hasMenu() { synchronized (screenLock) { return hasMenu; } }
	
	@Nullable
	public static MenuScreen getScreen() { synchronized (screenLock) { return menuScreen; } }
	
	public static ClientWorld getWorld() { return clientWorld; }
	public static GameClient getClient() { return clientWorld.getClient(); }
}
