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
     * GraalVM原生镜像构建
     */
    static def springNative(ctx, map, mavenCommandType, isMavenTest) {
        // Spring Boot 3 以后的 AOT 引擎可自动生成大部分反射和资源加载配置  初始化生成反射配置  检查日志中的缺失类，手动添加到 reflect-config.json
        // 仅生成反射配置（不编译镜像）
        ctx.sh "${mavenCommandType} spring-boot:process-aot"
        def springNativeBuildParams = " -Pnative " // spring-boot:build-image
        // 可以使用mvnd守护进程加速构建
        if ("${ctx.IS_MAVEN_SINGLE_MODULE}" == 'true') {
            ctx.sh "${mavenCommandType} clean package -T 2C -Dmaven.compile.fork=true ${isMavenTest} ${springNativeBuildParams} "
        } else { // 多模块情况
            ctx.sh "${mavenCommandType} clean package -T 2C -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.compile.fork=true ${isMavenTest} ${springNativeBuildParams} "
        }
    }

    /**
     * Maven基于自定义setting文件方式打包
     */
    static def packageBySettingFile(ctx, map, mavenCommandType, isMavenTest) {
        // 自定义私有库settings.xml方式 使用Secret file凭据存储
        ctx.withCredentials([ctx.file(credentialsId: "${map.maven_settings_xml_id}", variable: 'MAVEN_SETTINGS')]) {
            def data = ctx.readFile(file: "${ctx.MAVEN_SETTINGS}")
            def fileName = "settings.xml"
            // 使用 Groovy 代码写入文件
            ctx.writeFile file: fileName, text: data
            ctx.sh "${mavenCommandType} clean install -T 2C -s $fileName -pl ${ctx.MAVEN_ONE_LEVEL}${ctx.PROJECT_NAME} -am -Dmaven.compile.fork=true  ${isMavenTest} "
        }

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
