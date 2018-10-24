package miniventure.game.util.function;

@FunctionalInterface
public interface ValueBiFunction<P1, P2> {
	void act(P1 obj1, P2 obj2);
}
