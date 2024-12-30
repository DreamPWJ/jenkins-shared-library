## Kubernetes 是一个开源容器编排平台，用于容器化应用的自动部署、自动容器编排、自动扩缩与自动修复等管理。它将构成应用的容器按逻辑单位进行分组以便于管理和发现

### yaml格式的pod定义文件完整内容：

apiVersion: v1 #必选，版本号，例如v1
kind: Pod #必选，Pod
metadata:       #必选，元数据
name: string #必选，Pod名称
namespace: string #必选，Pod所属的命名空间
labels:      #自定义标签

- name: string #自定义标签名字
  annotations:       #自定义注释列表
- name: string
  spec:         #必选，Pod中容器的详细定义
  containers:      #必选，Pod中容器列表

- name: string #必选，容器名称
  image: string #必选，容器的镜像名称
  imagePullPolicy: [Always | Never | IfNotPresent] #获取镜像的策略 Always表示总是下载最新镜像 IfNotPresent表示优先使用本地镜像，否则下载镜像，Never表示仅使用本地镜像
  command: [string]    #容器的启动命令列表，如不指定，使用打包时使用的启动命令
  args: [string]     #容器的启动命令参数列表
  workingDir: string #容器的工作目录
  volumeMounts:    #挂载到容器内部的存储卷配置
    - name: string #引用pod定义的共享存储卷的名称，需用volumes[]部分定义的的卷名
      mountPath: string #存储卷在容器内mount的绝对路径，应少于512字符
      readOnly: boolean #是否为只读模式
      ports:       #需要暴露的端口库号列表
    - name: string #端口号名称
      containerPort: int #容器需要监听的端口号
      hostPort: int #容器所在主机需要监听的端口号，默认与Container相同
      protocol: string #端口协议，支持TCP和UDP，默认TCP
      env:       #容器运行前需设置的环境变量列表
    - name: string #环境变量名称
      value: string #环境变量的值
      resources:       #资源限制和请求的设置
      limits:      #资源限制的设置
      cpu: string #Cpu的限制，单位为core数，将用于docker run --cpu-shares参数
      memory: string #内存限制，单位可以为Mib/Gib，将用于docker run --memory参数
      requests:      #资源请求的设置
      cpu: string #Cpu请求，容器启动的初始可用数量
      memory: string #内存请求，容器启动的初始可用数量
      livenessProbe:     #对Pod内个容器健康检查的设置，当探测无响应几次后将自动重启该容器，检查方法有exec、httpGet和tcpSocket，对一个容器只需设置其中一种方法即可
      exec:      #对Pod容器内检查方式设置为exec方式
      command: [string]  #exec方式需要制定的命令或脚本
      httpGet:       #对Pod内个容器健康检查方法设置为HttpGet，需要制定Path、port
      path: string
      port: number
      host: string
      scheme: string
      HttpHeaders:
        - name: string
          value: string
          tcpSocket:     #对Pod内个容器健康检查方式设置为tcpSocket方式
          port: number
          initialDelaySeconds: 0 #容器启动完成后首次探测的时间，单位为秒
          timeoutSeconds: 0 #对容器健康检查探测等待响应的超时时间，单位秒，默认1秒
          periodSeconds: 0 #对容器监控检查的定期探测时间设置，单位秒，默认10秒一次
          successThreshold: 0
          failureThreshold: 0
          securityContext:
          privileged:false
          restartPolicy: [Always | Never | OnFailure] #Pod的重启策略，Always表示一旦不管以何种方式终止运行，kubelet都将重启，OnFailure表示只有Pod以非0退出码退出才重启，Never表示不再重启该Pod
          nodeSelector: object #设置NodeSelector表示将该Pod调度到包含这个label的node上，以key：value的格式指定
          imagePullSecrets:    #Pull镜像时使用的secret名称，以key：secretKey格式指定
    - name: string
      hostNetwork:false #是否使用主机网络模式，默认为false，如果设置为true，表示使用宿主机网络
      volumes:       #在该pod上定义共享存储卷列表
    - name: string #共享存储卷名称 （volumes类型有很多种）
      emptyDir: {} #类型为emptyDir的存储卷，与Pod同生命周期的一个临时目录。为空值
      hostPath: string #类型为hostPath的存储卷，表示挂载Pod所在宿主机的目录
      path: string #Pod所在宿主机的目录，将被用于同期中mount的目录
      secret:      #类型为secret的存储卷，挂载集群与定义的secret对象到容器内部
      items:
        - key: string
          path: string
          configMap:     #类型为configMap的存储卷，挂载预定义的configMap对象到容器内部
          name: string
          items:
        - key: string

### 安装kubectl命令访问k8s集群  环境变量Path中 路径为kubectl.exe所在的文件夹目录并双击运行kubectl.exe

- https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
- https://kubernetes.io/docs/tasks/tools/install-kubectl-windows/

### 安装Helm包管理工具  设置环境变量在helm.exe目录

- https://github.com/helm/helm/releases

### K8S集群使用 cert-manager基于 ACME 协议与 Let's Encrypt 自动签发与续签免费的SSL证书 [文档](https://help.aliyun.com/document_detail/409430.html)
#### Let's Encrypt限制 每个 IP 地址每 3 小时最多可以请求 5 个证书 超出请分批执行创建证书 

- kubectl create namespace cert-manager
- helm repo add jetstack https://charts.jetstack.io
- helm repo update

### ！！！注意cert-manager版本要和K8S版本匹配  比如1.7.0以上开启ServerSideApply影响Secret证书生成  ServerSideApply是k8s的v1.22版本生产可用

- helm install cert-manager jetstack/cert-manager --namespace cert-manager --version v1.6.3 --set startupapicheck.timeout=5m --set installCRDs=true
- kubectl get pods --namespace cert-manager
- helm uninstall cert-manager -n cert-manager
- kubectl get cert 和 kubectl get cert CERT-NAME -o yaml
- 如果SSL证书不生效删除ClusterIssuer重新再创建 域名解析ip正确 ingressClassName指定正确 或重命名cert-manager-ingress.yaml文件内的命名 

### K8S集群安装 Prometheus与安装 Grafana监控

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/prometheus

helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install grafana grafana/grafana --set persistence.enabled=false --set service.type=NodePort --set
service.nodePort=30000

### 基于Ansible自动部署K8S集群 [项目](https://github.com/lizhenliang/ansible-install-k8s)

### 通过kubectl创建简单nginx服务 [文档](https://docs.ksyun.com/documents/5517)

kubectl run my-nginx --image=nginx --replicas=3 --port=80

### 创建镜像私有库密钥ImagePullSecrets

kubectl create secret docker-registry <name> --docker-server=DOCKER_REGISTRY_SERVER --docker-username=DOCKER_USER
--docker-password=DOCKER_PASSWORD --docker-email=DOCKER_EMAIL

### 使用K8S集群内全域名访问 K8S内置Core DNS解析  因为集群内网ClusterIP如果Service被删除会变化 域名可应对变化

示例 如 http://k8s-service-name.default.svc.cluster.local:8080

### 在IDEA内查看K8S容器日志乱码问题

idea64.exe.vmoptions 配置文件中添加 -Dfile.encoding=UTF-8 即可解决

### K8S扩缩容和重启Pod命令

kubectl scale deployment deploymentName --replicas=0
kubectl scale deployment deploymentName --replicas=3