#!/usr/bin/env bash
# Author: 潘维吉
# 卸载Kubernetes

echo "清理运行到k8s群集中的pod"
kubectl delete node --all

echo "主机系统中删除数据卷和备份"
for service in kube-apiserver kube-controller-manager kubectl kubelet kube-proxy kube-scheduler; do
  systemctl stop $service
done
yum -y remove kubernetes #if it's registered as a service

kubeadm reset -f
modprobe -r ipip
lsmod
rm -rf ~/.kube/
rm -rf /etc/kubernetes/
rm -rf /etc/systemd/system/kubelet.service.d
rm -rf /etc/systemd/system/kubelet.service
rm -rf /usr/bin/kube*
rm -rf /etc/cni
rm -rf /opt/cni
rm -rf /var/lib/etcd
rm -rf /var/etcd

yum list installed | grep kubernetes
yum -y remove cri-tools.x86_64 kubeadm.x86_64 kubectl.x86_64 kubelet.x86_64 kubernetes-cni.x86_64
