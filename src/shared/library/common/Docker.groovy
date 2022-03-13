package shared.library.common

import shared.library.GlobalVars

/**
 * @author 潘维吉
 * @date 2021/3/18 16:29
 * @email 406798106@qq.com
 * @description  Docker相关
 * 构建Docker镜像与上传远程仓库 拉取镜像 等
 */
class Docker implements Serializable {

    // 镜像标签  也可自定义版本标签用于无需重复构建相同的镜像, 做到复用镜像CD持续部署到多环境中
    static def imageTag = "latest"

    /**
     *  初始化环境变量
     */
    static def initEnv(ctx) {
        try {
            //println(Utils.getShEchoResult(this, "whoami"))
            //def dockerPath = tool 'Docker' //全局配置里 名称Docker 位置/usr/local  使用系统安装好的docker引擎
            ctx.env.PATH = "${ctx.env.PATH}:/usr/local/bin:/usr/local/go/bin" //添加了系统环境变量上
        } catch (e) {
            ctx.println("初始化Docker环境变量失败")
            ctx.println(e.getMessage())
        }
    }

    /**
     *  构建Docker镜像
     *  Docker For Mac 3.1.0以后docker login登录镜像仓库报错 删除 ~/.docker/config.json中的credsStore这行解决
     */
    static def build(ctx, imageName) {
        //ctx.pullCIRepo()
        def imageFullName = "${ctx.DOCKER_REPO_NAMESPACE}/${imageName}:${imageTag}"
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_REPO_CREDENTIALS_ID}", usernameVariable: 'DOCKER_HUB_USER_NAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
            ctx.sh """      
                   docker login ${ctx.DOCKER_REPO_REGISTRY} --username=${ctx.DOCKER_HUB_USER_NAME} --password=${ctx.DOCKER_HUB_PASSWORD}
                   """
            //docker buildx 多CPU架构支持 Building Multi-Arch Images for Arm and x86 with Docker Desktop
            //docker buildx create --name mybuilder , docker buildx use mybuilder &&  docker buildx build --platform linux/amd64
            //多CPU架构文档: https://docs.docker.com/develop/develop-images/build_enhancements/
            ctx.println("开始制作多CPU架构Docker镜像并上传远程仓库")
            // 解决buildx报错error: failed to solve: rpc error: code = Unknown desc = failed to solve with frontend dockerfile.v0
            // Docker desktop -> Settings -> Docker Engine -> Change the "features": { buildkit: true} to "features": { buildkit: false}
            ctx.sh """  export DOCKER_BUILDKIT=0
                        export COMPOSE_DOCKER_CLI_BUILD=0
                       """
            if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
                ctx.sh """  cp -p ${ctx.env.WORKSPACE}/ci/.ci/web/default.conf ${ctx.env.WORKSPACE}/${ctx.monoRepoProjectDir} &&
                            cd ${ctx.env.WORKSPACE}/${ctx.monoRepoProjectDir} && pwd && \
                            docker  buildx build --platform linux/amd64  -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}  \
                            --build-arg DEPLOY_FOLDER="${ctx.DEPLOY_FOLDER}" --build-arg PROJECT_NAME="${ctx.PROJECT_NAME}"  --build-arg WEB_STRIP_COMPONENTS="${ctx.WEB_STRIP_COMPONENTS}" \
                            --build-arg NPM_PACKAGE_FOLDER=${ctx.NPM_PACKAGE_FOLDER}  -f ${ctx.env.WORKSPACE}/ci/.ci/web/Dockerfile . --no-cache \
                            --push
                            """
            } else if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
                def exposePort = "${ctx.SHELL_HOST_PORT}"
                if ("${ctx.SHELL_PARAMS_ARRAY.length}" == '7') {
                    exposePort = "${ctx.SHELL_HOST_PORT} ${ctx.SHELL_EXTEND_PORT}"
                }
                exposePort = "${ctx.IS_PROD}" == 'true' ? "${exposePort}" : "${exposePort} 5005"
                if ("${ctx.COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
                    ctx.sh """ cd ${ctx.mavenPackageLocationDir} && pwd &&
                            docker buildx build --platform linux/amd64  -t ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --build-arg DEPLOY_FOLDER="${ctx.DEPLOY_FOLDER}" \
                            --build-arg PROJECT_NAME="${ctx.PROJECT_NAME}"  --build-arg EXPOSE_PORT="${exposePort}" \
                            --build-arg JDK_VERSION=${ctx.JDK_VERSION}  -f ${ctx.env.WORKSPACE}/ci/.ci/Dockerfile . --no-cache \
                            --push
                            """
                }
            }

            ctx.println("构建镜像上传完成后删除本地镜像")
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
            ctx.sh """  docker login ${ctx.DOCKER_REPO_REGISTRY} --username=${ctx.DOCKER_HUB_USER_NAME} --password=${ctx.DOCKER_HUB_PASSWORD}
                        docker push ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName}
                        docker rmi ${ctx.DOCKER_REPO_REGISTRY}/${imageFullName} --no-prune
                   """
        }
    }

    /**
     *  拉取远程仓库Docker镜像
     */
    static def pull(ctx, imageName) {
        def imageFullName = "${ctx.DOCKER_REPO_NAMESPACE}/${imageName}:${imageTag}"
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_REPO_CREDENTIALS_ID}", usernameVariable: 'DOCKER_HUB_USER_NAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]) {
            ctx.sh """     
                       ssh  ${ctx.remote.user}@${ctx.remote.host} \
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
