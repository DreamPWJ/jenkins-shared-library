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
            //ctx.println("初始化Docker环境变量")
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
                    dockerFileName = "docker-install-centos.sh"
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
                ctx.sh " ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} 'chmod +x /${ctx.DEPLOY_FOLDER}/${dockerFileName} ' || true "
                ctx.retry(3) { // 重试几次
                    ctx.println "初始化Docker引擎环境  执行Docker初始化脚本"
                    ctx.sh " ssh ${ctx.proxyJumpSSHText} ${ctx.remote.user}@${ctx.remote.host} 'cd /${ctx.DEPLOY_FOLDER} && ./${dockerFileName} ' "
                }
            }
        }
    }

    /**
     *  构建Docker镜像和多CPU平台架构镜像
     */
    static def build(ctx, imageName, deployNum = 0) {
        // 设置镜像源 加速构建和解决网络不通等问题
        setDockerRegistry(ctx)

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
            // Docker For Mac 3.1.0以后docker login登录镜像仓库报错 删除 ~/.docker/config.json中的credsStore这行解决
            ctx.sh """      
                   docker login ${ctx.DOCKER_REPO_REGISTRY} --username=${ctx.DOCKER_HUB_USER_NAME} --password=${ctx.DOCKER_HUB_PASSWORD}
                   """
            def dockerBuildDiffStr = " build " // 默认构建镜像
            def dockerPushDiffStr = "" // 默认不同时推送镜像
            // 是否使用buildkit构建多CPU架构支持
            def isBuildKit = "${ctx.IS_DOCKER_BUILD_MULTI_PLATFORM}" == 'true' ? true : false

            if (isBuildKit) { // 构建多CPU架构镜像
                // docker buildx 多CPU架构支持 Building Multi-Arch Images for Arm and x86 with Docker Desktop
                // docker buildx create --name mybuilder && docker buildx use mybuilder && docker buildx build --platform linux/amd64 .
                // 多CPU架构文档: https://docs.docker.com/build/building/multi-platform/
                ctx.println("开始制作多CPU架构Docker镜像并上传远程仓库")
                // 解决buildx报错error: failed to solve: rpc error: code = Unknown desc = failed to solve with frontend dockerfile.v0
                // Docker desktop -> Settings -> Docker Engine -> Change the "features": { buildkit: true} to "features": { buildkit: false}

                // 是否开启Buildkit 是下一代的镜像构建组件
                ctx.sh """  export DOCKER_BUILDKIT=1
                       """
                // 在Docker容器内使用Buildkit
                /* ctx.sh """  DOCKER_CLI_EXPERIMENTAL=enabled
                            """  */
                // 根据运行CPU架构构建Docker镜像
                dockerBuildDiffStr = " buildx build --platform linux/arm64 " // 如 --platform  linux/arm64,linux/amd64
                dockerPushDiffStr = " --push "
            } else {
                ctx.println("开始制作Docker镜像并上传远程仓库 🏗️ ")
            }

            if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                def webDockerFileName = "Dockerfile"
                if ("${ctx.CUSTOM_DOCKERFILE_NAME}" != "") {
                    webDockerFileName = "${ctx.CUSTOM_DOCKERFILE_NAME}"
                    // 如Node构建环境 SSR方式等
                    // 拉取基础镜像避免重复下载
                    def dockerImagesName = "node:lts"
                    ctx.sh " [ -z \"\$(docker images -q ${dockerImagesName})\" ] && docker pull ${dockerImagesName} || echo \"基础镜像 ${dockerImagesName} 已存在无需重新pull拉取\" "
                    ctx.sh """ cd ${ctx.env.WORKSPACE}/${ctx.monoRepoProjectDir} && pwd && \
                            docker ${dockerBuildDiffStr} -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}  \
                            --build-arg EXPOSE_PORT="${ctx.SHELL_EXPOSE_PORT}" \
                            -f ${ctx.env.WORKSPACE}/ci/.ci/web/${webDockerFileName} . --no-cache \
                            ${dockerPushDiffStr}
                            """
                } else {
                    // 前端静态资源部署
                    def webProjectDir = ctx.monoRepoProjectDir
                    if ("${ctx.GIT_PROJECT_FOLDER_NAME}" != "") { // Git目录区分项目
                        webProjectDir = "${ctx.GIT_PROJECT_FOLDER_NAME}"
                    }
                    def dockerImagesName = "nginx:stable"
                    // 拉取基础镜像避免重复下载
                    ctx.sh " [ -z \"\$(docker images -q ${dockerImagesName})\" ] && docker pull ${dockerImagesName} || echo \"基础镜像 ${dockerImagesName} 已存在无需重新pull拉取\" "
                    ctx.sh """  cp -p ${ctx.env.WORKSPACE}/ci/.ci/web/default.conf ${ctx.env.WORKSPACE}/${webProjectDir} &&
                            cp -p ${ctx.env.WORKSPACE}/ci/.ci/web/nginx.conf ${ctx.env.WORKSPACE}/${webProjectDir} &&
                            cd ${ctx.env.WORKSPACE}/${webProjectDir} && pwd && \
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
                    def jdkPublisher = "${ctx.JDK_PUBLISHER}"
                    def dockerImagesName = ""
                    if ("${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringBoot) {
                        dockerFileName = "Dockerfile"
                        if ("${ctx.IS_SPRING_NATIVE}" == 'true') { // Spring Native原生镜像可执行二进制文件
                            dockerFileName = "Dockerfile.native"
                            // jdkPublisher = "container-registry.oracle.com/graalvm/native-image"  // GraalVM JDK with Native Image
                            // GraalVM JDK without Native Image
                            jdkPublisher = "container-registry.oracle.com/graalvm/jdk"
                            dockerImagesName = "${jdkPublisher}:${ctx.JDK_VERSION}"
                        } else {
                            // 拉取基础镜像避免重复下载
                            dockerImagesName = "${jdkPublisher}:${ctx.JDK_VERSION}"
                        }
                    } else if ("${ctx.JAVA_FRAMEWORK_TYPE}".toInteger() == GlobalVars.SpringMVC) {
                        dockerFileName = "Dockerfile.mvc"
                        // 拉取基础镜像避免重复下载
                        dockerImagesName = "${ctx.TOMCAT_VERSION}-jre8"
                    }

                    ctx.sh " [ -z \"\$(docker images -q ${dockerImagesName})\" ] && docker pull ${dockerImagesName} || echo \"基础镜像 ${dockerImagesName} 已存在无需重新pull拉取\" "

                    ctx.sh """ cd ${ctx.env.WORKSPACE}/${ctx.GIT_PROJECT_FOLDER_NAME}/${ctx.mavenPackageLocationDir} && pwd &&
                            docker ${dockerBuildDiffStr} -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --build-arg DEPLOY_FOLDER="${ctx.DEPLOY_FOLDER}" \
                            --build-arg PROJECT_NAME="${ctx.PROJECT_NAME}" --build-arg EXPOSE_PORT="${exposePort}" --build-arg TOMCAT_VERSION=${ctx.TOMCAT_VERSION} \
                            --build-arg JDK_PUBLISHER=${jdkPublisher} --build-arg JDK_VERSION=${ctx.JDK_VERSION} --build-arg JAVA_OPTS="-Xms128m ${ctx.DOCKER_JAVA_OPTS}" \
                            -f ${ctx.env.WORKSPACE}/ci/.ci/${dockerFileName} . --no-cache \
                            ${dockerPushDiffStr}
                            """
                } else if ("${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
                    def dockerImagesName = "python:${ctx.CUSTOM_PYTHON_VERSION}"
                    // 拉取基础镜像避免重复下载
                    ctx.sh " [ -z \"\$(docker images -q ${dockerImagesName})\" ] && docker pull ${dockerImagesName} || echo \"基础镜像 ${dockerImagesName} 已存在无需重新pull拉取\" "
                    ctx.sh """ cd ${ctx.env.WORKSPACE}/${ctx.GIT_PROJECT_FOLDER_NAME} && pwd &&
                            docker ${dockerBuildDiffStr} -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --build-arg DEPLOY_FOLDER="${ctx.DEPLOY_FOLDER}" \
                            --build-arg PROJECT_NAME="${ctx.PROJECT_NAME}"  --build-arg EXPOSE_PORT="${exposePort}"  \
                            --build-arg PYTHON_VERSION=${ctx.CUSTOM_PYTHON_VERSION} --build-arg PYTHON_START_FILE=${ctx.CUSTOM_PYTHON_START_FILE} \
                            -f ${ctx.env.WORKSPACE}/ci/.ci/python/Dockerfile . --no-cache \
                            ${dockerPushDiffStr}
                            """
                }
            }
            // 非buildkit构建 推送镜像到远程仓库
            if (!isBuildKit) {
                ctx.retry(3) {  // 网络问题 重试机制
                    ctx.sh " docker push ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} "
                }
            }
            ctx.println("构建镜像并上传到容器仓库完成 ✅")
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
        def imageFullName = "${ctx.DOCKER_REPO_NAMESPACE}/${imageName}:${imageTag}"
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_REPO_CREDENTIALS_ID}", usernameVariable: 'DOCKER_HUB_USER_NAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
            ctx.retry(3) {  // 网络问题 重试机制
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
            ctx.println("拉取远程仓库Docker镜像完成 ✅")
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
                def dockerFile = ""
                if ("${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                    dockerFile = "${ctx.env.WORKSPACE}/ci/.ci/Dockerfile"
                } else if ("${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
                    dockerFile = "${ctx.env.WORKSPACE}/ci/.ci/python/Dockerfile"
                }
                def dockerFileContent = ctx.readFile(file: "${dockerFile}")
                ctx.writeFile file: "${dockerFile}", text: "${dockerFileContent}"
                        .replaceAll("#FROM-MULTISTAGE-BUILD-IMAGES", "FROM ${imageName}")
                        .replaceAll("#COPY-MULTISTAGE-BUILD-IMAGES", "COPY --from=0 / /")
            }
        }
    }

    /**
     *  Docker镜像源设置
     *  加速构建和解决网络不通等问题
     */
    static def setDockerRegistry(ctx) {
        ctx.println("Docker镜像源设置 加速构建速度")
        // 阿里云镜像加速仅对阿里云产品有效
        ctx.sh """     
export DOCKER_REGISTRY_MIRROR='https://docker.lanneng.tech,https://em1sutsj.mirror.aliyuncs.com'
             """

        // 让容器配置服务生效 reload 不会重启 Docker 服务，但会使新的配置生效
        // ctx.sh " sudo systemctl reload docker "
    }

    /**
     *  Docker镜像容器回滚版本
     *  当服务启动失败的时候 回滚服务版本 保证服务高可用
     */
    static def rollback(ctx, imageName) {
        ctx.println("执行Docker镜像容器回滚版本")
        // 版本控制策略
        ctx.sh " docker tag myapp:latest myapp:v1.2.3_\$(date +%Y%m%d%H%M) "
        // 快速回滚操作
        ctx.sh " docker stop <container_name> && docker rm <container_name> "
        // 启动上一个稳定版本容器
        ctx.sh " docker run -d --name <new_container> myapp:v1.2.3_20250327_1530"
    }

}
