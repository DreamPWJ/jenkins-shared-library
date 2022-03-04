package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/9/18 13:22
 * @email 406798106@qq.com
 * @description Python语言
 */
class Python implements Serializable {

    /**
     * 构建
     */
    static def build(ctx) {
        // pip3 install pyinstaller 、 pip3 install flask 、 apt install -y upx
        // 初始化环境变量
        try {
            // 动态获取环境变量使用 which  命令响应结果
            def path = Utils.getShEchoResult(ctx, "which pyinstaller")
            ctx.env.PATH = "${ctx.env.PATH}:${path}" //添加了系统环境变量上
        } catch (e) {
            println("初始化Python环境变量失败")
            println(e.getMessage())
        }

        ctx.sh " python3 --version "
        ctx.sh " rm -rf build && rm -rf dist "
        // requirements.txt 可以保证项目依赖包版本的确定性, 不会因为依赖更新而导致异常产生，通常可以由 pip 来生成
        def requirementsFile = "requirements.txt"
        if (ctx.fileExists(requirementsFile)) {
            ctx.sh " pip3 install -r $requirementsFile "
        }
        // PyInstaller自身支持跨平台 来编译打包 Python 项目 , 但是，它不是一个交叉编译器 操作系统不互通
        // 注意!!! linux下打包只能在linux下运行，windows下打包只能在windows下运行，macos下打包只能在macos下运行 生成的可执行文件再dist目录下，可执行文件的名字与py文件名一致
        // 生成了两个新目录: build 和 dist
        // dist 目录下生成了一个可执行程序 app 执行(运行不再需python环境)  包服务启动 ./app  开发启动 python3 app.py
        // -F 打包一个单个文件
        ctx.sh " pyinstaller -F app.py "
    }


    /**
     * 安装初始化环境
     */
    static def install(ctx) {
        ctx.sh " pip3 install pyinstaller  "
        ctx.sh " which pyinstaller "
    }

}
