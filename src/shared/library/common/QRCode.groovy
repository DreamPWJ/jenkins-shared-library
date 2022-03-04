package shared.library.common


/**
 * @author 潘维吉
 * @date 2021/4/15 12:22
 * @email 406798106@qq.com
 * @description 二维码相关
 */
class QRCode implements Serializable {

    /**
     *  生成二维码
     *  libqrencode依赖库 https://github.com/fukuchi/libqrencode
     */
    static def generate(ctx, content, imageFile = "qrcode") {
        // Ubuntu apt-get install qrencode
        // CentOS yum install qrencode
        // MacOS  brew install qrencode
        try {
            ctx.env.PATH = "${ctx.env.PATH}:/opt/homebrew/bin:/usr/bin/qrencode" //添加了系统环境变量上
            try {
                // 判断服务器是是否安装qrencode环境
                ctx.sh "qrencode -V"
            } catch (error) {
                // 自动安装qrencode环境
                ctx.sh "apt-get install -y qrencode || true"
                ctx.sh "yum install -y qrencode || true"
                ctx.sh "brew install -y qrencode || true"
            }
        } catch (e) {
            ctx.println("二维码工具qrencode环境变量设置失败")
            ctx.println(e.getMessage())
        }
        ctx.sh " qrencode -l H -o ${imageFile}.png ${content} "
        return imageFile + ".png"
    }

    /**
     *  node生成二维码
     *  node-qrcode依赖库 https://github.com/soldair/node-qrcode
     */
    static def generateByNode(ctx, content, imageFile = "qrcode") {
        // npm install -g qrcode
        ctx.sh " qrcode -o ${imageFile}.png ${content} "
    }

}
