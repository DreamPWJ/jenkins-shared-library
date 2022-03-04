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
     */
    static def mvndPackage(ctx) {
        ctx.sh "mvnd clean install -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.test.skip=true"
    }

    /**
     * Maven基于自定义setting文件方式打包
     */
    static def packageBySettingFile(ctx) {
        // Managed files自定义settings.xml方式 安装 Config File Provider插件
        /* ctx.configFileProvider([configFile(fileId: 'maven-settings', variable: 'MAVEN_SETTINGS')]) {
             ctx.sh "mvn -s $MAVEN_SETTINGS clean install -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.test.skip=true"
         }*/
        // -s settings.xml文件路径
        def settingsFile = "${ctx.env.WORKSPACE}/ci/_jenkins/maven/${ctx.MAVEN_SETTING_XML}"
        ctx.sh "mvn -s ${settingsFile} clean install -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.test.skip=true"
    }

    /**
     * 上传Maven仓库
     */
    static def uploadWarehouse(ctx) {
        // 推送
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
