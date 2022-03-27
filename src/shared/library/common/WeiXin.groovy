package shared.library.common

/**
 * @author 潘维吉
 * @date 2022/3/27 13:22
 * @email 406798106@qq.com
 * @description 微信相关
 */
class WeiXin implements Serializable {

    /**
     * 企业微信群通知
     * 文档: https://developer.work.weixin.qq.com/document/path/91770
     */
    static def notice(ctx, credentialsId, title, content, mobile = "") {
        ctx.sh "curl 'https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=693axxx6-7aoc-4bc4-97a0-0ec2sifa5aaa' \\\n" +
                "   -H 'Content-Type: application/json' \\\n" +
                "   -d '\n" +
                "   {\n" +
                "    \t\"msgtype\": \"text\",\n" +
                "    \t\"text\": {\n" +
                "        \t\"content\": \"Hello World\"\n" +
                "    \t}\n" +
                "   }'"
    }


}
