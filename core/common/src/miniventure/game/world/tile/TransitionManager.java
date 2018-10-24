package miniventure.game.world.tile;

import java.util.EnumMap;
import java.util.HashMap;

import miniventure.game.texture.TextureHolder;
import miniventure.game.util.customenum.SerialMap;
import miniventure.game.world.tile.TileType.TileTypeEnum;
import miniventure.game.world.tile.data.TileCacheTag;

import com.badlogic.gdx.utils.Array;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransitionManager {
	
	static EnumMap<TileTypeEnum, HashMap<String, Array<TextureHolder>>> tileAnimations = new EnumMap<>(TileTypeEnum.class);
	
	private final TileTypeEnum tileType;
	private final HashMap<String, TransitionAnimation> entranceAnimations = new HashMap<>();
	private final HashMap<String, TransitionAnimation> exitAnimations = new HashMap<>();
	
	// store this in DataMap, along with trans start time?
	public enum TransitionMode {
		ENTERING, EXITING, NONE
	}
	
	public TransitionManager(@NotNull TileTypeEnum tileType) {
		this.tileType = tileType;
	}
	public TransitionManager(@NotNull TransitionManager manager) {
		this(manager.tileType);
		entranceAnimations.putAll(manager.entranceAnimations);
		exitAnimations.putAll(manager.exitAnimations);
	}
	
	public TransitionManager addEntranceAnimations(@NotNull TransitionAnimation... animations) {
		for(TransitionAnimation transition: animations)
			entranceAnimations.put(transition.name, transition);
		
		return this;
	}
	
	public TransitionManager addExitAnimations(@NotNull TransitionAnimation... animations) {
		for(TransitionAnimation transition: animations)
			exitAnimations.put(transition.name, transition);
		
		return this;
	}
	
	
	/*@Nullable
	private TransitionAnimation getAnimationStyle(DataMap dataMap) {
		TransitionMode mode = dataMap.getOrDefault(CacheTag.TransitionMode, TransitionMode.NONE);
		String name = dataMap.get(CacheTag.TransitionName);
		return getAnimationStyle(mode, name);
	}*/
	@Nullable
	private TransitionAnimation getAnimationStyle(TransitionMode mode, String name) {
		TransitionAnimation animation = null;
		if(mode == TransitionMode.ENTERING)
			animation = entranceAnimations.get(name);
		else if(mode == TransitionMode.EXITING)
			animation = exitAnimations.get(name);
		
		return animation;
	}
	
	
	// enter animation
	public boolean tryStartAnimation(@NotNull Tile tile, @NotNull TileType previous) {
		return tryStartAnimation(tile, true, previous, false);
	}
	// exit animation
	public boolean tryStartAnimation(@NotNull Tile tile, @NotNull TileType next, boolean addNext) {
		return tryStartAnimation(tile, false, next, addNext);
	}
	// check for transition animation; tiletype is being entered or removed, and given what tile type will be the main one next.
	private boolean tryStartAnimation(@NotNull Tile tile, boolean isEntering, @NotNull TileType other, boolean addNext) { // addNext is ignored if isEntering is true
		HashMap<String, TransitionAnimation> animations = isEntering ? entranceAnimations : exitAnimations;
		for(TransitionAnimation animation: animations.values()) {
			if(animation.isTriggerType(other)) {
				SerialMap dataMap = tile.getDataMap();
				dataMap.put(TileCacheTag.TransitionName, animation.name);
				float start = tile.getWorld().getGameTime();
				dataMap.put(TileCacheTag.TransitionStart, start);
				dataMap.put(TileCacheTag.TransitionMode, isEntering ? TransitionMode.ENTERING : TransitionMode.EXITING);
				if(addNext)
					dataMap.put(TileCacheTag.TransitionTile, other.getEnumType());
				else
					dataMap.remove(TileCacheTag.TransitionTile);
				return true;
			}
		}
		
		return false;
	}
	
	TileAnimation<TextureHolder> getTransitionSprite(@NotNull Tile tile) {
		SerialMap dataMap = tile.getDataMap(tileType);
		
		TransitionMode mode = dataMap.get(TileCacheTag.TransitionMode);
		String name = dataMap.get(TileCacheTag.TransitionName);
		
		TransitionAnimation animation = getAnimationStyle(mode, name);
		if(animation == null)
			throw new IllegalStateException("Cannot get transition sprite when not transitioning.");
		
		return animation.getAnimation(tileType, name, tileAnimations);
	}
	
	/*public float getTimeRemaining(@NotNull Tile tile) {
		if(!playingAnimation(tile)) return 0;
		TransitionAnimation curTransition = getAnimationStyle(tile.getDataMap(tileType));
		if(curTransition == null)
			return 0;
		
		float start = tile.getDataMap(tileType).get(CacheTag.TransitionStart);
		float now = tile.getWorld().getGameTime();
		float duration = curTransition.time;
		return duration - (now - start);
	}*/
	
	void resetAnimation(@NotNull Tile tile) {
		SerialMap map = tile.getDataMap(tileType);
		map.put(TileCacheTag.TransitionStart, tile.getWorld().getGameTime());
	}
	
	private boolean isTransitionMode(@NotNull Tile tile, TransitionMode mode) {
		SerialMap map = tile.getDataMap(tileType);
		return map.getOrDefault(TileCacheTag.TransitionMode, TransitionMode.NONE) == mode;
	}
	
	public boolean playingAnimation(@NotNull Tile tile) { return !isTransitionMode(tile, TransitionMode.NONE); }
	public boolean playingEntranceAnimation(@NotNull Tile tile) { return isTransitionMode(tile, TransitionMode.ENTERING); }
	public boolean playingExitAnimation(@NotNull Tile tile) { return isTransitionMode(tile, TransitionMode.EXITING); }
	
	public float tryFinishAnimation(@NotNull Tile tile) {
		SerialMap dataMap = tile.getDataMap(tileType);
		
		TransitionAnimation anim = getAnimationStyle(dataMap.get(TileCacheTag.TransitionMode), dataMap.get(TileCacheTag.TransitionName));
		
		if(anim == null)
			return 0;
		
		float now = tile.getWorld().getGameTime();
		float prev = dataMap.get(TileCacheTag.TransitionStart);
		float timeElapsed = now - prev;
		
		if(timeElapsed < anim.getDuration())
			return anim.getDuration() - timeElapsed;
		
		
		TransitionMode mode = dataMap.remove(TileCacheTag.TransitionMode);
		dataMap.remove(TileCacheTag.TransitionStart);
		dataMap.remove(TileCacheTag.TransitionName);
		TileTypeEnum nextType = dataMap.remove(TileCacheTag.TransitionTile);
		
		// if entering, no action required. if removing, remove the current tile from the stack, specifying not to check for an exit animation. If removing, and there is data for a tile type, then add that tile type.
		
		if(mode == TransitionMode.EXITING) {
			tile.breakTile(false);
			if(nextType != null)
				tile.addTile(nextType.getTileType(tile.getWorld()));
		}
		
		return 0;
	}
}
