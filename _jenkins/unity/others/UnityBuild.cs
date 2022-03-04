using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using UnityEditor;

public class UnityBuild
{
	/*
	 * -- Game information --
	 */
	private static readonly string GameName = PlayerSettings.productName;

	/*
	 * -- Dependancies --
	 * 
	 * Depending on the platform you are building for, you may need to direct Unity
	 * to any specific depenencies. This includes but is not limited to:
	 * 
	 * Android SDK
	 * Android NDK
	 * 
	 * As well as any other depenencies a platform may have.
	 * 
	 * Make sure to change these to your own personal paths to the dependencies!
	 */
	private static readonly string AndroidSdkDirectory = "/Users/root/Library/Android/sdk";

	/*
	 * -- Configuration --
	 * 
	 * Here I use 2 dictionaries for telling the script how to compile the builds.
	 * 
	 * First, called 'PlatformToBuild' is used to instruct the class how to build the application. 
	 * This includes the BuildTarget, Action for calling the build method, and a boolean instructing
	 * the class if you would actually like to build for this platform.
	 * 
	 * Second, called 'PlatformBuildOptions' is used to instruct the class with what options you would
	 * like to build for each specific platform. Each platform has BuildOptions for both development and release builds.
	 * 
	 * You can easily switch between Development/Release by adjusting the boolean directly below this message.
	 */	
	private static readonly bool IsDevelopmentBuild = true;

	private static readonly Dictionary<BuildTarget, PlatformBuilds> PlatformToBuild = new Dictionary<BuildTarget, PlatformBuilds>()
	{
		{ BuildTarget.StandaloneWindows64,      new PlatformBuilds(BuildWindows, false) },
		{ BuildTarget.StandaloneLinuxUniversal, new PlatformBuilds(BuildLinux,   true) },
		{ BuildTarget.StandaloneOSX,            new PlatformBuilds(BuildMacOS,   false) },
		{ BuildTarget.Android,                  new PlatformBuilds(BuildAndroid, true) },
		{ BuildTarget.iOS,                      new PlatformBuilds(BuildiOS,     false) },
		{ BuildTarget.WebGL,                    new PlatformBuilds(BuildWebGL,   false) },
	};

	private static readonly Dictionary<BuildTarget, PlatformSpecificBuildOptions> PlatformBuildOptions = new Dictionary<BuildTarget, PlatformSpecificBuildOptions>()
	{
		{ BuildTarget.StandaloneWindows64,        new PlatformSpecificBuildOptions(BuildOptions.Development | BuildOptions.CompressWithLz4,                                 BuildOptions.CompressWithLz4HC) },
		{ BuildTarget.StandaloneLinuxUniversal,   new PlatformSpecificBuildOptions(BuildOptions.Development | BuildOptions.CompressWithLz4,                                 BuildOptions.CompressWithLz4HC) },
		{ BuildTarget.StandaloneOSX,              new PlatformSpecificBuildOptions(BuildOptions.Development | BuildOptions.CompressWithLz4,                                 BuildOptions.CompressWithLz4HC) },
		{ BuildTarget.Android,                    new PlatformSpecificBuildOptions(BuildOptions.Development | BuildOptions.CompressWithLz4,                                 BuildOptions.CompressWithLz4HC) },
		{ BuildTarget.iOS,                        new PlatformSpecificBuildOptions(BuildOptions.Development | BuildOptions.CompressWithLz4 | BuildOptions.SymlinkLibraries, BuildOptions.CompressWithLz4HC) },
		{ BuildTarget.WebGL,                      new PlatformSpecificBuildOptions(BuildOptions.Development | BuildOptions.CompressWithLz4,                                 BuildOptions.CompressWithLz4HC) },
	};

	private static PlatformBuilds GetPlatformBuildTargets(BuildTarget buildPlatform) 
	{
		return PlatformToBuild[buildPlatform];
	}
	
	private static BuildOptions GetPlatformBuildOptions(BuildTarget buildPlatform) 
	{
		if (IsDevelopmentBuild == true)
			return PlatformBuildOptions[buildPlatform].BuildOptionsDevelopment;
		else
			return PlatformBuildOptions[buildPlatform].BuildOptionsRelease;
	}

	/*
	 * -- Paths --
	 * 
	 * Here, we have 2 different paths.
	 * 
	 * First is a temporary location where the application is built to initially. This
	 * temporary location can be anywhere on your drive, however I recommend that it is a location
	 * not synced by Google Drive or Dropbox or another cloud solution.
	 * 
	 * Second is the final location for builds after they have been compiled to the first location
	 * and where they are sent after being compressed into a zip folder. This is where I recommend using 
	 * a Google Drive or Dropbox path.
	 * 
	 * Below are examples that I personally use. Documents for the temp directory and Google Drive 
	 * for the final zip location. 
	 * 
	 * DriveTempDirectory and DriveDirectory should be changed for your own paths!
	 */
	private static readonly string DriveTempDirectory = "/Users/root/Documents";
	private static readonly string DriveTempFolderName = "JenkinsTemp";

	private static readonly string DriveDirectory = "/Users/root/Unity";
	private static readonly string DriveFolderName = "Jenkins";

	private static string DriveBuildLocation     => Path.Combine(DriveDirectory, DriveFolderName);
	private static string DriveBuildTempLocation => Path.Combine(DriveTempDirectory, DriveTempFolderName);

	private static string DriveGameLocation     => Path.Combine(DriveBuildLocation, GameName);
	private static string DriveGameLocationTemp => Path.Combine(DriveBuildTempLocation, GameName);

	private static string DriveGameLocationFolders     => Path.Combine(DriveGameLocation,     DateTime.Now.ToString("yyyy"), DateTime.Now.ToString("MMMM dd"), BuildPlatform.ToString("G"));
	private static string DriveGameLocationTempFolders => Path.Combine(DriveGameLocationTemp, DateTime.Now.ToString("yyyy"), DateTime.Now.ToString("MMMM dd"), BuildPlatform.ToString("G"));

	private static string FinalBuildLocation     => Path.Combine(DriveGameLocationFolders, $"{DateTime.Now.ToString("HH-mm-ss")}");
	private static string FinalBuildLocationTemp => Path.Combine(DriveGameLocationTempFolders, $"{DateTime.Now.ToString("HH-mm-ss")}");

	/*
	 * -- Data --
	 * 
	 * This is purely for storage of information that may be needed
	 * throughout this class. Such as the scenes to build or the path
	 * to put the final build.
	 */
	private static string      BuildLocation { get; set; }
	private static BuildTarget BuildPlatform { get; set; }

	private static string[] EnabledScenePaths => EditorBuildSettings.scenes
		.Where((scene) => scene.enabled == true)
		.Select((scene) => scene.path)
		.ToArray();

	private static BuildPlayerOptions CreatePlayerOptions(string buildName, BuildTarget platformName)
	{
		return new BuildPlayerOptions()
		{
			scenes = EnabledScenePaths,
			locationPathName = Path.Combine(BuildLocation, buildName),
			target  = platformName,
			options = GetPlatformBuildOptions(platformName)
		};
	}

	public static void SetupAndroidSdkPath()
	{
		EditorPrefs.SetString("AndroidSdkRoot", AndroidSdkDirectory);
	}

	public static void BuildPlatforms()
	{
		PlayerSettings.stripEngineCode = true;
		PlayerSettings.stripUnusedMeshComponents = true;

		foreach (var platform in PlatformToBuild)
		{
			if (platform.Value.WillBuildPlatform == true) 
			{
				BuildPlatform = platform.Key;
				BuildLocation = FinalBuildLocationTemp;

				string directoryTemp = FinalBuildLocationTemp;
				string directoryBuild = FinalBuildLocation;

				Directory.CreateDirectory(DriveGameLocationFolders);
				Directory.CreateDirectory(directoryTemp);

				platform.Value.PlatformBuildMethod();

				ZipFile.CreateFromDirectory(
					directoryTemp,
					directoryBuild + ".zip",
					CompressionLevel.Optimal,
					false
				);

				Directory.Delete(DriveBuildTempLocation, true);
			}
		}
	}

	private static void BuildWindows()
	{
		BuildPlayerOptions playerOptions = CreatePlayerOptions($"{GameName}.exe", BuildTarget.StandaloneWindows64);
		BuildPipeline.BuildPlayer(playerOptions);
	}

	private static void BuildLinux()
	{
		BuildPlayerOptions playerOptions = CreatePlayerOptions($"{GameName}", BuildTarget.StandaloneLinuxUniversal);
		BuildPipeline.BuildPlayer(playerOptions);
	}

	private static void BuildMacOS()
	{
		BuildPlayerOptions playerOptions = CreatePlayerOptions($"{GameName}", BuildTarget.StandaloneOSX);
		BuildPipeline.BuildPlayer(playerOptions);
	}

	private static void BuildAndroid()
	{
		SetupAndroidSdkPath();

		BuildPlayerOptions playerOptions = CreatePlayerOptions($"{GameName}.apk", BuildTarget.Android);
		BuildPipeline.BuildPlayer(playerOptions);
	}

	private static void BuildiOS()
	{
		BuildPlayerOptions playerOptions = CreatePlayerOptions($"{GameName}", BuildTarget.iOS);
		BuildPipeline.BuildPlayer(playerOptions);
	}

	private static void BuildWebGL()
	{
		BuildPlayerOptions playerOptions = CreatePlayerOptions($"{GameName}", BuildTarget.WebGL);
		BuildPipeline.BuildPlayer(playerOptions);
	}
}
