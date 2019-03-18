package miniventure.game.chat.command;

import java.util.Arrays;

import miniventure.game.util.MyUtils;
import miniventure.game.util.function.FetchFunction;
import miniventure.game.util.function.MapFunction;
import miniventure.game.world.entity.mob.player.ServerPlayer;
import miniventure.game.world.management.Config;
import miniventure.game.world.management.ServerWorld;
import miniventure.game.world.management.TimeOfDay;

import org.jetbrains.annotations.NotNull;

public interface Argument {
	
	boolean satisfiedBy(@NotNull ServerWorld world, String[] args, int offset);
	int length();
	
	static Argument get(@NotNull ArgValidator... validators) {
		return new Argument() {
			@Override
			public boolean satisfiedBy(@NotNull ServerWorld world, String[] args, int offset) {
				for(int i = 0; i < validators.length; i++)
					if(!validators[i].isValid(world, args[i]))
						return false;
				
				return true;
			}
			
			@Override
			public int length() { return validators.length; }
		};
	}
	
	/*static Argument[] get(@NotNull ArgumentValidator... validators) {
		Argument[] args = new Argument[validators.length];
		
		for(int i = 0; i < args.length; i++) {
			final int index = i;
			args[i] = new Argument() {
				@Override
				public boolean satisfiedBy(String[] args, int offset) {
					return validators[index].isValid(args[offset]);
				}
				
				@Override public int length() { return 1; }
			};
		}
		
		return args;
	}*/
	
	static Argument varArg(ArgValidator validator) {
		return new Argument() {
			@Override
			public boolean satisfiedBy(@NotNull ServerWorld world, String[] args, int offset) {
				return validator.isValid(world, String.join(" ", Arrays.copyOfRange(args, offset, args.length)));
			}
			
			@Override
			public int length() { return -1; }
		};
	}
	
	interface ArgValidator<T> {
		static <T> T notNull(FetchFunction<T> function) throws IllegalArgumentException {
			T obj = function.get();
			if(obj == null)
				throw new IllegalArgumentException();
			return obj;
		}
		static <T> T noException(FetchFunction<T> function) throws IllegalArgumentException {
			T obj;
			try {
				obj = function.get();
			} catch(Throwable t) {
				throw new IllegalArgumentException();
			}
			
			return obj;
		}
		
		SimpleArgValidator<String> ANY = arg -> arg;
		SimpleArgValidator<Integer> INTEGER = arg -> noException(() -> Integer.parseInt(arg));
		SimpleArgValidator<Float> DECIMAL = arg -> noException(() -> Float.parseFloat(arg));
		SimpleArgValidator<Boolean> BOOLEAN = arg -> noException(() -> Boolean.parseBoolean(arg));
		ArgValidator<ServerPlayer> PLAYER = (world, arg) -> notNull(() -> world.getServer().getPlayerByName(arg));
		SimpleArgValidator<Command> COMMAND = arg -> noException(() -> Enum.valueOf(Command.class, arg.toUpperCase()));
		SimpleArgValidator<Float> CLOCK_DURATION = arg -> noException(() -> {
			String[] parts = arg.split(":");
			int hour = Integer.parseInt(parts[0]);
			int min = Integer.parseInt(parts[1]);
			
			if(hour < 0 || hour >= 24 || min < 0 || min >= 60) throw new IllegalArgumentException();
			
			float time = hour + (min / 60f);
			
			return MyUtils.mapFloat(time, 0, 24, 0, TimeOfDay.SECONDS_IN_DAY);
		});
		SimpleArgValidator<Float> CLOCK_TIME = arg -> {
			float duration = CLOCK_DURATION.get(arg);
			float total = TimeOfDay.SECONDS_IN_DAY;
			return (duration + total - TimeOfDay.SECONDS_START_TIME_OFFSET) % total;
			//time = (time + 24 - ) % 24;
			//return MyUtils.mapFloat(time, 0, 24, 0, TimeOfDay.SECONDS_IN_DAY);
		};
		SimpleArgValidator<TimeOfDay> TIME_RANGE = arg -> noException(() -> TimeOfDay.valueOf(MyUtils.toTitleCase(arg)));
		SimpleArgValidator<Float> TIME = anyOf(CLOCK_TIME, map(TIME_RANGE, TimeOfDay::getStartOffsetSeconds));
		SimpleArgValidator<Config> CONFIG_VALUE = arg -> notNull(() -> Config.valueOf(arg));
		
		@SafeVarargs
		static <T> SimpleArgValidator<T> anyOf(SimpleArgValidator<T>... validators) {
			return arg -> {
				for(SimpleArgValidator<T> validator: validators) {
					if(validator.isValid(arg)) {
						return validator.get(arg);
					}
				}
				
				throw new IllegalArgumentException();
			};
		}
		
		static <T1, T2> SimpleArgValidator<T2> map(SimpleArgValidator<T1> orig, MapFunction<T1, T2> mapper) { return arg -> mapper.get(orig.get(arg)); }
		
		static SimpleArgValidator<String> exactString(boolean matchCase, String... matches) { return exactString(str -> str, matchCase, matches); }
		static <T> SimpleArgValidator<T> exactString(MapFunction<String, T> resultMapper, boolean matchCase, String... matches) {
			return arg -> {
				for(String match : matches)
					if(matchCase ? arg.equals(match) : arg.equalsIgnoreCase(match))
						return resultMapper.get(arg);
				
				throw new IllegalArgumentException();
			};
		}
		
		T get(@NotNull ServerWorld world, String arg) throws IllegalArgumentException;
		
		default boolean isValid(@NotNull ServerWorld world, String arg) {
			try {
				get(world, arg);
			} catch(IllegalArgumentException ex) {
				return false;
			}
			
			return true;
		}
	}
	
	interface SimpleArgValidator<T> extends ArgValidator<T> {
		
		T get(String arg) throws IllegalArgumentException;
		
		@Override
		default T get(@NotNull ServerWorld world, String arg) throws IllegalArgumentException {
			return get(arg);
		}
		
		default boolean isValid(String arg) {
			try {
				get(arg);
			} catch(IllegalArgumentException ex) {
				return false;
			}
			
			return true;
		}
	}
}
