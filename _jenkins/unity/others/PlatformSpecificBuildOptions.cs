using UnityEditor;

public struct PlatformSpecificBuildOptions
{
	public readonly BuildOptions BuildOptionsDevelopment;
	public readonly BuildOptions BuildOptionsRelease;

	public PlatformSpecificBuildOptions(BuildOptions optionsDevelopment, BuildOptions optionsRelease) 
	{
		BuildOptionsDevelopment = optionsDevelopment;
		BuildOptionsRelease = optionsRelease;
	}
}
