package miniventure.game.world.tile;

import java.util.LinkedHashMap;

import miniventure.game.world.tile.AnimationProperty.AnimationType;

public interface TileProperty {
	
	static LinkedHashMap<String, TileProperty> getDefaultPropertyMap() {
		LinkedHashMap<String, TileProperty> map = new LinkedHashMap<>();
		map.put(SolidProperty.class.getName(), SolidProperty.WALKABLE);
		map.put(DestructibleProperty.class.getName(), DestructibleProperty.INDESTRUCTIBLE);
		map.put(InteractableProperty.class.getName(), (InteractableProperty)((p, i, t) -> {}));
		map.put(TouchListener.class.getName(), (TouchListener)((entity, tile) -> {}));
		map.put(AnimationProperty.class.getName(), new AnimationProperty(AnimationType.SINGLE_FRAME));
		map.put(ConnectionProperty.class.getName(), new ConnectionProperty(false));
		map.put(OverlapProperty.class.getName(), new OverlapProperty(false));
		map.put(UpdateProperty.class.getName(), (UpdateProperty) ((delta, tile) -> {}));
		return map;
	}
	
	Integer[] getInitData();
	
}
