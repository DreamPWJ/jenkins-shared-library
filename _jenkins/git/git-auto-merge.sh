#!/bin/bash
# Author: 潘维吉
# Description:  Git自动merge脚本

RED_COLOR='\E[1;31m'  #红
GREEN_COLOR='\E[1;32m' #绿
YELOW_COLOR='\E[1;33m' #黄
BLUE_COLOR='\E[1;34m'  #蓝
PINK='\E[1;35m'      #粉红
RES='\E[0m'

#退出程序
function goto_exit()
{
    echo -e "${RED_COLOR} $1 ${RES}"
    read -p "按任意键关闭" -n 1
    exit $2
}

#获取当前分支的冲突检查情况
function check_conflict()
{
    git --no-pager diff --check
    if [ $? = 0 ]; then
        exit 0
    else
        echo 还有冲突未解决
        exit 1
    fi
}

#检测冲突
function last_status()
{
    if [ $? -eq 0 ]
    then
        return 0
    else
        #exit -2
        goto_exit "执行异常[产生冲突或其他原因]" -2
    fi
}

#切换分支并拉取代码
function pull_latest_branch()
{
    echo -e "切换并拉取${GREEN_COLOR} $1 ${RES} 分支最新代码"
#     如果有两个人共同用一个分支开发，他刚更新的代码在测试上没有问题，过一段时间测试完成了，通知运维人员可以上预发布环境了，
#     但是这个时候该功能分支的另外一个正好又提交了一个commit，这时会把最新的版本也合并了，
#     说了半天，才说到重要，也就是每次合并之前都会有个pull --rebase操作
#    if git checkout $1 && git pull --rebase
#    then
#        last_status
#    fi
    if git checkout $1 && git pull
    then
        last_status
    fi
}

#拉取远程分支
function check_branch()
{
    echo -e "检出切换到本地${GREEN_COLOR} $1 ${RES} 分支"
    if git branch | grep -q $1
    then
        return 0
    else
        echo "本地没有此分支, 从远程分支上拉取${GREEN_COLOR} $1 ${RES}到本地"
        pull_latest_branch
        if git branch -rv | grep $1
        then
            git checkout -b $1 origin/$1
        fi
    fi
}

# $1表示要合并到的分支[目标分支]，$2表示要合并的分支
function merge_branch()
{
    #拉取代码
    if git checkout $1 && pull_latest_branch $1
    then
        last_status
    fi
    echo -e "合并 ${GREEN_COLOR} $2 ${RES} 分支 到 ${GREEN_COLOR} $1 ${RES} 分支"
    #git merge --no-edit参数能够用于接受自动合并的信息（通常情况下并不鼓励这样做）
    #合并分支
    if git merge --no-edit $2
    then
        last_status
    fi
}

# 接收一个参数，要推送的目标分支
function push_branch()
{
    echo -e "开始push推送合并代码到远端 ${GREEN_COLOR} $1 ${RES}"
    if git push origin $1 && git status
    then
        last_status
    fi
}

function show_commit_id()
{
    git log | head -5
}

function new_line()
{
    echo
}

function echo_line()
{
    echo -e "${GREEN_COLOR} ---------------------------- ${RES}"
}

#*********************************
# LOCAL_BRANCH 表示功能分支
# TARGET_BRANCH 表示要合并到的分支
#*********************************

############################处理逻辑开始##############################

if [ -n "$(git status -s)" ];then
    goto_exit "有文件变更，请先处理，再执行" -2
fi

echo 1.dev_ca_common合并到dev_v1.0.0
read -p "请选择：" input
case $input in
[1])
    LOCAL_BRANCH=dev_ca_common
    TARGET_BRANCH=dev_v1.0.0
;;
[2])
    goto_exit "退出" -2
;;
*)
#默认执行命令
goto_exit "退出" -2
esac

echo -e "${RED_COLOR}当前摘取的分支是:${RES} $LOCAL_BRANCH"
echo -e "${RED_COLOR}要合并到的分支是:${RES} $TARGET_BRANCH"
check_branch $LOCAL_BRANCH
new_line

#echo -e "拉取${GREEN_COLOR} $LOCAL_BRANCH${RES} 分支最新代码"
#拉取
pull_latest_branch $LOCAL_BRANCH
new_line

#echo -e "切换到${GREEN_COLOR} $TARGET_BRANCH ${RES}分支，拉取${GREEN_COLOR} $TARGET_BRANCH${RES} 分支最新代码，合并 ${GREEN_COLOR} $LOCAL_BRANCH ${RES} 分支 到 ${GREEN_COLOR} $TARGET_BRANCH ${RES}分支"
#拉取+合并
merge_branch $TARGET_BRANCH $LOCAL_BRANCH

#推送
push_branch $TARGET_BRANCH
new_line

echo -e "${GREEN_COLOR}------------------ COMMIT ID ------------------${RES}"
show_commit_id
read -p "按任意键关闭" -n 1
