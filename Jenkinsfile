#!groovy
@Library('jenkins-shared-library@master') _

/**
 * @author 潘维吉
 * @description 核心Pipeline代码 针对Web项目和JAVA项目CI/CD的脚本
 * 注意 本文件在Git位置和名称不能随便改动 配置在jenkins里
 */
// Pipeline可能需要安装的插件
// 共享库请先去配置Jenkins系统配置 -> Global Pipeline Libraries 注意名称和版本分支分开填写 最终组合如@Library('jenkins-shared-library@master')
// Pipeline Maven Integration , NodeJS , Pipeline Utility Steps , DingTalk , Docker , Docker Pipeline
// build user vars, Git Parameter, AnsiColor, Generic Webhook Trigger, simple theme, Blue Ocean, Gitlab, HTTP Request, ThinBackup,
// Role Strategy, SSH Pipeline Steps, HTML Publisher, Extended Choice Parameter, Hidden Parameter, Rebuilder, Active Choices

// 根据不同环境项目配置不同参数
def map = [:]

// 远程服务器地址
map.put('remote_ip', '101.200.54.165')
// 工作服务器地址 同时支持N个服务器自动化部署
map.put('remote_worker_ips', [])
// 远程服务器用户名
map.put('remote_user_name', 'root')

// 默认统一设置项目级别的分支 方便整体控制改变分支 将覆盖单独job内的设置
map.put('default_git_branch', ' ')

// 保持构建的最大个数
map.put('build_num_keep', 1)

// Docker相关参数
// JVM内存设置
map.put('docker_java_opts', '-Xmx600m')
// docker内存限制
map.put('docker_memory', '800m')
// docker日志限制
map.put('docker_log_opts', 'max-size=50m') // --log-opt max-size=50m --log-opt max-file=3
// docker挂载映射 docker run -v 参数  多个用逗号,分割
map.put('docker_volume_mount', '')
// Dockerfile多阶段构建 镜像名称
map.put('docker_multistage_build_images', ' ')
// 是否上传镜像到docker容器仓库
map.put('is_push_docker_repo', false)
// docker容器镜像仓库账号信任id
map.put('docker_repo_credentials_id', '5553c9ed-5416-4bbf-b7fc-9d53ed856177')
// docker镜像仓库注册地址
map.put('docker_repo_registry', 'registry.cn-qingdao.aliyuncs.com')
// docker仓库命名空间名称
map.put('docker_repo_namespace', 'lexiang')

// 项目标签或项目简称
map.put('project_tag', ' ')

// 是否是生产环境
map.put('is_prod', false)
// 是否在同一台服务器分布式部署
map.put('is_same_server', true)
// 是否进行优雅停机
map.put('is_grace_shutdown', true)
// 是否进行服务启动健康探测
map.put('is_health_check', true)
// 是否Pipeline内脚本钉钉通知  总开关
map.put('is_ding_notice', true)
// 是否进行部署前通知
map.put('is_before_deploy_notice', false)
// 是否通知变更记录
map.put('is_notice_change_log', true)
// 是否在生产环境发布成功后自动给Git仓库打Tag版本和生成变更记录
map.put('is_git_tag', true)
// 是否需要css预处理器sass
map.put('is_need_sass', false)

// jenkins分布式构建节点label名称
map.put('jenkins_node', 'master')
map.put('jenkins_node_front_end', 'master')

// 构建环境变量
map.put('nodejs', 'Node14')
map.put('maven', 'Maven3.6')
map.put('jdk', '11')

// 相关信任标识
map.put('ci_git_credentials_id', '45392b97-5c21-4451-b323-bbf104f70e51')
map.put('git_credentials_id', '45392b97-5c21-4451-b323-bbf104f70e51')
map.put('ding_talk_credentials_id', 'ba0ebec7-73ad-4a26-af8b-d15c470b1328')

// 服务器上部署所在的文件夹名称
map.put('deploy_folder', "my")
// Web项目NPM打包代码所在的文件夹名称
map.put('npm_package_folder', "dist")
// Web项目解压到指定目录层级
map.put('web_strip_components', 1)
// 如果Maven模块化存在二级模块目录 设置一级模块目录名称
map.put('maven_one_level', ' ')
// Maven自定义指定settings.xml文件  如设置私有库或镜像源情况
map.put('maven_setting_xml', ' ')

// 调用核心通用Pipeline
sharedLibrary(map)


// ---------------------------------------------------------------------------------------------------------------------

// jenkins流水线配置
// https://github.com/DreamPWJ/jenkins-shared-library.git  Jenkinsfile

/*
JSON_PARAMS:
{
    "REPO_URL" : "https://github.com/DreamPWJ/health.git" ,
    "BRANCH_NAME" : "prod" ,
    "PROJECT_TYPE" : "2" ,
    "PROJECT_NAME" : "health-app" ,
    "SHELL_PARAMS" : "health app 8080 8080 prod 8180" ,
}

可选参数如下
{
  "NPM_RUN_PARAMS" : "release"
  "IS_ROLL_DEPLOY" : true
  "IS_BLUE_GREEN_DEPLOY" : true
  "IS_STATIC_RESOURCE" : true
  "IS_MONO_REPO" : true
  "IS_BEFORE_DEPLOY_NOTICE" : true
}
*/

/*
Pipeline五大特性
代码: Pipeline以代码的形式实现，通常被检入源代码控制，使团队能够编辑、审查和迭代其CI/CD流程。
可持续性: Jenkins重启或者中断后都不会影响Pipeline Job。
停顿: Pipeline可以选择停止并等待任工输入或批准，然后再继续Pipeline运行。
多功能: Pipeline支持现实世界的复杂CI/CD要求，包括fork/join子进程，循环和并行执行工作的能力
可扩展: Pipeline插件支持其DSL的自定义扩展以及与其他插件集成的多个选项。
*/
