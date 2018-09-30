package miniventure.game.screen;

import javax.swing.*;
import javax.swing.border.BevelBorder;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import miniventure.game.GameCore;
import miniventure.game.client.ClientCore;
import miniventure.game.client.ClientWorld;
import miniventure.game.client.LevelViewport;
import miniventure.game.screen.InfoScreen.CreditsScreen;
import miniventure.game.screen.InfoScreen.InstructionsScreen;
import miniventure.game.util.VersionInfo;
import miniventure.game.world.DisplayLevel;
import miniventure.game.world.TimeOfDay;
import miniventure.game.world.levelgen.LevelGenerator;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

public class MainMenu extends MenuScreen implements BackgroundProvider {
	
	private boolean dialog = false;
	
	private final DisplayLevel backgroundLevel;
	private final LevelViewport levelView;
	private final com.badlogic.gdx.graphics.Color lightOverlay;
	private final Vector2 cameraPos, cameraDir;
	
	private final JPanel labelPanel = new JPanel() {
		@Override
		protected void paintComponent(final Graphics g) {
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
			super.paintComponent(g);
		}
	};
	
	private static final float PAN_SPEED = 4.5f; // in tiles/second.
	
	public MainMenu() {
		super(true, true);
		ClientWorld world = ClientCore.getWorld();
		
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.PAGE_AXIS));
		labelPanel.setOpaque(false);
		labelPanel.setBackground(new Color(163, 227, 232, 200));
		labelPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.CYAN, Color.GRAY), BorderFactory.createEmptyBorder(20, 30, 20, 20)));
		
		addLabel("Welcome to Miniventure!", 20);
		addLabel("You are playing version " + GameCore.VERSION, 25);
		
		JLabel updateLabel = addLabel("Checking for higher versions...", 0);
		add(labelPanel);
		add(Box.createVerticalStrut(45));
		setVersionUpdateLabel(updateLabel);
		
		JButton playButton = makeButton("Play", () -> {
			if(dialog) return;
			world.createWorld(0, 0);
		});
		add(playButton);
		add(Box.createVerticalStrut(20));
		
		JButton joinBtn = makeButton("Join Server", () -> {
			if(dialog) return;
			dialog = true;
			EventQueue.invokeLater(() -> {
				String ipAddress = JOptionPane.showInputDialog("Enter the IP Address you want to connect to.");
				Gdx.app.postRunnable(() -> {
					if(ipAddress != null) {
						// LoadingScreen loader = new LoadingScreen(this);
						// loader.pushMessage("Preparing to connect...");
						// ClientCore.setScreen(loader);
						world.createWorld(ipAddress);
					}
				});
				dialog = false;
			});
		});
		
		add(joinBtn);
		add(Box.createVerticalStrut(20));
		
		JButton helpBtn = makeButton("Instructions", () -> ClientCore.setScreen(new InstructionsScreen()));
		add(helpBtn);
		add(Box.createVerticalStrut(20));
		
		JButton creditsBtn = makeButton("Credits", () -> ClientCore.setScreen(new CreditsScreen()));
		add(creditsBtn);
		add(Box.createVerticalStrut(20));
		
		JButton exitBtn = makeButton("Quit", () -> Gdx.app.exit());
		add(exitBtn);
		
		// setup level scrolling in background
		
		levelView = new LevelViewport();
		levelView.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		levelView.zoom(-1);
		TimeOfDay time = TimeOfDay.values[MathUtils.random(TimeOfDay.values.length-1)];
		lightOverlay = TimeOfDay.getSkyColor(time.getStartOffsetSeconds());
		
		LevelGenerator generator = new LevelGenerator(MathUtils.random.nextLong(), 200, 100, true);
		backgroundLevel = new DisplayLevel(generator);
		
		Vector2 size = new Vector2(levelView.getViewWidth(), levelView.getViewHeight());//.scl(0.5f);
		cameraPos = new Vector2(MathUtils.random(size.x, backgroundLevel.getWidth()-size.x), MathUtils.random(size.y, backgroundLevel.getHeight()-size.y));
		
		cameraDir = new Vector2().setLength(PAN_SPEED).setToRandomDirection().setLength(PAN_SPEED);
		
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				Gdx.app.postRunnable(() -> levelView.resize(getWidth(), getHeight()));
			}
		});
		
		// setup background music
		if(ClientCore.PLAY_MUSIC) {
			Music song = ClientCore.setMusicTrack(Gdx.files.internal("audio/music/title.mp3"));
			song.setLooping(true);
			song.play();
		}
	}
	
	@Override
	public void glDraw() {
		super.glDraw();
		// levelView.handleInput();
		levelView.render(cameraPos, lightOverlay, backgroundLevel);
		
		cameraPos.add(cameraDir.cpy().scl(GameCore.getDeltaTime()));
		cameraDir.x = velDir(cameraPos.x, cameraDir.x, levelView.getViewWidth()/2, backgroundLevel.getWidth() - levelView.getViewWidth()/2);
		cameraDir.y = velDir(cameraPos.y, cameraDir.y, levelView.getViewHeight()/2, backgroundLevel.getHeight() - levelView.getViewHeight()/2);
	}
	
	private float velDir(float pos, float vel, float min, float max) {
		if((pos >= max && vel >= 0) || (pos <= min && vel <= 0)) {
			vel += MathUtils.random(-PAN_SPEED/4, PAN_SPEED/4);
			vel = -vel;
		}
		
		return vel;
	}
	
	private JLabel addLabel(String msg, int spacing) {
		JLabel label = makeLabel(msg);
		label.setForeground(Color.BLACK);
		labelPanel.add(label);
		if(spacing > 0) labelPanel.add(Box.createVerticalStrut(spacing));
		return label;
	}
	
	private void setVersionUpdateLabel(JLabel label) {
		if(!GameCore.determinedLatestVersion()) {
			// return a "loading" label that will be replaced once the version check completes.
			new Thread(() -> {
				GameCore.getLatestVersion();
				setVersionUpdateLabel(label);
			}).start();
		}
		else {
			// add a message saying you have the latest version, or a hyperlink message to the newest jar file.
			VersionInfo latestVersion = GameCore.getLatestVersion();
			
			if(latestVersion.version.compareTo(GameCore.VERSION) > 0) { // link new version
				JButton btn = new JButton("Miniventure " + latestVersion.releaseName + " Now Available! Click here to download.");
				btn.addActionListener(e -> openLink(latestVersion.assetUrl));
				labelPanel.remove(label);
				labelPanel.add(btn);
			}
			else {
				if(latestVersion.releaseName.length() > 0)
					label.setText("You have the latest version.");
				else
					label.setText("Connection failed, could not check for updates.");
			}
			
			revalidate();
			repaint();
		}
	}
	
	private void openLink(String url) {
		if(!Desktop.isDesktopSupported())
			showLink(url);
		else {
			Desktop desktop = Desktop.getDesktop();
			if(!desktop.isSupported(Action.BROWSE))
				showLink(url);
			else {
				try {
					desktop.browse(new URI(url));
				} catch(IOException | URISyntaxException e) {
					e.printStackTrace();
					showLink(url);
				}
			}
		}
	}
	
	private void showLink(String url) {
		EventQueue.invokeLater(() -> JOptionPane.showMessageDialog(ClientCore.getUiPanel(), url, "Site Link", JOptionPane.INFORMATION_MESSAGE));
	}
}
