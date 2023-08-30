package shared.library.common

import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2022/1/9 13:22
 * @email 406798106@qq.com
 * @description Terraform基础设施即代码
 * 使用 Terraform 在任何云上实现基础设施自动化， 基础设施自动化，用于在任何云或数据中心中配置和管理资源
 */
class Terraform implements Serializable {

    /**
     * 初始化
     */
    static def init(ctx) {
        ctx.sh " "
    }

}
