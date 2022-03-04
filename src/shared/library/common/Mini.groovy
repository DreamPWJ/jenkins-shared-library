package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/12/21 13:22
 * @email 406798106@qq.com
 * @description 小程序相关
 */
class Mini implements Serializable {

    /**
     * 小程序提交审核后创建定时检测任务 审核状态的变化后通知
     */
    static def miniCheckState(ctx) {
        ctx.sh ""
    }


}
