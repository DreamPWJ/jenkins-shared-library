:: 本地变更Windows hosts映射

@echo off
chcp 65001 > NUL
setlocal enabledelayedexpansion
:: 检查是否是管理员权限，如果不是则重新以管理员身份运行
NET SESSION >nul 2>&1
if %errorlevel% neq 0 (
    echo 当前没有管理员权限，正在以管理员身份重新启动脚本...
    :: 以管理员权限重新启动该批处理文件
    powershell -Command "Start-Process cmd -ArgumentList '/c %~s0' -Verb runAs"
    exit /b
)
echo 已经获取管理员权限

:: 定义文件路径
set hostsFile=%SystemRoot%\System32\drivers\etc\hosts
set backupFile=%SystemRoot%\System32\drivers\etc\host.bak

echo hostsFile

:: 复制原始 hosts 文件为 hosts.bak
copy "%hostsFile%" "%backupFile%"

:: 检查是否成功复制
if exist "%backupFile%" (
    echo hosts 文件已成功备份为 host.bak

    :: 清空原有内容并写入新的内容
    echo 192.168.0.100 jtss.rzbus.cn > "%hostsFile%"
    :: 这是换行注释
    :: echo. >> "%hostsFile%"  
    :: echo 192.168.1.135 t2.com >> "%hostsFile%"
    echo 停止dns服务
    net stop dnscache
    echo 重启dns服务
    net start dnscache

    echo 替换完成
) else (
    echo 备份失败，请检查文件权限或路径。
)

endlocal
pause
