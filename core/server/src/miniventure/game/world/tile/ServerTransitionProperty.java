package miniventure.game.world.tile;

import org.jetbrains.annotations.NotNull;

public class ServerTransitionProperty extends TransitionProperty {
	
	public ServerTransitionProperty(TransitionProperty model) {
		super(model);
	}
	
	// exit animation
	@Override
	public boolean tryStartAnimation(Tile tile, @NotNull TileType next, boolean addNext) {
		ServerTile sTile = (ServerTile) tile;
		boolean success = super.tryStartAnimation(tile, next, addNext);
		if(success) sTile.getLevel().onTileUpdate(sTile);
		return success;
	}
}
