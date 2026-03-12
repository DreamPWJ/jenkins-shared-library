### 企业级VPN实现所有节点互联互通

 - 技术方案: OpenVPN的Pritunl页面端管理
 - 服务安装: https://docs.pritunl.com/docs/installation?utm_source=chatgpt.com#other-providers-ubuntu-2404

#### 如果VPN网关和其他机器网络通 不需要配置VPN客户端 直接访问原先的内网地址即可  如果不通多个机器也只需配置一个VPN客户端

#### 服务器VPN客户端配置

apt install -y openvpn
openvpn --config client.ovpn --daemon
