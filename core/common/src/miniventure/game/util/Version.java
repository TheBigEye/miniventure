package miniventure.game.util;

import org.jetbrains.annotations.NotNull;

public class Version implements Comparable<Version> {
	
	static class VersionFormatException extends IllegalArgumentException {
		public VersionFormatException(String versionString) {
			super("The string \"" + versionString + "\" does not represent a valid version format; valid formats are #.# or #.#.# or #.#.#.dev");
		}
	}
	
	private final int make, major, minor;
	private final boolean dev;
	
	private Version() {make = 0; major = 0; minor = 0; dev = false;}
	public Version(String version) {
		String[] nums = version.split("\\.");
		if(nums.length > 4 || nums.length < 2)
			throw new VersionFormatException(version);
		
		try {
			make = new Integer(nums[0]);
			major = new Integer(nums[1]);
			if(nums.length > 2)
				minor = new Integer(nums[2]);
			else
				minor = -1;
			
			if(make < 0 || major < 0 || (nums.length > 2 && minor < 0))
				throw new VersionFormatException(version);
			
			dev = nums.length > 3;
			if(dev && !nums[3].equalsIgnoreCase("dev"))
				throw new VersionFormatException(version);
			
		} catch(NumberFormatException ex) {
			throw new VersionFormatException(version);
		}
	}
	
	@Override
	public int compareTo(@NotNull Version other) {
		if(make != other.make) return Integer.compare(make, other.make);
		if(major != other.major) return Integer.compare(major, other.major);
		if(minor != other.minor) {
			if(minor < 0) return 1; // -1 means after all other numbers, so this is after the other version.
			else if(other.minor < 0) return -1; // this is before the other version.
			else return Integer.compare(minor, other.minor); // compare normally.
		}
		if(dev != other.dev) return dev ? -1 : 1; // the dev version comes before the non-dev one.
		
		return 0; // versions are the same.
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof Version)) return false;
		Version o = (Version) other;
		return make==o.make && major==o.major && minor==o.minor && dev==o.dev;
	}
	
	@Override
	public int hashCode() {
		int result = make;
		result = 31 * result + major;
		result = 31 * result + minor;
		result = 17 * result + (dev ? 1 : 0);
		return result;
	}
	
	@Override
	public String toString() {
		return make+"."+major+(minor<0?" Official":", Pre-Release "+minor)+(dev?" (Alpha Build)":"");
	}
}
