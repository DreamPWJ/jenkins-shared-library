package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/7/9 13:22
 * @email 406798106@qq.com
 * @description Unity引擎
 */
class Unity implements Serializable {

    static def sshCommand = "root@172.16.1.55"
    static def jenkinsWorkspace = "/my/jenkins/workspace/"

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        try {
            // 动态获取环境变量使用 which  命令响应结果
            def path = Utils.getShEchoResult(ctx, "which dotnet")
            ctx.env.PATH = "${ctx.env.PATH}:${path}" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化dotnet环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     * 打包构建
     */
    static def build(ctx, buildTarget) {
        // Docker构建Unity环境: https://github.com/game-ci/docker   https://game.ci/docs/introduction/getting-started
        // 禁用Unity自动化构建双重认证: https://id.unity.com/zh/settings/tfa 个人版无需serial激活许可证
        // Unity环境版本下载: https://unity.cn/releases
        // 命令文档: https://docs.unity3d.com/Manual/CommandLineArguments.html
        // 打包目标平台: Standalone, Win, Win64, Linux64, iOS, Android, WebGL, XboxOne, PS4, Switch, tvOS

        // 动态传参给Unity脚本
        def androidParams = ""
        def iosParams = ""
        if (buildTarget == "Android") {
            def isDebug = "${ctx.androidBuildType}".contains("Release") ? false : true
            androidParams = "Debug=${isDebug}"
        } else if (buildTarget == "iOS") {
            iosParams = "Identifier=${ctx.iosAppIdentifier}"
        }

        def unityPath = " xvfb-run --auto-servernum --server-args='-screen 0 640x480x24' unity-editor "
        // 执行构建命令
        ctx.sh "${unityPath} -projectPath \$(pwd) -batchmode -nographics -quit " +
                " -buildTarget ${buildTarget} -executeMethod ProjectBuild.BuildFor${buildTarget} ${androidParams} ${iosParams} " +
                " -username 406798106@qq.com -password Aa18863302302  -logFile /dev/stdout "
        // " -logFile \$(pwd)/unity.log "
        ctx.println("执行Unity For " + buildTarget + "构建命令成功")
    }

    /**
     * 同步打包执行的构建文件
     */
    static def syncBuildFile(ctx) {
        ctx.sh "rm -rf ./Assets/Editor/ "
        ctx.sh "cp -r ${ctx.jenkinsConfigDir}/unity/Editor/ ./Assets/Editor/ "
        // 解决'ZipFile' does not exist等
        ctx.sh "cp -r ${ctx.jenkinsConfigDir}/unity/csc.rsp ./Assets/ "
    }

    /**
     * 从远程服务器拉取编译后的代码
     */
    static def pullCodeFromRemote(ctx) {
        // 拉取Xcode工程代码到MacOS系统 用Xcode执行打包
        // apt install sshpass  可以带密码
        ctx.sh "scp -r ${sshCommand}:${jenkinsWorkspace}${ctx.env.JOB_NAME}/ios/*/** ${ctx.env.WORKSPACE}"
        ctx.sh "scp -r ${sshCommand}:${jenkinsWorkspace}${ctx.env.JOB_NAME}/ci/ ${ctx.env.WORKSPACE}"
    }

    /**
     * 推送打包产物到远程服务器
     */
    static def pushPackageToRemote(ctx) {
        ctx.sh "scp -r ${ctx.env.WORKSPACE}/${ctx.iosPackagesOutputDir} ${sshCommand}:${jenkinsWorkspace}${ctx.env.JOB_NAME}/"
    }

    /**
     * 授权许可协议激活
     */
    static def license(ctx) {
        ctx.sh "cp -r ${ctx.jenkinsConfigDir}/unity/${ctx.unityActivationFile} ./ "
        ctx.sh """
       export UNITY_LICENSE='`cat ${ctx.unityActivationFile}`'
     """
    }

}
