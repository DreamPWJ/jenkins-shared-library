// Author: 潘维吉
// Description: Unity构建参数设置

public class BuildSetting
{
    //程序名称
    public string Name = "";
    //版本号
    public string Version = "";
    //build次数
    public string Build = "";
    //程序唯一标识
    public string Identifier = "";
    //是否debug
    public bool Debug = true;
    //渠道
    public Place Place = Place.None;
    //多线程渲染
    public bool MTRendering = true;
    //是否IL2CPP引擎 程序运行效率提升 或者Mono引擎
    public bool IL2CPP = true;
    //是否开启动态合批
    public bool DynamicBatching = false;
}

// 多渠道
public enum Place
{
    None =0,
    Huawei,
    Xiaomi,
    Oppo,
    Vivo,
    Meizu,
    Sansumg,
    Weixin,
}
