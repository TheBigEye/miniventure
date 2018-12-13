package miniventure.game.util;

import org.json.JSONObject;

public class VersionInfo {
	
	public final Version version;
	public final String assetUrl;
	public final String releaseName;
	
	public VersionInfo(JSONObject releaseInfo) {
		String versionTag = releaseInfo.getString("tag_name").substring(1); // cut off the "v" at the beginning
		if(!releaseInfo.getBoolean("prerelease"))
			versionTag = versionTag.substring(0, versionTag.lastIndexOf("."));
		version = new Version(versionTag);
		
		assetUrl = releaseInfo.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
		
		releaseName = releaseInfo.getString("name");
	}
	
	public VersionInfo(Version version, String assetUrl, String releaseName) {
		this.version = version;
		this.assetUrl = assetUrl;
		this.releaseName = releaseName;
	}
	
}
