#!/usr/bin/env groovy
package shared.library

//import jenkins.model.Jenkins

import java.util.regex.Matcher
import java.util.regex.Pattern

//@Grab('org.apache.commons:commons-lang3:3.10+')
//import org.apache.commons.lang.time.StopWatch


/**
 * @author 潘维吉
 * @date 2020/10/9 9:20
 * @email 406798106@qq.com
 * @description 工具类*  实现序列化是为了pipeline被jenkins停止重启后能正确恢复
 * 使用引入 import shared.library.Utils
 */
class Utils implements Serializable {

    /**
     * 获取 shell 命令输出内容方法
     */
    static def getShEchoResult(ctx, cmd) {
        def getShEchoResultCmd = "ECHO_RESULT=`${cmd}`\necho \${ECHO_RESULT}"
        return ctx.sh(
                script: getShEchoResultCmd,
                returnStdout: true,
                encoding: 'UTF-8'
        ).trim()
    }

    /**
     * 格式化时间
     */
    static def formatDate(String format = 'yyyy-MM-dd HH:mm') {
        return new Date().format(format)
    }

    /**
     * 语义化版本自动生成器
     */
    static def genSemverVersion(String versionNum = '1.0.0', type = GlobalVars.gitCommitFix) {
        /*  语义化主版本 major 版本Git提交必须以 BREAKING CHANGE:开头
            语义化次版本 minor 版本Git提交必须以 feat: 开头
            语义化补丁版 patch 版本Git提交必须以 fix: 开头
            语义化测试版 如 2.1.0-beta.2 , Git提交必须含有alpha、beta、rc */
        try {
            versionNum = versionNum.replaceAll("v", "").replaceAll("V", "") // 去掉前缀
            def regex = '^(([0-9]|([1-9]([0-9]*))).){2}([0-9]|([1-9]([0-9]*)))([-](([0-9A-Za-z]|([1-9A-Za-z]([0-9A-Za-z]*)))[.]){0,}([0-9A-Za-z]|([1-9A-Za-z]([0-9A-Za-z]*)))){0,1}([+](([0-9A-Za-z]{1,})[.]){0,}([0-9A-Za-z]{1,})){0,1}$'
            if (isRegexMatcher(regex, versionNum)) {
                def version = versionNum.split("\\.")
                if (type == GlobalVars.gitCommitFeature) {
                    version[1] = version[1].toInteger() + 1
                    version[2] = 0
                } else if (type == GlobalVars.gitCommitFix) {
                    version[2] = version[2].toInteger() + 1
                }
                return version.join(".")
            } else {
                return "1.0.0"
            }
        } catch (e) {
            return "1.0.0"
        }
    }

    /**
     * 获取时间差 并且格式化
     */
    static def getTimeDiff(start, end) {
        def totalSeconds = ((end.getTime() - start.getTime()) / 1000) as int
        if (totalSeconds == 0) {
            totalSeconds = (totalSeconds + 1) // 防止出现0s情况
        }
        int seconds = totalSeconds % 60
        int minutes = totalSeconds / 60
        if (totalSeconds < 60) {
            return totalSeconds + "s"
        } else {
            return minutes + "min" + seconds + "s"
        }
    }

    /**
     * 通用正则校验
     */
    static boolean isRegexMatcher(String regex, String matcher) {
        //String regex = '^((13[0-9])|(14[5,7])|(15[0-3,5-9])|(17[0,3,5-8])|(18[0-9])|166|198|199|(147))\\d{8}$'
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        Matcher m = p.matcher(matcher)
        return m.matches()
    }

    /**
     * 首字母大写的方法
     */
    static firstWordUpperCase(value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase()
    }

    /**
     * 首字母小写的方法
     */
    static firstWordLowerCase(value) {
        return value.substring(0, 1).toLowerCase() + value.substring(1)
    }

    /**
     * 获取文件大小
     */
    static getFileSize(ctx, fileLocation) {
        try {
            def size = ctx.sh(returnStdout: true, script: " ls -lh $fileLocation | awk '{print \$5}' ")
            // return ((Double) "${size}".toInteger() / 1024 / 1024).round(2) + " MB"
            return size
        } catch (e) {
            ctx.println("获取文件大小失败")
            return "未知"
        }
    }

    /**
     * 获取文件夹大小
     */
    static getFolderSize(ctx, folderLocation) {
        try {
            def sizeInfo = ctx.sh(returnStdout: true, script: " du -sh  $folderLocation ")
            def size = sizeInfo.toString().replaceAll(folderLocation, "")
            return size
        } catch (e) {
            ctx.println("获取文件夹大小失败")
            return "未知"
        }
    }

    /**
     * 获取文件大小 kb转mb
     */
    static kbConvertMb(size) {
        return ((Double) "${size}".toInteger() / 1024 / 1024).round(2) + " MB"
    }

    /**
     * 获取当前系统时间的星期
     */
    static def getWeek(ctx) {
        def week = ctx.sh(returnStdout: true, script: " date +%w ")
        switch (week.toInteger()) {
            case 0:
                return "周日"
            case 1:
                return "周一"
            case 2:
                return "周二"
            case 3:
                return "周三"
            case 4:
                return "周四"
            case 5:
                return "周五"
            case 6:
                return "周六"
            default:
                return "未知"
        }
    }

    /**
     * 获取系统CPU使用率
     */
    static getCPURate(ctx) {
        // -t 解决 TERM environment variable not set.
        // export LC_ALL=C  解决 bash: warning: setlocale: LC_ALL: cannot change locale (zh_CN.UTF-8)
        def rateInfo = ctx.sh(returnStdout: true,
                script: " ssh -t ${ctx.remote.user}@${ctx.remote.host} 'export LC_ALL=C && \$[100-\$(vmstat 1 2|tail -1|awk '{print \$15}')]' ")
        return rateInfo
    }

    /**
     * Shell通过端口号获取进程PID
     */
    static getPIDByPort(ctx, port) {
        def pId = ctx.sh(returnStdout: true, script: " netstat -anp|grep $port|awk '{printf \$7}'|cut -d/ -f1 ")
        return pId
    }

    /**
     * 获取版本号方法
     */
    static def getVersionNum(String versionNum = '') {
        return new Date().format('yyyy-MM-dd') + "${versionNum}"
    }

    /**
     *  更新当前任务jenkins内的构建号码
     *  import jenkins.model.Jenkins
     */
/*    static def updateNextBuildNumber(String jobName, int num) {
        def item = Jenkins.instance.getItemByFullName(jobName)
        item.nextBuildNumber = num
        item.save()
    }*/

    /**
     *  删除指定的构建
     */
/*    static def deleteBuild(String jobName, int num) {
        Jenkins.instance.getItemByFullName(jobName).builds.findAll { it.number == num }.each { it.delete() }
    }*/

    /**
     *  终止Jenkins队列中某Job的所有排队任务
     *  import hudson.model.*
     */
/*    static def cancelTask(jobName) {
        def queue = Jenkins.instance.queue
        queue.items.findAll { it.task.name.startsWith(jobName) }.each { queue.cancel(it.task) }
    }*/

    /**
     * Gradle构建动态命令
     * 使用Utils.gradleBuild(this, "clean build")
     */
/*    static def gradleBuild(ctx, tasks) {
        def gradleHome = ctx.tool 'Gradle6.6'
        ctx.sh = "echo 构建号码: ${env.BUILD_NUMBER}"
        ctx.timestamps {
            ctx.sh "${gradleHome}/bin/gradle ${tasks}"
        }
    }*/


}
