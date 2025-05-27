package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/8/9 13:22
 * @email 406798106@qq.com
 * @description Maven相关的功能
 */
class Maven implements Serializable {

    /**
     * 更快的构建工具mvnd 多个的守护进程来服务构建请求来达到并行构建的效果  源码: https://github.com/apache/maven-mvnd
     * 多核cpu构建速度比较明显快 核数少构建速度差距不大
     */
    static def mvndPackage(ctx, isMavenTest) {
        ctx.sh "mvnd --version"
        ctx.sh "mvnd clean install -Dquickly -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am ${isMavenTest}"
    }

    /**
     * Maven基于自定义setting文件方式打包
     */
    static def packageBySettingFile(ctx, mavenCommandType, isMavenTest, springNativeBuildParams) {
        // Managed files自定义settings.xml方式 安装 Config File Provider插件
        /* ctx.configFileProvider([configFile(fileId: 'maven-settings', variable: 'MAVEN_SETTINGS')]) {
             ctx.sh "${mavenCommandType} clean install -T 2C -s $MAVEN_SETTINGS -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.compile.fork=true  ${isMavenTest} ${springNativeBuildParams}"
         }*/

        // -s settings.xml文件路径  -T 1C 参数，表示每个CPU核心跑一个工程并行构建
        def settingsFile = "${ctx.env.WORKSPACE}/ci/_jenkins/maven/${ctx.MAVEN_SETTING_XML}"
        ctx.sh "${mavenCommandType} clean install -T 2C -s ${settingsFile} -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.compile.fork=true  ${isMavenTest} ${springNativeBuildParams}"
    }

    /**
     * 打包时混淆可读性 防止反编译
     */
    static def obfuscation(ctx) {
        // 代码可读性混淆proguard  地址: https://github.com/Guardsquare/proguard
        ctx.sh "bin/proguard.sh <options...>"
    }

    /**
     * Maven打包时重命名
     */
    static def renameBuildPom(ctx) {
        // 思路是复制原来的pom.xml 成 pom-copy.xml 在pom-copy.xml 里 改项目名
        // mvn clean package -f pom-copy.xml
        // ctx.sh " "
        // 读取pom.xml信息
        def pom = ctx.readMavenPom(file: 'pom.xml')
        def pomVersion = pom.version
        // 写入pom.xml信息
        pom.name = "panweiji"
        ctx.writeMavenPom model: pom
        ctx.println(pomVersion)
        ctx.println(ctx.readMavenPom(file: 'pom.xml'))
    }

    /**
     * 上传Maven仓库
     */
    static def uploadWarehouse(ctx) {
        // 推送包到远程仓库
        ctx.sh "mvn clean install org.apache.maven.plugins:maven-deploy-plugin:2.8:deploy -DskipTests"
    }

    /**
     * jar转换成exe执行文件
     */
    static def jar2exe(ctx) {
        // 官网: http://launch4j.sourceforge.net/
        // launch4j插件 https://github.com/lukaszlenart/launch4j-maven-plugin
        ctx.sh ""
    }

    /**
     * 获取pom文件信息
     */
    static def getPomInfo(ctx) {
        // 使用Pipeline Utility Steps插件从pom.xml读取信息到环境变量
        def artifactId = ctx.readMavenPom().getArtifactId()
        def version = ctx.readMavenPom().getVersion()
        return version
    }

}
