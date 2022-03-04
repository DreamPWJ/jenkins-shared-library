package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/9/18 13:22
 * @email 406798106@qq.com
 * @description Go语言
 */
class Go implements Serializable {

    /**
     * 构建
     */
    static def build(ctx) {
        // 初始化环境变量
        try {
            // 动态获取环境变量使用 which  命令响应结果
            def path = Utils.getShEchoResult(ctx, "which go")
            ctx.env.PATH = "${ctx.env.PATH}:${path}" //添加了系统环境变量上
        } catch (e) {
            println("初始化Go环境变量失败")
            println(e.getMessage())
        }
        ctx.sh " go version "

        // 依赖模块下载  如果是私有库需要设置凭证访问
        ctx.sh " go mod tidy "
        // 构建打包命令 go build [-o output] [-i] [build flags] [packages]
        // GOOS指定打包的目标操作系统 支持 linux、windows、darwin(为macos原始码操作系统)、freebsd(FreeBSD是一种类UNIX操作系统)
        // GOARCH指的是目标处理器的架构 arm arm64 386 amd64 ppc64 ppc64le mips64 mips64le s390x
        ctx.sh " env GOOS=linux GOARCH=amd64 go build -o main.go "

        // 直接执行 ./main.go 二进制文件名即可启动服务 不要安装任何环境
    }

    /**
     * 宿主机部署  非Docker方式
     */
    static def deploy(ctx) {
        try {
            // 部署前先停止服务
            def pId = Utils.getPIDByPort(this, "${ctx.SHELL_HOST_PORT}")
            ctx.sh " kill -9 ${pId} "
        } catch (e) {
            ctx.(e.getMessage())
        }
        // nohup 用于在系统后台不挂断地运行命令，不挂断指的是退出执行命令的终端也不会影响程序的运行
        // Go运行不需要任何语言环境  只需要将编译后的代码执行即可
        ctx.sh " ssh  ${ctx.remote.user}@${ctx.remote.host} 'cd /${ctx.DEPLOY_FOLDER}/${ctx.SHELL_PROJECT_NAME}-${ctx.SHELL_PROJECT_TYPE} " +
                " && chmod +x main.go && nohup ./main.go > ${ctx.env.JOB_NAME}.log 2>&1 & ' "
    }

    /**
     * 安装初始化环境
     */
    static def install(ctx) {
        ctx.sh " brew info go "
        ctx.sh " brew install go "
        ctx.sh " which go "
    }

}
