### FRP内网穿透

- 代码地址: https://github.com/fatedier/frp
- 配置后台运行和开机自启动文档: https://gofrp.org/zh-cn/docs/setup/systemd/
- 自定义后台运行和开机自启动目录: /etc/systemd/system/

# 启动frp
sudo systemctl start frps
# 停止frp
sudo systemctl stop frps
# 重启frp
sudo systemctl restart frps
# 查看frp状态
sudo systemctl status frps
# 禁用frp状态
sudo systemctl disable frps

- 启动服务器：./frps -c ./frps.toml
- 启动客户端：./frpc -c ./frpc.toml