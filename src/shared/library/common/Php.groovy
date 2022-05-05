package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/11/09 13:22
 * @email 406798106@qq.com
 * @description PHP语言
 */
class Php implements Serializable {

    /**
     * 构建
     */
    static def build(ctx) {
        ctx.sh "php -v"
        // php语言无需提供制品包方式 直接使用源码部署
    }

}
