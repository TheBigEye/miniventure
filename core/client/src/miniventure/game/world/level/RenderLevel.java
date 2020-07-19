package miniventure.game.world.level;

import java.util.Set;

import miniventure.game.world.WorldObject;
import miniventure.game.world.entity.Entity;
import miniventure.game.world.entity.particle.ActionParticle;
import miniventure.game.world.management.LevelWorldManager;
import miniventure.game.world.tile.Tile;
import miniventure.game.world.tile.TileTypeEnum;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import org.jetbrains.annotations.NotNull;

public abstract class RenderLevel extends Level {
	
	public final TileDataMap<Float> animStartTimes = new TileDataMap<>();
	
	protected RenderLevel(@NotNull LevelWorldManager world, LevelId levelId, @NotNull TileTypeEnum[][][] tileTypes, @NotNull TileMaker tileFetcher) {
		super(world, levelId, tileTypes, tileFetcher);
	}
	
	protected RenderLevel(@NotNull LevelWorldManager world, LevelId levelId, int width, int height, TileFetcher tileFetcher) {
		super(world, levelId, width, height, tileFetcher);
	}
	
	@Override @NotNull
	public LevelWorldManager getWorld() { return (LevelWorldManager) super.getWorld(); }
	
	
	
	@Override
	public int getEntityCount() { return getWorld().getEntityTotal(); }
	
	@Override
	public Set<? extends Entity> getEntities() { return getWorld().getRegisteredEntities(); }
	
	public abstract void render(Rectangle renderSpace, SpriteBatch batch, float delta, Vector2 posOffset);
	
	public static void render(Array<Tile> tiles, Array<Entity> entities, SpriteBatch batch, float delta, Vector2 posOffset) {
		// pass the offset vector to all objects being rendered.
		
		Array<WorldObject> objects = new Array<>();
		Array<Tile> under = new Array<>(); // ground tiles
		Array<Entity> over = new Array<>();
		for(Entity e: entities) {
			if(e.isFloating() && !(e instanceof ActionParticle))
				over.add(e);
			else
				objects.add(e);
		}
		for(Tile t: tiles) {
			if(!t.getType().isWalkable()) // used to check if z offset > 0
				objects.add(t);
			else
				under.add(t);
		}
		
		// first, ground tiles
		// then, entities and surface tiles, higher y first
		// then particles
		
		// entities second
		under.sort((e1, e2) -> Float.compare(e2.getCenter().y, e1.getCenter().y));
		objects.sort((e1, e2) -> Float.compare(e2.getCenter().y, e1.getCenter().y));
		over.sort((e1, e2) -> Float.compare(e2.getCenter().y, e1.getCenter().y));
		//objects.addAll(entities);
		
		for(WorldObject obj: under)
			obj.render(batch, delta, posOffset);
		for(WorldObject obj: objects)
			obj.render(batch, delta, posOffset);
		for(WorldObject obj: over)
			obj.render(batch, delta, posOffset);
	}
	
	public static Array<Vector3> renderLighting(Array<WorldObject> objects) {
		Array<Vector3> lighting = new Array<>();
		
		for(WorldObject obj: objects) {
			float lightR = obj.getLightRadius();
			if(lightR > 0)
				lighting.add(new Vector3(obj.getCenter(), lightR));
		}
		
		return lighting;
	}
}
