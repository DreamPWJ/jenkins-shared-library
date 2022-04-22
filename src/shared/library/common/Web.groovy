package shared.library.common

import shared.library.Utils
import shared.library.common.Git

/**
 * @author 潘维吉
 * @date 2021/9/26 13:22
 * @email 406798106@qq.com
 * @description Web相关的方法
 */
class Web implements Serializable {

    /**
     * 初始化monorepo仓库依赖环境
     */
    static def initMonoRepoEnv(ctx) {
        try {
            ctx.sh "lerna --version"
        } catch (error) {
            // npm一般在root用户权限下
            ctx.sh "npm i -g lerna --unsafe-perm=true --allow-root"
        }
        try {
            ctx.sh "yarn --version"
        } catch (error) {
            ctx.sh "npm install -g yarn" // 动态配置或固定yarn版本号 防止版本变化兼容性问题
        }
        // yarn镜像源
        // ctx.sh "yarn config set registry https://registry.npm.taobao.org"
        try {
            ctx.sh "dotenv --version"
        } catch (error) {
            ctx.sh "yarn global add dotenv-cli"
        }
/*        try {
            ctx.sh "rimraf"
        } catch (error) {
            ctx.sh "yarn global add rimraf"
        }*/
    }

    /**
     * 基于Lerna管理的Monorepo仓库打包
     */
    static def monorepoBuild(ctx) {
        // 初始化monorepo仓库依赖环境
        initMonoRepoEnv(ctx)
        ctx.timeout(time: 20, unit: 'MINUTES') {
            // ctx.sh " lerna clean -y "
            try {
                ctx.sh " npm run clean:ts "
                ctx.sh " cd ${ctx.monoRepoProjectDir} && rm -rf *umi* "
            } catch (error) {
            }

            ctx.println("执行Monorepo仓库构建 🏗️  ")
            if (Git.isExistsChangeFile(ctx)) { // 自动判断是否需要下载依赖 可新增动态参数用于强制下载依赖情况
                // 全部下载依赖 更通用 bootstrap不仅是下载依赖资源 还建立多包之间的依赖软链
                // Turborepo解决Monorepo多项目构建缓慢问题 充分利用CPU性能并发构建提速
                ctx.sh "lerna bootstrap"
                // lerna bootstrap指定作用域 加速下载依赖  --scope 限制 lerna bootstrap 在哪些包起作用 包的package.json文件中名称
                // ctx.sh "lerna bootstrap --include-dependents --include-dependencies --scope ${ctx.PROJECT_NAME}"
            }
            // 执行基础通用包编译和自定义脚本处理工作  会有无效的包被编译 后期优化
            ctx.sh "npm run build:all"
            // 定位到具体业务包执行构建打包命令
            ctx.sh "cd ${ctx.monoRepoProjectDir} && npm run ${ctx.NPM_RUN_PARAMS}" // >/dev/null 2>&1
        }
    }

    /**
     * 静态web项目打包目录结构定义和压缩处理
     */
    static def staticResourceBuild(ctx) {
        ctx.sh "rm -rf ${ctx.NPM_PACKAGE_FOLDER} && rm -f ${ctx.NPM_PACKAGE_FOLDER}.tar.gz"
        // 压缩当前文件夹下非隐藏文件的所有文件 --exclude排除
        ctx.sh "tar -zcvf  ${ctx.NPM_PACKAGE_FOLDER}.tar.gz  --exclude .git  --exclude ci --exclude ci@tmp  * "
        // 移动文件夹下的多个文件到另一个文件夹
        ctx.sh "mkdir -p ${ctx.NPM_PACKAGE_FOLDER} && mv ${ctx.NPM_PACKAGE_FOLDER}.tar.gz ${ctx.NPM_PACKAGE_FOLDER}"
        def currentDir = Utils.getShEchoResult(ctx, "pwd")
        ctx.sh "cd ${currentDir}/${ctx.NPM_PACKAGE_FOLDER} && tar -xzvf ${ctx.NPM_PACKAGE_FOLDER}.tar.gz  && rm -f ${ctx.NPM_PACKAGE_FOLDER}.tar.gz"
    }

    /**
     * 是否需要css预处理器sass处理
     */
    static def needSass(ctx) {
        // node-sass已经不被官方推荐使用了，可以考虑换成sass !!!
        // 判断sass是否安装 加速构建速度
        // def npmListInfo = Utils.getShEchoResult(this, "npm list node-sass")
        // if (npmListInfo.toString().contains("empty")) {
        try {
            ctx.retry(2) {
                if ("${ctx.NODE_VERSION}" == "Node15") {
                    // sass5.0更高版本需要node15支持  --unsafe-perm解决权限不够
                    ctx.sh "npm install node-sass@latest --unsafe-perm=true --allow-root"
                } else {
                    ctx.sh "npm install node-sass@4.14.1 --unsafe-perm=true --allow-root" // 固定版本号
                }
            }
        } catch (e) {
            ctx.println(e.getMessage())
            ctx.sh "npm rebuild node-sass"
        }
    }

}
