package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/3/3 13:22
 * @email 406798106@qq.com
 * @description Java语言相关
 * 动态切换JDK版本
 */
class Java implements Serializable {

    /**
     * jenv方式切换JDK版本 满足多版本jdk同一时间同时使用需求
     * Maven内JDK版本跟随jenv切换 在用户目录下新建 ~/.mavenrc文件 必须是.mavenrc   source ~/.mavenrc
     * 内容是: JAVA_HOME=$(/usr/libexec/java_home -v $(jenv version-name))
     */
    static def switchJDKByJenv(ctx, version = 11, scope = "local") {
        version = version as int
        if (version <= 10) {
            version = "1." + version
        }
        try {
            ctx.env.PATH = "${ctx.env.PATH}:/opt/homebrew/bin" //添加了系统环境变量上
            // 动态切换jdk版本 shell/local/global
            ctx.println("多JDK版本并行情况 动态切换JDK版本")
            try {
                // 判断服务器是是否安装jenv环境
                ctx.sh "jenv --version"
            } catch (error) {
                // 自动安装jenv环境
                ctx.sh "brew install -y jenv || true"
            }
            ctx.sh """ 
                      jenv ${scope} ${version}
                      java -version
                      """
        } catch (e) {
            ctx.println("jenv方式切换JDK版本失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     * 别名方式切换JDK版本
     */
    static def switchJDKByAlias(ctx) {
        try {
            // 动态切换jdk版本
            ctx.sh """ 
                      jdk8
                      java -version
                      """
        } catch (e) {
            ctx.println("别名方式切换JDK版本失败")
            ctx.println(e.getMessage())
        }
    }


}
