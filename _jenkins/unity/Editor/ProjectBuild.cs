using System.Collections;
using System.IO;
using UnityEditor;
using UnityEngine;
using System.Collections.Generic;
using System;
using UnityEditor.Build.Reporting;

// Author: 潘维吉
// Description: 自定义核心Unity跨平台编译打包

class ProjectBuild : Editor
{
    // 是否是调试模式构建
    private static bool isDebugBuild = true;

    // 找出当前工程所有的场景文件，假设你只想把部分的scene文件打包 那么这里可以写你的条件判断 总之返回一个字符串数组。
    static string[] GetBuildScenes()
    {
        List<string> names = new List<string>();
        foreach (EditorBuildSettingsScene e in EditorBuildSettings.scenes)
        {
            if (e == null)
                continue;
            if (e.enabled)
                names.Add(e.path);
        }
        return names.ToArray();
    }

    /// <summary>
    /// 自定义工程名："project-"作为工程名的前缀参数
    /// </summary>
    public static string projectName
    {
        get
        {
            foreach (string arg in System.Environment.GetCommandLineArgs())
            {
                if (arg.StartsWith("project"))
                {
                    return arg.Split("-"[0])[1];
                }
            }
            return Application.productName;
        }
    }

   // 获取命令行-executeMethod后动态参数
    private static BuildSetting GetCommandArgs()
    {
        string[] args = System.Environment.GetCommandLineArgs();
        BuildSetting buildSetting = new BuildSetting();
        foreach(string arg in args)
        {
                    if (arg.StartsWith("Name"))
                      {
                          var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                          if (tempParam.Length == 2)
                          {
                              buildSetting.Name = tempParam[1].Trim();
                          }
                      }
                    else if (arg.StartsWith("Version"))
                    {
                        var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                        if (tempParam.Length == 2)
                        {
                            Debug.Log("动态参数: Version=" + tempParam[1]);
                            buildSetting.Version = tempParam[1].Trim();
                        }
                    }
                    else if (arg.StartsWith("Build"))
                    {
                        var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                        if (tempParam.Length == 2)
                        {
                            buildSetting.Build = tempParam[1].Trim();
                        }
                    }
                    else if (arg.StartsWith("Identifier"))
                    {
                        var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                        if (tempParam.Length == 2)
                        {
                            Debug.Log("动态参数: Identifier=" + tempParam[1]);
                            buildSetting.Identifier = tempParam[1].Trim();
                        }
                    }
                    else if (arg.StartsWith("Debug"))
                    {
                        var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                        if (tempParam.Length == 2)
                        {
                            Debug.Log("动态参数: Debug=" + tempParam[1]);
                            bool.TryParse(tempParam[1], out buildSetting.Debug);
                        }
                    }
                   else if (arg.StartsWith("Place"))
                     {
                         var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                         if (tempParam.Length == 2)
                         {
                             buildSetting.Place = (Place)Enum.Parse(typeof(Place), tempParam[1], true);
                         }
                     }
                    else if (arg.StartsWith("MTRendering"))
                    {
                        var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                        if (tempParam.Length == 2)
                        {
                            bool.TryParse(tempParam[1], out buildSetting.MTRendering);
                        }
                    }
                    else if (arg.StartsWith("IL2CPP"))
                    {
                        var tempParam = arg.Split(new string[] { "=" }, StringSplitOptions.RemoveEmptyEntries);
                        if (tempParam.Length == 2)
                        {
                            bool.TryParse(tempParam[1], out buildSetting.IL2CPP);
                        }
                    }
                }
                return buildSetting;
        }


    // 打包Android应用
    static void BuildForAndroid()
    {
       // 获取命令行-executeMethod后动态参数
       BuildSetting buildSetting = GetCommandArgs();
       // 设置Android打包
       string suffix = SetAndroidSetting(buildSetting);

        // APK路径、名字配置
        string date = DateTime.UtcNow.ToString("yyyy-MM-dd_HH:mm");
        string path = Application.dataPath.Replace("/Assets", "") + "/android/" + projectName + suffix + "_" + date + ".apk";
        BuildReport report = BuildPipeline.BuildPlayer(GetBuildScenes(), path, BuildTarget.Android, BuildOptions.None);

        BuildSummary summary = report.summary;
           if (summary.result == BuildResult.Succeeded)
           {
               Debug.Log("Unity For Android打包完成 包路径: " + summary.outputPath + " , 大小: " + summary.totalSize + " bytes");
           }
           if (summary.result == BuildResult.Failed)
           {
               Debug.Log("Unity For Android打包失败 ❌");
           }
    }

    // 打包iOS应用
    static void BuildForiOS()
    {
         // 获取命令行-executeMethod后动态参数
         BuildSetting buildSetting = GetCommandArgs();
        // 设置应用名称与版本号
        // PlayerSettings.productName = "";
        // PlayerSettings.bundleVersion = "";

        if (!string.IsNullOrEmpty(buildSetting.Identifier)){
               // 在iOS和Android平台之间共享的应用程序包标识符
              PlayerSettings.applicationIdentifier = buildSetting.Identifier;
        }

        string path = Application.dataPath.Replace("/Assets", "") + "/ios/" + projectName;
    	BuildPipeline.BuildPlayer(GetBuildScenes(), path, BuildTarget.iOS, BuildOptions.None);
    }

    // 打包WebGL应用
    static void BuildForWebGL()
    {
        // 获取命令行-executeMethod后动态参数
        BuildSetting buildSetting = GetCommandArgs();

        string path = Application.dataPath.Replace("/Assets", "") + "/webgl/" + projectName;
    	BuildPipeline.BuildPlayer(GetBuildScenes(), path, BuildTarget.WebGL, BuildOptions.None);
    }

    // 打包Windows应用
    static void BuildForWin64()
    {
        // 获取命令行-executeMethod后动态参数
        BuildSetting buildSetting = GetCommandArgs();

        string path = Application.dataPath.Replace("/Assets", "") + "/windows/" + projectName+ ".exe";
    	BuildPipeline.BuildPlayer(GetBuildScenes(), path, BuildTarget.StandaloneWindows64, BuildOptions.None);
    }

    // 打包MacOS应用
	 static void BuildForMacOS()
	{
	  // 获取命令行-executeMethod后动态参数
       BuildSetting buildSetting = GetCommandArgs();

       string path = Application.dataPath.Replace("/Assets", "") + "/macos/" + projectName;
       BuildPipeline.BuildPlayer(GetBuildScenes(), path, BuildTarget.StandaloneOSX, BuildOptions.None);
	}

	 // 打包Linux应用
	 static void BuildForLinux()
	{
	  // 获取命令行-executeMethod后动态参数
       BuildSetting buildSetting = GetCommandArgs();

       string path = Application.dataPath.Replace("/Assets", "") + "/linux/" + projectName;
       BuildPipeline.BuildPlayer(GetBuildScenes(), path, BuildTarget.StandaloneLinuxUniversal, BuildOptions.None);
	}


    // 设置Android打包
    static string SetAndroidSetting(BuildSetting setting)
    {
        string suffix = "_";

        if (!string.IsNullOrEmpty(setting.Name))
        {
            PlayerSettings.productName = setting.Name;
            //PlayerSettings.applicationIdentifier = "com.xxx." + setting.Name;
        }
        if (!string.IsNullOrEmpty(setting.Version))
        {
            PlayerSettings.bundleVersion = setting.Version;
            suffix += setting.Version;
        }
        else
        {
             suffix += PlayerSettings.bundleVersion;
        }
        if (!string.IsNullOrEmpty(setting.Build))
        {
            PlayerSettings.Android.bundleVersionCode = int.Parse(setting.Build);
            suffix += "_" + setting.Build;
        }
        if (!string.IsNullOrEmpty(setting.Identifier))
         {
             PlayerSettings.applicationIdentifier = setting.Identifier;
             suffix += "_" + setting.Identifier;
         }
        if (setting.Place != Place.None)
        {
            //代表了渠道包
            string symbol = PlayerSettings.GetScriptingDefineSymbolsForGroup(BuildTargetGroup.Android);
            PlayerSettings.SetScriptingDefineSymbolsForGroup(BuildTargetGroup.Android, symbol + ";" + setting.Place.ToString());
            suffix += setting.Place.ToString();
        }
        if (setting.MTRendering)
        {
            //多线程渲染
            PlayerSettings.MTRendering = true;
            suffix += "_MTRendering";
        }
        else
        {
            PlayerSettings.MTRendering = false;
        }
        if (setting.IL2CPP)
        {
            //是否IL2CPP 程序运行效率提升
            PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.IL2CPP);
            suffix += "_IL2CPP";
        }
        else
        {
            PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android, ScriptingImplementation.Mono2x);
        }
        if (setting.Debug)
        {
            // EditorUserBuildSettings参数文档: https://docs.unity3d.com/ScriptReference/EditorUserBuildSettings.html
            EditorUserBuildSettings.development = true;
            EditorUserBuildSettings.allowDebugging = true;
            //EditorUserBuildSettings.connectProfiler = true;
            suffix += "_Debug";
        }
        else
        {
            EditorUserBuildSettings.development = false;
        }
        Debug.Log("Android包名后缀组合: suffix=" + suffix);
        return suffix;

        // 签名文件配置，若不配置，则使用Unity默认签名
        // PlayerSettings.Android.keyaliasName = "";
        // PlayerSettings.Android.keyaliasPass = "";
        // PlayerSettings.Android.keystoreName = Application.dataPath.Replace("/Assets", "") + "/xxx.jks";
        // PlayerSettings.Android.keystorePass = "";
        // 开启Android新包格式aab
        // EditorUserBuildSettings.buildAppBundle = true;
        // 打包环境类型
     /*
     BuildOptions buildOption = BuildOptions.None;
        if (isDebugBuild)
        {
            buildOption |= BuildOptions.Development;
            buildOption |= BuildOptions.AllowDebugging;
            buildOption |= BuildOptions.ConnectWithProfiler;
        }
        else
        {
            buildOption |= BuildOptions.None;
        }*/
     }

}
