package shared.library.common

/**
 * @author 潘维吉
 * @date 2022/06/28 13:22
 * @email 406798106@qq.com
 * @description Qodana是一个代码质量监控平台 JetBrains公司出品
 */
class Qodana implements Serializable {

    /**
     * 分析
     * 文档: https://www.jetbrains.com/help/qodana/jenkins.html
     */
    static def analyse(ctx) {
        // CLI 应该从具有qodana.yaml
        def qodanaFile = "${ctx.env.WORKSPACE}/ci/_jenkins/qodana/qodana.yaml"
        // ctx.sh "export QODANA=${qodanaFile}"
        // 质量门是 Qdana 可以在不导致 CI/CD 工作流或管道失败的情况下检测到的最大问题数量 一旦达到质量门限，Qodana 就会以退出代码 255 终止
        def qualityGate = " --fail-threshold 500 "
        // 执行命令
        ctx.sh "qodana --save-report ${qualityGate}"
    }

}
