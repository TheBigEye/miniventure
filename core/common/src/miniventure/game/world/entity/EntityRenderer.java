package miniventure.game.world.entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;

import miniventure.game.GameCore;
import miniventure.game.item.Item;
import miniventure.game.texture.TextureHolder;
import miniventure.game.util.MyUtils;
import miniventure.game.util.blinker.Blinker;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EntityRenderer {
	
	private float elapsedTime = 0;
	
	protected abstract String[] save();
	
	public void update(float delta) { elapsedTime += delta; }
	//public void reset() { elapsedTime = 0; }
	
	public abstract void render(float x, float y, Batch batch, float drawableHeight);
	
	public abstract Vector2 getSize();
	
	public static class SpriteRenderer extends EntityRenderer {
		
		private static final HashMap<String, TextureHolder> textures = new HashMap<>();
		
		private final String spriteName;
		private final TextureHolder sprite;
		
		public SpriteRenderer(String spriteName, TextureHolder texture) {
			this.spriteName = spriteName;
			this.sprite = texture;
		}
		public SpriteRenderer(String spriteName) {
			this.spriteName = spriteName;
			
			if(!textures.containsKey(spriteName))
				textures.put(spriteName, GameCore.entityAtlas.findRegion(spriteName));
			
			sprite = textures.get(spriteName);
		}
		protected SpriteRenderer(String[] data) {
			this(data[0]);
		}
		
		@Override
		protected String[] save() { return new String[] {spriteName}; }
		
		public String getName() { return spriteName; }
		
		@Override
		public void render(float x, float y, Batch batch, float drawableHeight) { batch.draw(sprite.texture.split(sprite.width, (int)(sprite.height*drawableHeight))[0][0], x, y); }
		
		@Override
		public Vector2 getSize() { return new Vector2(sprite.width, sprite.height); }
	}
	
	
	public static class ItemSpriteRenderer extends SpriteRenderer {
		
		private final Item item;
		
		public ItemSpriteRenderer(Item item) {
			super(item.getName(), item.getTexture());
			this.item = item;
		}
		
		protected ItemSpriteRenderer(String[] data) {
			this(Item.load(data));
		}
		
		@Override
		protected String[] save() { return item.save(); }
	}
	
	
	public static class AnimationRenderer extends EntityRenderer {
		
		private static final HashMap<String, Array<TextureHolder>> animationFrames = new HashMap<>();
		
		private final String animationName;
		private final boolean isFrameDuration;
		private final float duration;
		private final boolean loopAnimation;
		
		private final Animation<TextureHolder> animation;
		
		public AnimationRenderer(String animationName, float frameTime) { this(animationName, frameTime, true, true); }
		public AnimationRenderer(String animationName, float duration, boolean isFrameDuration, boolean loopAnimation) {
			this.animationName = animationName;
			this.duration = duration;
			this.isFrameDuration = isFrameDuration;
			this.loopAnimation = loopAnimation;
			
			if(!animationFrames.containsKey(animationName))
				animationFrames.put(animationName, GameCore.entityAtlas.findRegions(animationName));
			
			Array<TextureHolder> frames = animationFrames.get(animationName);
			
			animation = new Animation<>(isFrameDuration ? duration : duration/frames.size, frames);
		}
		private AnimationRenderer(String[] data) {
			this(data[0], Float.parseFloat(data[1]), Boolean.parseBoolean(data[2]), Boolean.parseBoolean(data[3]));
		}
		
		@Override
		protected String[] save() {
			return new String[] {animationName, String.valueOf(duration), String.valueOf(isFrameDuration), String.valueOf(loopAnimation)};
		}
		
		public String getName() { return animationName; }
		
		private TextureHolder getSprite() { return animation.getKeyFrame(super.elapsedTime, loopAnimation); }
		
		@Override
		public void render(float x, float y, Batch batch, float drawableHeight) {
			TextureHolder sprite = getSprite();
			batch.draw(sprite.texture.split(sprite.width, (int)(sprite.height*drawableHeight))[0][0], x, y);
		}
		
		@Override
		public Vector2 getSize() {
			TextureHolder frame = getSprite();
			return new Vector2(frame.width, frame.height);
		}
	}
	
	
	public static class DirectionalAnimationRenderer extends EntityRenderer {
		
		@FunctionalInterface
		public interface DirectionalSpriteFetcher {
			String getSpriteName(Direction dir);
		}
		
		private final EnumMap<Direction, AnimationRenderer> animations = new EnumMap<>(Direction.class);
		
		@NotNull private Direction dir;
		
		public DirectionalAnimationRenderer(@NotNull Direction initialDir, DirectionalSpriteFetcher spriteFetcher, float frameTime) { this(initialDir, spriteFetcher, frameTime, true, true); }
		public DirectionalAnimationRenderer(@NotNull Direction initialDir, DirectionalSpriteFetcher spriteFetcher, float duration, boolean isFrameDuration, boolean loopAnimation) {
			this.dir = initialDir;
			
			for(Direction dir: Direction.values)
				animations.put(dir, new AnimationRenderer(spriteFetcher.getSpriteName(dir), duration, isFrameDuration, loopAnimation));
		}
		
		private DirectionalAnimationRenderer(String[] data) {
			this.dir = Direction.valueOf(data[0]);
			
			int i = 1;
			for(Direction dir: Direction.values) {
				animations.put(dir, new AnimationRenderer(MyUtils.parseLayeredString(data[i])));
				i++;
			}
		}
		
		@Override
		protected String[] save() {
			String[] data = new String[animations.size()+1];
			data[0] = dir.name();
			
			int i = 1;
			for(Direction dir: Direction.values) {
				data[i] = MyUtils.encodeStringArray(animations.get(dir).save());
				i++;
			}
			
			return data;
		}
		
		public void setDirection(@NotNull Direction dir) { this.dir = dir; }
		
		@Override
		public void render(float x, float y, Batch batch, float drawableHeight) {
			AnimationRenderer renderer = animations.get(dir);
			((EntityRenderer)renderer).elapsedTime = super.elapsedTime;
			renderer.render(x, y, batch, drawableHeight);
		}
		
		@Override
		public Vector2 getSize() { return animations.get(dir).getSize(); }
	}
	
	
	public static class TextRenderer extends EntityRenderer {
		
		@NotNull private final String text;
		private final Color main;
		private final Color shadow;
		private final float width;
		private final float height;
		
		public TextRenderer(@NotNull String text, Color main, Color shadow) {
			this.text = text;
			this.main = main;
			this.shadow = shadow;
			GlyphLayout layout = GameCore.getTextLayout(text);
			this.width = layout.width;
			this.height = layout.height;
		}
		private TextRenderer(String[] data) {
			this(data[0], Color.valueOf(data[1]), Color.valueOf(data[2]));
		}
		
		@Override
		protected String[] save() { return new String[] {text, main.toString(), shadow.toString()}; }
		
		@Override
		public void render(float x, float y, Batch batch, float drawableHeight) {
			BitmapFont font = GameCore.getFont();
			font.setColor(shadow);
			font.draw(batch, text, x-1, y+1, 0, Align.center, false);
			font.setColor(main);
			try {
				font.draw(batch, text, x, y, 0, Align.center, false);
			} catch(Exception e) {
				System.err.println("error drawing text");
				e.printStackTrace();
			}
		}
		
		@Override
		public Vector2 getSize() { return new Vector2(width, height); }
	}
	
	// NOTE, I won't be using this for mobs. I want to just enforce blinking no matter the sprite underneath, and only for a period of time...
	public static class BlinkRenderer extends EntityRenderer {
		
		private EntityRenderer mainRenderer;
		private final boolean blinkFirst;
		private final float initialDuration;
		@Nullable private final Color blinkColor;
		private final Blinker blinker;
		
		public BlinkRenderer(EntityRenderer mainRenderer, @Nullable Color blinkColor, float initialDuration, boolean blinkFirst, Blinker blinker) {
			this.mainRenderer = mainRenderer;
			this.blinkColor = blinkColor;
			this.initialDuration = initialDuration;
			this.blinkFirst = blinkFirst;
			this.blinker = blinker;
		}
		
		private BlinkRenderer(String[] data) {
			this(EntityRenderer.deserialize(MyUtils.parseLayeredString(data[0])), data[1].equals("null")?null:Color.valueOf(data[1]), Float.parseFloat(data[2]), Boolean.parseBoolean(data[3]), Blinker.load(MyUtils.parseLayeredString(data[4])));
		}
		
		public void setRenderer(EntityRenderer renderer) {
			mainRenderer = renderer;
		}
		
		@Override
		protected String[] save() {
			return new String[] {
				MyUtils.encodeStringArray(EntityRenderer.serialize(mainRenderer)),
				blinkColor==null?"null":blinkColor.toString(),
				String.valueOf(initialDuration),
				String.valueOf(blinkFirst),
				MyUtils.encodeStringArray(blinker.save())
			};
		}
		
		private boolean blinkerActive() {
			boolean firstDuration = super.elapsedTime <= initialDuration;
			return blinkFirst == firstDuration; // blinker is active if (should blink first == is first time), aka the current time matches when it should be active.
		}
		
		@Override
		public void update(float delta) {
			super.update(delta);
			if(blinkerActive())
				blinker.update(delta);
		}
		
		@Override
		public void render(float x, float y, Batch batch, float drawableHeight) {
			if(!blinkerActive() || blinker.shouldRender())
				mainRenderer.render(x, y, batch, drawableHeight);
			else if(blinkColor != null) {
				Color c = batch.getColor();
				batch.setColor(blinkColor);
				mainRenderer.render(x, y, batch, drawableHeight);
				batch.setColor(c);
			}
		}
		
		@Override
		public Vector2 getSize() { return mainRenderer.getSize(); }
	}
	
	
	public static final EntityRenderer BLANK = new EntityRenderer() {
		@Override public void render(float x, float y, Batch batch, float drawableHeight) {}
		@Override public Vector2 getSize() { return new Vector2(); }
		@Override protected String[] save() { return new String[0]; }
	};
	
	
	public static String[] serialize(EntityRenderer renderer) {
		String[] data = renderer.save();
		String[] allData = new String[data.length+1];
		
		String className = renderer.getClass().getCanonicalName();
		if(className == null)
			allData[0] = "";
		else
			allData[0] = className.replace(EntityRenderer.class.getCanonicalName()+".", "");
		
		System.arraycopy(data, 0, allData, 1, data.length);
		
		return allData;
	}
	
	@NotNull
	public static EntityRenderer deserialize(String[] allData) {
		if(allData[0].length() == 0) return BLANK;
		
		String className = allData[0];
		String[] data = Arrays.copyOfRange(allData, 1, allData.length);
		
		try {
			//Class<?> clazz = Class.forName(EntityRenderer.class.getPackage().getName()+"."+className);
			for(Class<?> inner: EntityRenderer.class.getDeclaredClasses()) {
				//System.out.println("found inner class " + inner.getSimpleName());
				if(inner.getSimpleName().equals(className)) {
					
					Class<? extends EntityRenderer> erClass = inner.asSubclass(EntityRenderer.class);
					//noinspection JavaReflectionMemberAccess
					Constructor<? extends EntityRenderer> constructor = erClass.getDeclaredConstructor(String[].class);
					
					constructor.setAccessible(true);
					return constructor.newInstance((Object)data);
				}
			}
			
			throw new ClassNotFoundException(className);
			
		} catch(ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
			return BLANK;
		}
	}
}
