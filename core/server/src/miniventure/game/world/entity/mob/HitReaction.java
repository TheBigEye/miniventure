package miniventure.game.world.entity.mob;

import miniventure.game.item.ServerItem;
import miniventure.game.world.WorldObject;
import miniventure.game.world.entity.Entity;
import miniventure.game.world.entity.mob.PursuePattern.FleePattern;
import miniventure.game.world.entity.mob.player.Player;

@FunctionalInterface
public interface HitReaction {
	void onHit(MobAi attacked, WorldObject attacker, ServerItem attackItem);
	
	HitReaction FLEE = (attacked, attacker, item) -> attacked.setMovePattern(new FleePattern((self) -> attacker), 5f);
	HitReaction CHASE = (attacked, attacker, item) -> {
		if(attacker instanceof Entity)
			attacked.setMovePattern(new PursuePattern((self) -> attacker, Player.MOVE_SPEED*4f/5), 5f);
	};
}
