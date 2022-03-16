package shared.library.common

/**
 * @author 潘维吉
 * @date 2021/8/10 13:22
 * @email 406798106@qq.com
 * @description jenkins相关方法
 */
class Jenkins implements Serializable {

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
