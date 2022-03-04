package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/3/2 13:22
 * @email 406798106@qq.com
 * @description 工具类
 */
class Tools implements Serializable {

    /**
     * 格式化颜色输出
     */
    static def printColor(ctx, value, color = "green") {
        def colors = ['red'  : "\033[40;31m ${value} \033[0m",
                      'blue' : "\033[47;34m ${value} \033[0m",
                      'green': "\033[40;32m ${value} \033[0m"]
        ctx.ansiColor('xterm') {
            ctx.println(colors[color])
        }
    }

    /**
     * 文件内容替换
     */
    static def replaceInFile(ctx, from, to, file) {
        ctx.sh("sed -i -e 's/$from/$to/g' $file")
    }

    /**
     * zip压缩文件
     */
    static def zipFile(ctx, String path, String fileName) {
        ctx.zip(dir: "${path}", glob: '', zipFile: "${fileName}")
    }
}
