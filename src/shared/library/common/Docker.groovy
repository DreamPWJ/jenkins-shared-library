package shared.library.common

import shared.library.GlobalVars
import shared.library.Utils

/**
 * @author 潘维吉
 * @date 2021/3/18 16:29
 * @email 406798106@qq.com
 * @description Docker相关
 * 构建Docker镜像与上传远程仓库 拉取镜像 等
 */
class Docker implements Serializable {

    // 镜像标签  也可自定义版本标签用于无需重复构建相同的镜像, 做到复用镜像CD持续部署到多环境中
    // k8s集群中 在生产环境中部署容器时，你应该避免使用 :latest 标签，因为这使得正在运行的镜像的版本难以追踪，并且难以正确地回滚
    static def imageTag = "latest" // Utils.getVersionNum(ctx)
    static def imageNodeTag = "-node-" // 相同应用不同容器镜像标签

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        try {
            //println(Utils.getShEchoResult(this, "whoami"))
            //def dockerPath = tool 'Docker' //全局配置里 名称Docker 位置/usr/local  使用系统安装好的docker引擎
            ctx.env.PATH = "${ctx.env.PATH}:/usr/local/bin:/usr/local/go/bin:/usr/bin/docker" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化Docker环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     * 初始化Docker引擎环境 自动化第一次部署环境
     */
    static def initDocker(ctx) {
        try {
            // 判断服务器是是否安装docker环境
            ctx.sh "ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} 'docker version' "
        } catch (error) {
            ctx.println error.getMessage()
            ctx.dir("${ctx.env.WORKSPACE}/ci") {
                // 查看系统信息 Red Hat下CentOS旧版本使用cat /etc/redhat-release
                def systemInfoCommand = "lsb_release -a || cat /etc/redhat-release"
                def linuxType = Utils.getShEchoResult(ctx, "ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} '${systemInfoCommand}' ")
                // 判断linux主流发行版类型
                def dockerFileName = ""
                if ("${linuxType}".contains("CentOS")) {
                    ctx.println "CentOS系统"
                    dockerFileName = "docker-install.sh"
                } else if ("${linuxType}".contains("Ubuntu")) {
                    ctx.println "Ubuntu系统"
                    dockerFileName = "docker-install-ubuntu.sh"
                } else {
                    ctx.println "Linux系统: ${linuxType}"
                    ctx.error("部署服务器非CentOS或Ubuntu系统类型 ❌")
                }
                // 上传docker初始化脚本
                ctx.sh " scp ${ctx.proxyJumpSCPText} -r ./_docker/${dockerFileName}  ${ctx.remote.user}@${ctx.remote.host}:/${ctx.DEPLOY_FOLDER}/ "
                // 给shell脚本执行权限
                ctx.sh " ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} 'chmod +x /${ctx.DEPLOY_FOLDER}/${dockerFileName} ' "
                retry(2) { // 重试几次
                    ctx.println "初始化Docker引擎环境  执行Docker初始化脚本"
                    ctx.sh " ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} 'cd /${ctx.DEPLOY_FOLDER} && ./${dockerFileName} ' "
                }
            }
        }
    }

    /**
     *  构建Docker镜像
     *  Docker For Mac 3.1.0以后docker login登录镜像仓库报错 删除 ~/.docker/config.json中的credsStore这行解决
     */
    static def build(ctx, imageName, deployNum = 0) {
        // k8s用版本号方式给tag打标签
        if ("${ctx.IS_K8S_DEPLOY}" == 'true') {
            imageTag = Utils.getVersionNum(ctx)
        }
        def localImageTag = imageTag
        // 自动替换相同应用不同分布式部署节点的环境文件
        if ("${ctx.IS_DIFF_CONF_IN_DIFF_MACHINES}" == 'true' && "${ctx.SOURCE_TARGET_CONFIG_DIR}".trim() != "" && deployNum != 0) {
            localImageTag += imageNodeTag + deployNum // 重新定义镜像标签 区分不同节点不同配置情况
        }
        //ctx.pullCIRepo()
        def imageFullName = "${ctx.DOCKER_REPO_NAMESPACE}/${imageName}:${localImageTag}"
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_REPO_CREDENTIALS_ID}", usernameVariable: 'DOCKER_HUB_USER_NAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
            ctx.sh """      
                   docker login ${ctx.DOCKER_REPO_REGISTRY} --username=${ctx.DOCKER_HUB_USER_NAME} --password=${ctx.DOCKER_HUB_PASSWORD}
                   """
            def dockerBuildDiffStr = " build " // 默认构建镜像
            def dockerPushDiffStr = "" // 默认不同时推送镜像
            // 是否使用buildkit构建多CPU架构支持
            def isBuildKit = false

            if (isBuildKit) {
                //docker buildx 多CPU架构支持 Building Multi-Arch Images for Arm and x86 with Docker Desktop
                //docker buildx create --name mybuilder && docker buildx use mybuilder && docker buildx build --platform linux/amd64 .
                //多CPU架构文档: https://docs.docker.com/develop/develop-images/build_enhancements/
                ctx.println("开始制作多CPU架构Docker镜像并上传远程仓库")
                // 解决buildx报错error: failed to solve: rpc error: code = Unknown desc = failed to solve with frontend dockerfile.v0
                // Docker desktop -> Settings -> Docker Engine -> Change the "features": { buildkit: true} to "features": { buildkit: false}

                // 开启Buildkit
                ctx.sh """  export DOCKER_BUILDKIT=1
                       """
                // 在Docker容器内使用Buildkit
                /* ctx.sh """  DOCKER_CLI_EXPERIMENTAL=enabled
                            """*/
                dockerBuildDiffStr = " buildx build --platform linux/amd64 "
                dockerPushDiffStr = " --push "
            } else {
                ctx.println("开始制作Docker镜像并上传远程仓库")
            }

            if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                def webDockerFileName = "Dockerfile"
                if ("${ctx.CUSTOM_DOCKERFILE_NAME}" != "") {
                    webDockerFileName = "${ctx.CUSTOM_DOCKERFILE_NAME}"
                    // 如Node构建环境 SSR方式等
                    ctx.sh """ cd ${ctx.env.WORKSPACE}/${ctx.monoRepoProjectDir} && pwd && \
                            docker ${dockerBuildDiffStr} -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}  \
                            -f ${ctx.env.WORKSPACE}/ci/.ci/web/${webDockerFileName} . --no-cache \
                            ${dockerPushDiffStr}
                            """
                } else {
                    // 前端静态资源部署
                    ctx.sh """  cp -p ${ctx.env.WORKSPACE}/ci/.ci/web/default.conf ${ctx.env.WORKSPACE}/${ctx.monoRepoProjectDir} &&
                            cd ${ctx.env.WORKSPACE}/${ctx.monoRepoProjectDir} && pwd && \
                            docker ${dockerBuildDiffStr} -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}  \
                            --build-arg DEPLOY_FOLDER="${ctx.DEPLOY_FOLDER}" --build-arg PROJECT_NAME="${ctx.PROJECT_NAME}"  --build-arg WEB_STRIP_COMPONENTS="${ctx.WEB_STRIP_COMPONENTS}" \
                            --build-arg NPM_PACKAGE_FOLDER=${ctx.NPM_PACKAGE_FOLDER}  -f ${ctx.env.WORKSPACE}/ci/.ci/web/${webDockerFileName} . --no-cache \
                            ${dockerPushDiffStr}
                            """
                }

            } else if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
                def exposePort = "${ctx.SHELL_HOST_PORT}"
                if ("${ctx.SHELL_PARAMS_ARRAY.length}" == '7') { // 扩展端口
                    exposePort = "${ctx.SHELL_HOST_PORT} ${ctx.SHELL_EXTEND_PORT}"
                }
                exposePort = "${ctx.IS_PROD}" == 'true' ? "${exposePort}" : "${exposePort} 5005" // 调试端口
                if ("${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                    def dockerFileName = ""
                    if ("${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot) {
                        dockerFileName = "Dockerfile"
                    } else if ("${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringMVC) {
                        dockerFileName = "Dockerfile.mvc"
                    }
                    ctx.sh """ cd ${ctx.mavenPackageLocationDir} && pwd &&
                            docker ${dockerBuildDiffStr} -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --build-arg DEPLOY_FOLDER="${ctx.DEPLOY_FOLDER}" \
                            --build-arg PROJECT_NAME="${ctx.PROJECT_NAME}"  --build-arg EXPOSE_PORT="${exposePort}" --build-arg TOMCAT_VERSION=${ctx.TOMCAT_VERSION} \
                            --build-arg JDK_VERSION=${ctx.JDK_VERSION}  -f ${ctx.env.WORKSPACE}/ci/.ci/${dockerFileName} . --no-cache \
                            ${dockerPushDiffStr}
                            """
                }
            }
            // 非buildkit构建 推送镜像到远程仓库
            if (!isBuildKit) {
                ctx.sh " docker push ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} "
            }
            ctx.println("构建镜像上传完成并删除本地镜像")
            // --no-prune : 不移除该镜像的过程镜像 默认移除 移除导致并发构建找不到父镜像层
            ctx.sh """
            docker rmi ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --no-prune || true
             """
            //  docker rmi \$(docker images | grep "none" | awk '{print \$3}') || true
        }
    }

    /**
     *  Docker镜像上传远程仓库
     */
    static def push(ctx, imageName) {
        ctx.retry(3) {
            def imageFullName = "${ctx.DOCKER_REPO_NAMESPACE}/${imageName}:${imageTag}"
            ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_REPO_CREDENTIALS_ID}", usernameVariable: 'DOCKER_HUB_USER_NAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
                ctx.sh """  docker login ${ctx.DOCKER_REPO_REGISTRY} --username=${ctx.DOCKER_HUB_USER_NAME} --password=${ctx.DOCKER_HUB_PASSWORD}
                        docker push ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}
                        docker rmi ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --no-prune
                   """
            }
        }
    }

    /**
     *  拉取远程仓库Docker镜像
     */
    static def pull(ctx, imageName) {
        def imageFullName = "${ctx.DOCKER_REPO_NAMESPACE}/${imageName}:${imageTag}"
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_REPO_CREDENTIALS_ID}", usernameVariable: 'DOCKER_HUB_USER_NAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
            ctx.sh """     
                       ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} \
                      'docker login ${ctx.DOCKER_REPO_REGISTRY} --username=${ctx.DOCKER_HUB_USER_NAME} --password=${ctx.DOCKER_HUB_PASSWORD} && \
                       docker pull ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}'
                    """
        }
    }

    /**
     *  Docker多阶段镜像构建处理
     */
    static def multiStageBuild(ctx, imageName) {
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {

        } else if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            if ("${imageName}".trim() != "") {
                ctx.println("Docker多阶段镜像构建镜像名称: " + imageName)
                def dockerFile = "${ctx.env.WORKSPACE}/ci/.ci/Dockerfile"
                def dockerFileContent = ctx.readFile(file: "${dockerFile}")
                ctx.writeFile file: "${dockerFile}", text: "${dockerFileContent}"
                        .replaceAll("#FROM-MULTISTAGE-BUILD-IMAGES", "FROM ${imageName}")
                        .replaceAll("#COPY-MULTISTAGE-BUILD-IMAGES", "COPY --from=0 / /")
            }
        }
    }

}
