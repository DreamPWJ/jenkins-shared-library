### 初始化K8S集群环境

- kubeadm 参考文档: https://kubernetes.io/zh-cn/docs/reference/setup-tools/kubeadm/
- microk8s 参考文档: https://mp.weixin.qq.com/s/t6TNeLTeyvrfBhXxB-texQ


#### 公网域名访问方案 流量路径

公网用户
↓
DNS: example.com → 123.45.67.89 (你的公网 IP)
↓
路由器端口转发: 123.45.67.89:80 → 192.168.1.240:80
↓
MetalLB 提供的 IP: 192.168.1.240
↓
Ingress Controller
↓
根据域名路由到对应的 Service
↓
Pod