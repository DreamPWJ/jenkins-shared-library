package shared.library.common

import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*
import groovy.json.JsonOutput

/**
 * @author 潘维吉
 * @date 2021/8/10 13:22
 * @email 406798106@qq.com
 * @description jenkins相关方法
 * 访问Jenkins调试的Groovy脚本: https://YOUR_JENKINS_URL/script
 * 执行Jenkins调用CLI命令: https://www.jenkins.io/doc/book/managing/cli/
 */
class JenkinsCI implements Serializable {

    /**
     * 获取当前构建的Git变更文件集合
     */
    static def getChangedFilesList(ctx) {
        def changedFiles = []
        for (changeLogSet in ctx.currentBuild.changeSets) {
            for (entry in changeLogSet.getItems()) { // 对于检测到的更改中的每个提交
                for (file in entry.getAffectedFiles()) {
                    changedFiles.add(file.getPath()) // 将更改的文件添加到列表
                }
            }
        }
        return changedFiles
    }

    /**
     * 当前job是否有代码变更记录并提醒
     */
    static def getNoChangeLogAndTip(ctx) {
        try {
            def lastBuild = ctx.currentBuild.previousBuild
            if (lastBuild != null) {
                // 判断上次是否成功
                if (!lastBuild.result == 'SUCCESS') {
                    return // 如果上次构建不成功 之前变更记录获取不到 不再处理
                }
            }
            // 判断是否是默认分支 如果切换分支currentBuild.changeSets也会无记录 因为不知道上次代码的基线
            if (ctx.jsonParams.BRANCH_NAME.trim() != ctx.params.GIT_BRANCH) {
                ctx.println("当前分支: ${ctx.params.GIT_BRANCH} 不是默认分支: ${ctx.jsonParams.BRANCH_NAME.trim()}")
                return
            }

            // 获取所有变更记录
            def changeLogSets = ctx.currentBuild.changeSets // 始终在checkout后使用 确保在检出步骤之后访问变更集
            def filteredChanges = []

            // 遍历每个变更集（多仓库支持）  过滤特殊前缀git提交记录并返回数据
            changeLogSets.each { changeLogSet ->
                changeLogSet.items.each { commit ->
                    // 过滤条件：排除特殊开头的提交
                    if (!commit.msg.startsWith(GlobalVars.gitCommitChangeLogDocs)) {
                        filteredChanges.add([
                                msg: commit.msg,
                        ])
                    }
                }
            }

            if (filteredChanges.isEmpty()) {
                ctx.addBadge(id: "no-change-log-badge", text: "无代码", color: 'yellow', cssClass: 'badge-text--background')
            }
        } catch (e) {
            ctx.println("获取变更记录失败：${e.message}")
        }
    }

    /**
     * 获取当前job信息
     */
    @NonCPS
    static def getCurrentBuildParent(ctx) {
        // 获取当前项目的描述信息
        def job = ctx.currentBuild.getRawBuild().getParent()
        return job
    }

    /**
     * 获取当前job信息描述
     */
    @NonCPS
    static def getCurrentBuildDescription(ctx) {
        // 获取当前项目的描述信息
        def job = ctx.currentBuild.getRawBuild().getParent()
        def description = job.description ?: "无项目描述信息"
        // ctx.echo "当前项目描述: ${description}"
        return description
    }

    /**
     * 获取变更的模块 用于自动发布指定模块
     */
    static def getAutoPublishModule(ctx, pathPrefix) {
        // 使用Set容器去重，保证待发布模块只有一份
        def modulePaths = new HashSet<String>();
        for (def filePath in getChangedFilesList(ctx)) {
            // 忽略非模块的文件，比如 Jenkinsfile 等
            if (filePath.startsWith(pathPrefix)) {
                // 从超过模块前缀长度的下标开始，获取下一个/的位置。即分串位置
                int index = filePath.indexOf('/', pathPrefix.length() + 1)
                // 分串得到模块路径，比如 develop/panweiji/app
                def modulePath = filePath.substring(0, index)
                // println 'add module path: ' + modulePath
                modulePaths.add(modulePath)
            }
        }
        println '自动获取变更发布模块列表：' + modulePaths
        return modulePaths;
    }

    /**
     * 自动触发
     */
    static def trigger(ctx, jenkinsUrl, deployJobName, token, params) {
        // 远程访问Open API文档: https://www.jenkins.io/doc/book/using/remote-access-api/
        // WORKSPACE returns working directory which is /var/lib/jenkins/jobs/FOLDER/...
        def folder = ctx.WORKSPACE.split('/')[5]
        // http://jenkins.domain.com/generic-webhook-trigger/invoke?token=jenkins-app
        def url = "$jenkinsUrl/job/$folder/job/$deployJobName/buildWithParameters?token=$token"
        params.each { param ->
            url = url + "\\&$param.key=$param.value"
        }
        ctx.echo("jenkins job自动触发部署: $url")
        ctx.sh(script: "curl -fk $url")
    }

    /**
     * 获取所有分布式node节点信息
     */
    static def getAllNodes(ctx) {
        def nodesArray = ["master"] // 添加 Master 节点标签
        // 获取所有节点
        def allNodes = Jenkins.instance.nodes
        // 遍历节点并输出名称
        for (node in allNodes) {
            def computer = node.toComputer()
            if (computer.online) { // 是否在线
                nodesArray.add(node.nodeName) // 匹配的是label标签 而非名称
            }
            // ctx.println( "Node Name: ${node.nodeName}")
        }
        return nodesArray
    }

    /**
     * 获取所有job信息 并更新等
     */
    static def getAllJobs(ctx) {
        Jenkins.instance.items.each { job ->
            // println("${job.name}:${job.url}")
            /*  job.builds.each { build ->
                      println("${build.number}:${build.url}")
           }  */
            try {
                // 重载配置（清除缓存，重新解析共享库）
                job.doReload()
                println "[SUCCESS] Reloaded job: ${job.fullName}"
            } catch (Exception e) {
                println "[ERROR] Failed to reload ${job.fullName}: ${e.message}"
            }
        }
        Jenkins.instance.save()

    }

    /**
     * 获取所有已安装插件信息
     * 存储到 plugins.txt 用于自动化初始化安装大量插件
     */
    static def getAllPlugins(ctx) {
        Jenkins.instance.pluginManager.plugins.each {
            plugin ->
                println("${plugin.getShortName()}:${plugin.getVersion()}")
        }
        // java -jar jenkins-cli.jar -s https://jenkins.url/ install-plugin SOURCE ... [-deploy] [-name VAL] [-restart]
    }

    /**
     * 重新加载配置
     */
    static def reload(ctx) {
        // curl -X POST http://localhost:9090/reload -u "<your-admin-username>:<your-admin-api-token>"
    }

    /**
     * 检测授信是否存在
     */
    static Boolean checkCredentialsExist(ctx, id) {
        try {
            ctx.withCredentials([ctx.string(credentialsId: id, variable: 'jenkinsToken')]) {
                true
            }
        } catch (_) {
            false
        }
    }

}
