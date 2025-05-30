package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/4/16 13:22
 * @email 406798106@qq.com
 * @description Node语言相关
 * 动态切换Node版本
 */
class Node implements Serializable {

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        try {
            def nodePath = ctx.tool "${ctx.NODE_VERSION}" //全局配置里
            //ctx.println("Node环境变量:" + nodePath)
            ctx.env.PATH = "${ctx.env.PATH}:${nodePath}" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化Node环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     * Node环境设置镜像并初始化包管理工具 如npm、yarn、pnpm
     */
    static def setMirror(ctx) {
        // 每次流水线执行都将执行 会产生无效的浪费  后面优化一下
        ctx.sh "node -v && npm -v"  // node和npm版本信息
        if (true) { // 内置镜像已经初始化设置 无需重复设置 减少流水线构建时间
            return
        }
        def npmMirror = "" // NPM镜像
        if (false) { // 是否使用官方镜像
            npmMirror = "https://registry.npmjs.org" // 官方镜像
        } else { // 其他国内镜像加速下载
            npmMirror = "https://registry.npmmirror.com" // 新淘宝镜像
        }
        // 设置镜像源 加速下载
        ctx.sh "npm config set registry ${npmMirror}"

        try {
            ctx.sh "pnpm --version"
        } catch (error) {
            ctx.sh "npm install -g pnpm || true"
        }

        try {
            ctx.sh "yarn --version"
        } catch (error) {
            ctx.sh "npm install -g yarn || true" // 动态配置或固定yarn版本号 防止版本变化兼容性问题
        }

        // 设置镜像源 加速下载
        ctx.sh "pnpm config set registry ${npmMirror} || true"
        ctx.sh "yarn config set registry ${npmMirror} || true"
    }

    /**
     * 设置官方镜像
     */
    static def setOfficialMirror(ctx) {
        // 设置镜官方像源  因为国内镜像有些包404不存在导致安装依赖失败 关键字 ERR_PNPM_FETCH_404 或 Not Found - 404
        // 获取工作空间中文件的内容
        def fileContent = ctx.readFile 'npm_install.log'
        // 检查文件内容是否包含特定关键字
        if (fileContent.contains('ERR_PNPM_FETCH_404') || fileContent.contains('Not Found - 404')
                || fileContent.contains('No matching version found') || fileContent.contains('error Couldn\'t find any versions')) {
            def npmMirror = "https://registry.npmjs.org" // 官方镜像
            ctx.sh "npm config set registry ${npmMirror}"
            ctx.sh "yarn config set registry ${npmMirror}"
            ctx.sh "pnpm config set registry ${npmMirror} || true"
        }
    }

    /**
     * Node环境设置Electron镜像并初始化包管理工具 如yarn、pnpm
     */
    static def setElectronMirror(ctx) {
        // 每次流水线执行都将执行 会产生无效的浪费  后面优化一下
        ctx.sh "node -v && npm -v"  // node和npm版本信息
        try {
            ctx.sh "yarn --version"
        } catch (error) {
            ctx.sh "npm install -g yarn"  // 动态配置或固定yarn版本号 防止版本变化兼容性问题
        }
        def npmMirror = "" // NPM镜像
        if (false) { // 是否使用官方镜像
            npmMirror = "https://registry.npmjs.org" // 官方镜像
        } else { // 其他国内镜像加速下载
            npmMirror = "https://registry.npmmirror.com" // 新淘宝镜像
        }

        ctx.sh "npm config set registry ${npmMirror}"
        ctx.sh "yarn config set registry ${npmMirror}"
        ctx.sh "npm config set electron_mirror ${npmMirror}/electron/"
        ctx.sh "yarn config set electron_mirror ${npmMirror}/electron/"
        // ctx.sh "yarn config set electron_mirror https://mirrors.huaweicloud.com/electron/"
        // ctx.sh "npm config set registry  https://mirrors.huaweicloud.com/repository/npm"
    }

    /**
     * Mac通过nvm安装多版本node
     */
    static def change(ctx, version = 16) {
/*
        mkdir ~/.nvm
        ~/.zshrc

       export NVM_DIR="$HOME/.nvm"
        [ -s "/opt/homebrew/opt/nvm/nvm.sh" ] && . "/opt/homebrew/opt/nvm/nvm.sh"  # This loads nvm
        [ -s "/opt/homebrew/opt/nvm/etc/bash_completion.d/nvm" ] && . "/opt/homebrew/opt/nvm/etc/bash_completion.d/nvm"  # This loads nvm bash_completion
*/

        /* brew install nvm
         nvm ls -remote 查看 所有的node可用版本
         nvm install xxx 下载你想要的版本
         nvm use xxx 使用指定版本的node
         nvm alias default xxx 每次启动终端都使用该版本的node */

        try {
            ctx.env.PATH = "${ctx.env.PATH}:/Users/$ctx.USER/.nvm/" //添加了系统环境变量上
            ctx.sh """ 
                      nvm use ${version}
                      node -v
                      """
        } catch (e) {
            ctx.println("切换Node版本失败: ${ctx.env.PATH}")
            ctx.println(e.getMessage())
        }
    }

    /**
     * 上传npm仓库
     */
    static def uploadWarehouse(ctx) {
        // 设置为代理仓库
        ctx.sh "npm config set registry=https://packages.aliyun.com/npm/npm-registry/"
        // 登陆 npm 仓库
        ctx.sh "npm login"
        // 推送
        ctx.sh "npm publish"
    }

}
