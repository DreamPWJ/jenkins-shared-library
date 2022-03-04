package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/11/09 13:22
 * @email 406798106@qq.com
 * @description DotNet框架语言
 */
class DotNet implements Serializable {

    /**
     * 构建
     */
    static def build(ctx) {
        ctx.sh "dotnet run -p dotnet-demo --   \"Hello from the bot\""
        ctx.sh "dotnet build -r ubuntu.20.04-x64 dotnet-demo"
    }

}
