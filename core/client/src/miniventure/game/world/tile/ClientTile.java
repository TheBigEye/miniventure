package miniventure.game.world.tile;

import miniventure.game.util.MyUtils;
import miniventure.game.world.tile.TileCacheTag.TileDataCache;
import miniventure.game.world.level.ClientLevel;
import miniventure.game.world.level.Level;
import miniventure.game.world.tile.TileStack.TileData;

import org.jetbrains.annotations.NotNull;

public class ClientTile extends RenderTile {
	
	/*public ClientTile(@NotNull Level level, int x, int y, @NotNull TileTypeEnum[] types, @NotNull TileDataMap[] data) {
		this((ClientLevel)level, x, y, types, data);
	}
	public ClientTile(@NotNull ClientLevel level, int x, int y, @NotNull TileTypeEnum[] types, @NotNull TileDataMap[] data) {
		super(level, x, y, types, data);
	}*/
	public ClientTile(@NotNull Level level, int x, int y) {
		this((ClientLevel)level, x, y);
	}
	public ClientTile(@NotNull ClientLevel level, int x, int y) {
		super(level, x, y, new TileTypeEnum[] {TileTypeEnum.HOLE}, null);
	}
	
	@NotNull @Override
	public ClientLevel getLevel() { return (ClientLevel) super.getLevel(); }
	
	public void apply(TileData tileData, TileTypeEnum updatedType) {
		// MyUtils.debug("client tile edited, old top: "+getType());
		ClientTileStack newStack = makeStack(tileData.getTypes(), tileData.getDataMaps());
		/*for(TileTypeEnum type: getTypeStack().getEnumTypes()) {
			if(type == updatedType)
				continue; // forget the animation start time, because this is a new animation.
			// Float curValue = getLevel().animStartTimes.get(this);
			// if(curValue != null)
			// 	continue; // there's no data to transfer.
			// TileDataCache dest = newStack.getCacheMap(type);
			// if(dest != null) // can be null if the new stack is missing a type that the current stack has.
		}*/
		setTileStack(newStack);
		// MyUtils.debug("new top: "+getType());
	}
}
