package miniventure.game.world.entity.mob;

import java.util.ArrayList;
import java.util.Arrays;

import miniventure.game.network.GameProtocol.Hurt;
import miniventure.game.network.GameProtocol.MobUpdate;
import miniventure.game.network.GameProtocol.SpriteUpdate;
import miniventure.game.item.Item;
import miniventure.game.item.Result;
import miniventure.game.item.ToolItem;
import miniventure.game.item.ToolItem.ToolType;
import miniventure.game.util.Version;
import miniventure.game.util.function.ValueAction;
import miniventure.game.world.WorldObject;
import miniventure.game.world.entity.ClassDataList;
import miniventure.game.world.entity.Direction;
import miniventure.game.world.entity.KnockbackController;
import miniventure.game.world.entity.ServerEntity;
import miniventure.game.world.entity.mob.MobAnimationController.AnimationState;
import miniventure.game.world.entity.mob.player.Player;
import miniventure.game.world.entity.mob.player.ServerPlayer;
import miniventure.game.world.entity.particle.ParticleData.TextParticleData;
import miniventure.game.world.level.ServerLevel;
import miniventure.game.world.management.ServerWorld;
import miniventure.game.world.tile.TileTypeEnum;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is necessary because it ought to nicely package up the functionality of a mob, that moves around, and has up/down/left/right walking animations. Though, I may move the directional + state driven animation to its own class...
 */
public abstract class ServerMob extends ServerEntity implements Mob {
	
	@NotNull private Direction dir;
	@NotNull private MobAnimationController animator;
	
	private final int maxHealth;
	private int health;
	
	@NotNull private KnockbackController knockbackController;
	
	private float invulnerableTime = 0;
	
	private final String spriteName;
	
	protected ServerMob(@NotNull ServerWorld world, @NotNull String spriteName, int health) {
		super(world);
		dir = Direction.DOWN;
		this.maxHealth = health;
		this.health = health;
		
		this.spriteName = spriteName;
		
		knockbackController = new KnockbackController(this);
		
		animator = new MobAnimationController<>(this, spriteName);
	}
	
	// some stuff is given in the child constructor; this shouldn't need to be saved to file.
	// these include the sprite name and the max health, in this case.
	// these things
	protected ServerMob(@NotNull ServerWorld world, ClassDataList allData, final Version version, ValueAction<ClassDataList> modifier) {
		super(world, allData, version, modifier);
		ArrayList<String> data = allData.get(1);
		
		this.spriteName = data.get(0);
		dir = Direction.valueOf(data.get(1));
		maxHealth = Integer.parseInt(data.get(2));
		health = Integer.parseInt(data.get(3));
		// invulnerableTime = Float.parseFloat(data.get(4));
		
		knockbackController = new KnockbackController(this);
		animator = new MobAnimationController<>(this, spriteName);
	}
	
	@Override
	public ClassDataList save() {
		ClassDataList allData = super.save();
		ArrayList<String> data = new ArrayList<>(Arrays.asList(
			spriteName,
			dir.name(),
			String.valueOf(maxHealth),
			String.valueOf(health)
			// String.valueOf(invulnerableTime)
		));
		
		allData.add(data);
		return allData;
	}
	
	public void reset() {
		dir = Direction.DOWN;
		this.health = maxHealth;
		knockbackController.reset();
		invulnerableTime = 0;
		animator.progressAnimation(0);
	}
	
	protected int getHealth() { return health; }
	protected void setHealth(int health) { this.health = health; if(health == 0) die(); }
	
	@Override
	public Direction getDirection() { return dir; }
	protected void setDirection(@NotNull Direction dir) {
		if(animator.setDirection(dir)) {
			this.dir = dir;
			getServer().broadcast(new MobUpdate(getTag(), dir), this);
		}
	}
	
	@Override
	public boolean isKnockedBack() { return knockbackController.hasKnockback(); }
	
	@Override
	public void update(float delta) {
		animator.progressAnimation(delta);
		
		SpriteUpdate newSprite = animator.getSpriteUpdate();
		if(newSprite != null)
			updateSprite(newSprite);
		
		knockbackController.update(delta);
		
		super.update(delta);
		
		if(invulnerableTime > 0) invulnerableTime -= Math.min(invulnerableTime, delta);
	}
	
	@Override @NotNull
	public Rectangle getBounds() {
		Rectangle bounds = super.getBounds();
		bounds.setHeight(Mob.shortenSprite(bounds.getHeight()));
		return bounds;
	}
	
	@Override
	public boolean move(float xd, float yd, float zd) {
		boolean moved = super.move(xd, yd, zd);
		
		if(xd != 0 || yd != 0) animator.requestState(AnimationState.WALK);
		
		Direction dir = Direction.getDirection(xd, yd);
		if(dir != null) {
			// set/change sprite direction
			setDirection(dir);
		}
		
		return moved;
	}
	
	@Override
	public Result attackedBy(WorldObject obj, @Nullable Item item, int damage) {
		if(invulnerableTime > 0) return Result.INTERACT; // consume the event, but not the item.
		
		boolean use = false;
		if(item instanceof ToolItem) {
			use = true;
			ToolItem ti = (ToolItem) item;
			if(ti.getToolType() == ToolType.Sword)
				damage *= 3;
			else if(ti.getToolType() == ToolType.Axe)
				damage *= 2;
			else
				use = false;
		}
		
		health -= Math.min(damage, health);
		invulnerableTime = HURT_COOLDOWN;
		
		getServer().playEntitySound("hurt", this);
		
		if(health > 0) {
			// do knockback
			if(!(this instanceof Player)) // client will take care of it for theirself
				knockbackController.knock(obj, KNOCKBACK_SPEED, Mob.getKnockbackDuration(damage*1f/maxHealth));
		}
		
		ServerLevel level = getLevel();
		if(level != null) {
			getServer().broadcast(new Hurt(obj.getTag(), getTag(), damage*1f/maxHealth));
			getServer().broadcastParticle(new TextParticleData(String.valueOf(damage), this instanceof ServerPlayer ? Color.PINK : Color.RED), this);
		}
		
		if (health == 0)
			die();
		
		return use ? Result.USED : Result.INTERACT;
	}
	
	protected void regenHealth(int amount) { health = Math.min(maxHealth, health + amount); }
	
	public void die() { remove(); }
	
	public boolean maySpawn() { return true; }
	public boolean maySpawn(TileTypeEnum type) {
		return type == TileTypeEnum.GRASS || type == TileTypeEnum.DIRT || type == TileTypeEnum.SAND || type == TileTypeEnum.SNOW;
	}
}
