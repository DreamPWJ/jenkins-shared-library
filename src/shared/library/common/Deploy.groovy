package shared.library.common
/**
 * @author 潘维吉
 * @date 2021/1/26 13:22
 * @email 406798106@qq.com
 * @description 部署相关
 */
class Deploy implements Serializable {

    /**
     * 执行
     */
    static def execute(ctx) {
        ctx.sh ""
    }

}
