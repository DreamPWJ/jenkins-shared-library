package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/8/10 13:22
 * @email 406798106@qq.com
 * @description jenkins相关方法
 * 访问Jenkins调试的Groovy脚本: https://YOUR_JENKINS_URL/script
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
     * 获取所有已安装插件信息
     * 存储到 plugins.txt 用于自动化初始化安装大量插件
     */
    static def getAllPlugins(ctx) {
        Jenkins.instance.pluginManager.plugins.each{
            plugin ->
                println ("${plugin.getShortName()}:${plugin.getVersion()}")
        }
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
