1. **yum 安装k8s软件**
```yum
yum install -y kubelet-1.20.0 kubectl-1.20.0 kubeadm-1.20.0
```
2. **在master执行初始化节点**
```init
kubeadm init --kubernetes-version=1.20.0  --apiserver-advertise-address=10.0.3.166 --image-repository registry.aliyuncs.com/google_containers --service-cidr=192.168.0.1/16 --pod-network-cidr=10.10.0.0/16 --ignore-preflight-errors=Swap,NumCPU
```
> 初始化过程会遇到提示Cgroup必须为systemd的问题，修改方法如下:

```code
通过 docker info | grep Cgroup查看当前Cgroup
通过修改/etc/docker/daemon.json 文件，加入以下内容
{
  "exec-opts": ["native.cgroupdriver=systemd"]
}
然后执行systemctl restart docker
初始化过程遇到提示未关闭交换内存
running with swap on is not supported. Please disable swap，修改方法如下:
```swap
swapoff -a && sed -i '/swap/d' /etc/fstab
```

> 初始化成功会出现如下提示

```success
Your Kubernetes control-plane has initialized successfully!

To start using your cluster, you need to run the following as a regular user:

  1. mkdir -p $HOME/.kube
  2. sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
  3. sudo chown $(id -u):$(id -g) $HOME/.kube/config

You should now deploy a pod network to the cluster.
4. Run "kubectl apply -f [podnetwork].yaml" with one of the options listed at:
  https://kubernetes.io/docs/concepts/cluster-administration/addons/

Then you can join any number of worker nodes by running the following on each as root:

kubeadm join 10.0.3.166:6443 --token 1laxen.38bzx8hzul2ikbfk \
    --discovery-token-ca-cert-hash sha256:2df1e08577b6f8671bb19a7eaa2bdb9142040d370dae282d94b3001cf61619ab

按上述成功的步骤执行上述1-3步骤的语句，其中第4句是进行k8s的网络设置,暂时先不要执行等后面flannel组件安装完成后才需要执行。
1-3步骤执行完毕后，查看k8s的master节点的组件运行情况
```
3 **执行kubectl get cs**
```kubectl
NAME                 STATUS      MESSAGE                                                                                     ERROR
controller-manager   Unhealthy   Get http://127.0.0.1:10252/healthz: dial tcp 127.0.0.1:10252: connect: connection refused
scheduler            Unhealthy   Get http://127.0.0.1:10251/healthz: dial tcp 127.0.0.1:10251: connect: connection refused
etcd-0               Healthy     {"health":"true"}
会发现controller-manager和scheduler连接有问题，排查是否本地10252,10251端口未启动。
修改/etc/kubernetes/manifests/下的kube-controller-manager.yaml,kube-scheduler.yaml配置文件
去掉配置中的 --port=0的配置
然后再重启systemctl restart kubelet
重启完毕后，再执行kubectl get cs,会显示如下:
[root@master k8s_airflow]# kubectl get cs
Warning: v1 ComponentStatus is deprecated in v1.19+
NAME                 STATUS    MESSAGE             ERROR
controller-manager   Healthy   ok                  
scheduler            Healthy   ok                  
etcd-0               Healthy   {"health":"true"}
```
4 **使用flannel的进行K8S网络的设置**
- 获取kube-flannel.yml
```wget
wget https://raw.githubusercontent.com/coreos/flannel/master/Documentation/kube-flannel.yml
```
- 修改kube-flannel.yml
```
改Network配置项
"Network": "10.10.0.0/16"
```
- kubectl apply -f kube-flannel.yml
- 查看kubectl get pod -n kube-flannel
```
[root@master k8s_airflow]# kubectl get pod -n kube-flannel -o wide
NAME                    READY   STATUS    RESTARTS   AGE     IP           NODE     NOMINATED NODE   READINESS GATES
kube-flannel-ds-hp4jl   1/1     Running   0          5d15h   10.0.3.166   master   <none>           <none>
kube-flannel-ds-jjmfc   1/1     Running   0          5d15h   10.0.3.165   cdh3     <none>           <none>
kube-flannel-ds-wdjpp   1/1     Running   0          5d15h   10.0.3.164   cdh2     <none>           <none>
```
- 查看kubectl get pod -n kube-system,会发现coredns正常运行
```
[root@master k8s_airflow]# kubectl get pod -n kube-system -o wide
NAME                             READY   STATUS    RESTARTS   AGE     IP           NODE     NOMINATED NODE   READINESS GATES
coredns-7f89b7bc75-67ktr         1/1     Running   0          5d15h   10.10.1.2    cdh2     <none>           <none>
coredns-7f89b7bc75-hhzk2         1/1     Running   0          5d15h   10.10.2.3    cdh3     <none>           <none>
etcd-master                      1/1     Running   0          5d15h   10.0.3.166   master   <none>           <none>
kube-apiserver-master            1/1     Running   2          5d15h   10.0.3.166   master   <none>           <none>
kube-controller-manager-master   1/1     Running   0          5d15h   10.0.3.166   master   <none>           <none>
kube-proxy-4vd7q                 1/1     Running   0          5d15h   10.0.3.165   cdh3     <none>           <none>
kube-proxy-7kh2m                 1/1     Running   0          5d15h   10.0.3.164   cdh2     <none>           <none>
kube-proxy-tlkmm                 1/1     Running   0          5d15h   10.0.3.166   master   <none>           <none>
kube-scheduler-master            1/1     Running   0          5d15h   10.0.3.166   master   <none>           <none>
```
- 这里有个问题，如果发现pod之间无法相互访问或者宿主机无法访问其他宿主机的容器IP，有可能是flannel的upd端口8472变屏蔽了需要开启此端口

5 **节点加入**
> 由于网络组件安装好了，就要进行节点加入

- 在node1 和node2节点执行,k8s的集群有多少个节点就都要执行
```
kubeadm join 10.0.3.166:6443 --token 1laxen.38bzx8hzul2ikbfk    --discovery-token-ca-cert-hash sha256:2df1e08577b6f8671bb19a7eaa2bdb9142040d370dae282d94b3001cf61619ab
```
- 如果master节点的token过期
```
#得到token
[root@master k8s]# kubeadm token create 
bjjq4p.4c8ntpy20aoqptmi
#得到discovery-token-ca-cert-hash
[root@master k8s]# openssl x509 -pubkey -in /etc/kubernetes/pki/ca.crt | openssl rsa -pubin -outform der 2>/dev/null | openssl dgst -sha256 -hex | sed 's/^.* //'
2df1e08577b6f8671bb19a7eaa2bdb9142040d370dae282d94b3001cf61619ab
```
- 加入完毕后查看节点,等待节点都处于Ready状态
```
kubectl get nodes
[root@master k8s]# kubectl get nodes
NAME     STATUS     ROLES    AGE     VERSION
cdh2     NotReady   <none>   38s     v1.19.0
cdh3     Ready      <none>   20m     v1.19.0
master   Ready      master   7d21h   v1.19.0
```

6 **ingress-controller部署**
- 获取ingress-controller配置文件
```
wget https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v0.48.1/deploy/static/provider/baremetal/deploy.yaml .
```
- 部署环境为私有环境没有lb 所有把Deployment 改成DaemonSet
- 安装ingress-controller
```
[root@node1 ingress]# kubectl apply -f deploy.yaml 
namespace/ingress-nginx created
serviceaccount/ingress-nginx created
configmap/ingress-nginx-controller created
clusterrole.rbac.authorization.k8s.io/ingress-nginx created
clusterrolebinding.rbac.authorization.k8s.io/ingress-nginx created
role.rbac.authorization.k8s.io/ingress-nginx created
rolebinding.rbac.authorization.k8s.io/ingress-nginx created
service/ingress-nginx-controller-admission created
service/ingress-nginx-controller created
deployment.apps/ingress-nginx-controller created
validatingwebhookconfiguration.admissionregistration.k8s.io/ingress-nginx-admission created
serviceaccount/ingress-nginx-admission created
clusterrole.rbac.authorization.k8s.io/ingress-nginx-admission created
clusterrolebinding.rbac.authorization.k8s.io/ingress-nginx-admission created
role.rbac.authorization.k8s.io/ingress-nginx-admission created
rolebinding.rbac.authorization.k8s.io/ingress-nginx-admission created
job.batch/ingress-nginx-admission-create created
job.batch/ingress-nginx-admission-patch created
```
- 查看pod状态,由于deploy.yaml文件我将容器 hostNetwork设置为true,因此ingress-nginx-controller的IP为宿主机的IP，否则会是容器内部的IP
```
[root@master k8s_airflow]# kubectl get pod -n ingress-nginx -o wide
NAME                                   READY   STATUS      RESTARTS   AGE   IP           NODE   NOMINATED NODE   READINESS GATES
ingress-nginx-admission-create-8skkf   0/1     Completed   0          58m   10.10.2.30   cdh3   <none>           <none>
ingress-nginx-admission-patch-4r5kb    0/1     Completed   0          58m   10.10.1.13   cdh2   <none>           <none>
ingress-nginx-controller-4nspx         1/1     Running     0          26m   10.0.3.164   cdh2   <none>           <none>
ingress-nginx-controller-p548w         1/1     Running     0          25m   10.0.3.165   cdh3   <none>           <none>
```
- 查看svc状态,SVC是 nodeport,因此当访问宿主的时候，应该是宿主机的32202端口对应的是pod容器内的80端口
```
[root@master k8s_airflow]# kubectl get svc -n ingress-nginx -o wide
NAME                                 TYPE        CLUSTER-IP        EXTERNAL-IP   PORT(S)                      AGE   SELECTOR
ingress-nginx-controller             NodePort    192.168.206.166   <none>        80:32202/TCP,443:30702/TCP   60m   app.kubernetes.io/component=controller,app.kubernetes.io/instance=ingress-nginx,app.kubernetes.io/name=ingress-nginx
ingress-nginx-controller-admission   ClusterIP   192.168.221.2     <none>        443/TCP                      60m   app.kubernetes.io/component=controller,app.kubernetes.io/instance=ingress-nginx,app.kubernetes.io/name=ingress-nginx
```
