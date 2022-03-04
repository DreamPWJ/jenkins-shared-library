package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/11/03 13:22
 * @email 406798106@qq.com
 * @description Helm是Kubernetes的包管理工具 类似Node的npm
 * 将K8s中的应用以及依赖服务以包(Chart)的形式组织管理
 */
class Helm implements Serializable {

    /**
     * 下载包
     */
    static def install(ctx) {
        ctx.sh " helm "
    }

}
