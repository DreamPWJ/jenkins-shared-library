#!groovy
@Library('jenkins-shared-library@dev') _

/**
 * @author 潘维吉
 * @description 核心Pipeline代码 针对C++语言项目CI/CD的脚本
 * 注意 本文件在Git位置和名称不能随便改动 配置在jenkins里
 */

// 根据不同环境项目配置不同参数
def map = [:]

// 远程服务器地址 k8s集群方式可填空或填公网代理负载IP
map.put('remote_ip', '101.200.54.165')
// 工作服务器地址 同时支持N个服务器自动化分布式部署
map.put('remote_worker_ips', [])
// 远程服务器用户名
map.put('remote_user_name', 'root')
// 代理机或跳板机外网ip用于透传部署到内网目标机器 选填 目标机器外部无法直接访问情况填写内网ip
map.put('proxy_jump_ip', ' ')
// 自定义跳板机ssh和scp访问用户名 可精细控制权限 默认root
map.put('proxy_jump_user_name', 'root')
// 自定义跳板机ssh和scp访问端口 默认22
map.put('proxy_jump_port', '22')

// 默认统一设置项目级别的分支 方便整体控制改变分支 将覆盖单独job内的设置
map.put('default_git_branch', ' ')
map.put('default_frontend_git_branch', ' ')

// 保持构建的最大个数
map.put('build_num_keep', 2)

// 容器Docker相关参数
// JVM内存设置
map.put('docker_java_opts', '-Xmx1000m')
// 容器最大cpu限制 m是千分之一 100m是0.1个CPU
map.put('docker_limit_cpu', '1000m')
// 容器最大内存限制 不支持小数点形式设置
map.put('docker_memory', '1G')
// docker日志限制
map.put('docker_log_opts', 'max-size=50m') // --log-opt max-size=50m --log-opt max-file=3
// docker挂载映射  docker run -v 参数(格式 宿主机挂载路径:容器内目标路径)  多个用逗号,分割
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

// K8S集群相关参数
// K8S集群部署集群访问授权账号kube.config配置信息信任ids 多集群,逗号分割   Jenkins系统管理的Manage Credentials，类型选择为“Secret file”配置
map.put('k8s_credentials_ids', ' ')
// K8S集群私有镜像仓库拉取密钥 在集群内使用kubectl create secret命令生成
map.put('k8s_image_pull_secrets', ' ')

// 项目标签或项目简称
map.put('project_tag', ' ')

// 是否是生产环境
map.put('is_prod', false)
// 是否在同一台服务器分布式部署
map.put('is_same_server', true)
// 是否进行优雅停机
map.put('is_grace_shutdown', false)
// 是否进行服务启动健康探测 K8S集群类型设置false使用自带健康探测
map.put('is_health_check', false)
// 是否Pipeline内脚本钉钉通知  总开关
map.put('is_ding_notice', false)
// 是否进行部署前通知
map.put('is_before_deploy_notice', false)
// 是否只通知发布变更记录
map.put('is_only_notice_change_log', false)
// 是否在生产环境发布成功后自动给Git仓库打Tag版本和生成变更记录
map.put('is_git_tag', false)
// 是否需要css预处理器sass
map.put('is_need_sass', false)

// jenkins分布式构建节点label名称  预配置在jenkins节点管理内
map.put('jenkins_node', 'linux')
map.put('jenkins_node_frontend', 'master')

// 构建环境变量 分别使用Node和Maven关键字加版本号方式 如Maven3.9
map.put('nodejs', 'Node14')
map.put('maven', 'Maven3.6')
map.put('jdk_publisher', 'openjdk')
map.put('jdk', '11')

// 相关信任标识
map.put('ci_git_credentials_id', '45392b97-5c21-4451-b323-bbf104f70e51')
map.put('git_credentials_id', '45392b97-5c21-4451-b323-bbf104f70e51')
map.put('ding_talk_credentials_id', 'ba0ebec7-73ad-4a26-af8b-d15c470b1328')
map.put('ding_talk_credentials_ids', [["keyword":"蓝能科技","token":"383391980b120c38f0f9a4a398349739fa67a623f9cfa834df9c5374e81b2081"]]) // 支持多个群通知
// OSS对象存储访问凭据配置 Jenkins系统管理的Manage Credentials，类型选择为“Secret file”配置
map.put('oss_credentials_id', ' ')
// 直连方式服务器集群自动SSH连接信息 实现CI构建机器和多台部署机之间的免密连接
map.put('ssh_hosts_id', ' ')
// 跳板机方式服务器集群自动SSH连接信息 实现CI构建机器和多台部署机之间的免密连接
map.put('proxy_jump_hosts_id', ' ')

// 服务器上部署所在的文件夹名称
map.put('deploy_folder', "my")
// Web项目NPM打包代码所在的文件夹名称
map.put('npm_package_folder', "dist")
// Web项目解压到指定目录层级
map.put('web_strip_components', 1)
// 如果Maven模块化存在二级模块目录 设置一级模块目录名称
map.put('maven_one_level', ' ')
// Maven自定义settings.xml文件Secret file凭据  如设置私有库或镜像源情况
map.put('maven_settings_xml_id', ' ')

// 调用核心通用Pipeline
sharedLibrary(map)


// ---------------------------------------------------------------------------------------------------------------------
// https://github.com/DreamPWJ/jenkins-shared-library.git  Jenkinsfile.cpp

/*

test-cpp
测试C++语言流水线
JSON_PARAMS
{
    "REPO_URL" : "https://github.com/DreamPWJ/cpp-demo.git" ,
    "BRANCH_NAME" : "master" ,
    "PROJECT_TYPE" : "2" ,
    "COMPUTER_LANGUAGE" : "5" ,
    "PROJECT_NAME" : "test-cpp" ,
    "SHELL_PARAMS" : "test-cpp demo 8078 5555 sit",
    "IS_BLUE_GREEN_DEPLOY" : true
}

*/


