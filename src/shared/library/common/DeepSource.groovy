package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/11/03 13:22
 * @email 406798106@qq.com
 * @description DeepSource代码质量分析
 */
class DeepSource implements Serializable {

    /**
     * 分析
     * 文档: https://deepsource.io/docs/cli/usage
     */
    static def analyse(ctx) {
        // CLI 应该从具有.deepsource.toml
        def deepsourceFile = "${ctx.env.WORKSPACE}/ci/_jenkins/deepsource/.deepsource.toml"
        ctx.sh "export DEEPSOURCE_DSN=${deepsourceFile}"
        // 执行命令
        ctx.sh "./bin/deepsource auth" // 授权
        ctx.sh "./bin/deepsource report" // 向 DeepSource 报告工件
    }

    /**
     * 安装CLI
     */
    static def install(ctx) {
        // 二进制安装  二进制文件放入./bin/deepsource
        ctx.sh "curl https://deepsource.io/cli | sh"
        // ctx.sh "brew install deepsourcelabs/cli/deepsource"
    }
    
}
