1. **创建namespace,便于管理整个airflow在k8s中的集群,这里创建一个airflow-server名称的命名空间**
namespace.yaml
```
apiVersion: v1
kind: Namespace
metadata:
  name: airflow-server
```
执行 kubectl apply -f namespace.yaml
查看命名空间,可以看到airflow-server创建成功
```
[root@master k8s_airflow]# kubectl get ns
NAME              STATUS   AGE
airflow-server    Active   31h
default           Active   5d20h
ingress-nginx     Active   6h6m
kube-flannel      Active   5d20h
kube-node-lease   Active   5d20h
kube-public       Active   5d20h
kube-system       Active   5d20h
```

2. **由于airflow需要用到外部系统的mysql和redis,因此要先部署关于pod容器对mysql和redis访问的服务**
mysql.yaml

```
kind: Service
apiVersion: v1
metadata:
  name: mysql
  namespace: airflow-server
spec:
  ports:
    - protocol: TCP
      port: 3306
      targetPort: 3306

---
kind: Endpoints
apiVersion: v1
metadata:
  name: mysql
  namespace: airflow-server
subsets:
  - addresses:
      - ip: 10.0.3.166
    ports:
      - port: 3306
```
redis.yaml
```
kind: Service
apiVersion: v1
metadata:
  name: redis-service
  namespace: airflow-server
spec:
  ports:
    - protocol: TCP
      port: 6380
      targetPort: 6380

---
kind: Endpoints
apiVersion: v1
metadata:
  name: redis-service
  namespace: airflow-server
subsets:
  - addresses:
      - ip: 10.0.3.163
    ports:
      - port: 6380
```
>执行kubectl apply -f mysql.yaml以及kubectl apply -f redis.yaml
查看svc,这个时候会看到创建了2个svc,到时候可以通过名称mysql或者192.168.23.1就可以访问到外部的mysql,
这里建议使用名称mysql来访问，因为svc每次重新生成的ip会变，这样以后代码也得跟着变，除非固定住，但这个不是推荐的写法。redis的访问同理。
通过这步骤就可以在代码里面使用mysql:3306,redis:6380作为配置,就可以访问

```
[root@master k8s_airflow]# kubectl get svc -n airflow-server -o wide
NAME                        TYPE        CLUSTER-IP        EXTERNAL-IP   PORT(S)    AGE     SELECTOR
mysql                       ClusterIP   192.168.23.1      <none>        3306/TCP   3h15m   <none>
redis-service               ClusterIP   192.168.20.80     <none>        6380/TCP   3h14m   <none>
```

3  **airflow-webserver的服务部署**

airflow-webserver.yaml
> service 创建说明
由于airflow-webserver需要对外可以访问，因此定义了一个Service: airflow-webserver-service
其中port:8080 为这个service的端口，targetPort为目标服务端口，即后端服务的端口
selector:
  app: airflow-webserver-pod
为后端的pod容器，这样这个service就代理了airflow-webserver的服务了，到时候只需要访问
http://airflow-webserver-service:8080 即可访问airflow的web界面


> pod 容器创建说明
1.这边可以看到配置文件中声明的关于mysql和redis配置，这个就是第二步我们创建的svc的作用在这里体现出来了，直接通过名称访问，而不需要ip
mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
redis://redis-service:6380/0
2.env: 是用来声明环境变量，到时候会覆盖airflow中的一些配置
3.探测配置
startupProbe 判断容器是否启动后的一次探测，探测成功后就不会继续探测了
readinessProbe 判断容器是否就绪的探测，会根据配置文件一直循环探测，直到容器销毁，执行顺序位于starupProbe之后
4.安全设置，容器启动用户，这里记得要用uid，因为同一个用户名称，不同宿主机的uid不一致，而linux用户权限是根据uid走的，因此这里设置airflow的用户50000是，容器内部airflow的用户UID是50000，如果宿主机的airflow用户uid不是50000，可以通过usermod -u 50000 airflow,进行修改
5.目录挂载
将宿主机的dags目录和logs目录挂载到容器中
6.配置启动命令
args: ["webserver"]
此命令即告诉airflow在容器启动的时候执行airflow webserver 服务，这样就可以启动webserver的服务了
7.配置副本数
replicas: 1 #即启动1个pod容器

```
apiVersion: v1
kind: Service
metadata:
  name: airflow-webserver-service
  namespace: airflow-server
  labels:
    app: airflow-webserver-service
spec:
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: airflow-webserver-pod
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: airflow-webserver
  namespace: airflow-server
spec:
  replicas: 1
  selector:
    matchLabels:
      app: airflow-webserver-pod
  template:
    metadata:
      labels:
        app: airflow-webserver-pod
    spec:
      terminationGracePeriodSeconds: 10
      containers:
        - name: airflow-webserver
          image: apache/airflow:2.4.3
          securityContext:
            runAsUser: 50000
          args: ["webserver"]
          env:
            - name: AIRFLOW__CORE__EXECUTOR
              value: CeleryExecutor
            - name: AIRFLOW__DATABASE__SQL_ALCHEMY_CONN
              value: mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
            - name: AIRFLOW__CORE__SQL_ALCHEMY_CONN
              value: mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
            - name: AIRFLOW__CELERY__RESULT_BACKEND
              value: redis://redis-service:6380/0
            - name: AIRFLOW__CELERY__BROKER_URL
              value: redis://redis-service:6380/0
            - name: AIRFLOW__CORE__LOAD_EXAMPLES
              value: 'true'
          ports:
          - containerPort: 8080
          startupProbe:
            httpGet:
              scheme: HTTP
              path: /health
              port: 8080
            initialDelaySeconds: 20
            failureThreshold: 60
            periodSeconds: 5
          readinessProbe:
            httpGet:
              scheme: HTTP
              path: /health
              port: 8080
            periodSeconds: 10 #每次执行liveness探测的时间间隔
            timeoutSeconds: 5     #liveness探测成功的次数；如果成功1次，就表示容器正常    
            successThreshold: 1   #liveness探测成功的次数；如果成功1次，就表示容器正常
            failureThreshold: 5  #liveness探测失败的次数；如果连续5次失败，就会杀掉进程重启容器
          volumeMounts:
          - mountPath: /opt/airflow/dags
            name: airflow-dags
          - mountPath: /opt/airflow/logs
            name: airflow-logs
      volumes:
      - name: airflow-logs
        hostPath:
          path: /data/program/k8s/k8s_airflow/logs
      - name: airflow-dags
        hostPath:
          path: /data/program/k8s/k8s_airflow/dags
```
> 执行kubectl apply -f airflow-webserver.yaml

```
查看deployment
[root@master k8s_airflow]# kubectl get deployment -n airflow-server
NAME                READY   UP-TO-DATE   AVAILABLE   AGE
airflow-webserver   1/1     1            1           3h32m

查看svc
[root@master k8s_airflow]# kubectl get svc -n airflow-server
NAME                        TYPE        CLUSTER-IP        EXTERNAL-IP   PORT(S)    AGE
airflow-webserver-service   ClusterIP   192.168.150.184   <none>        8080/TCP   20h
mysql                       ClusterIP   192.168.23.1      <none>        3306/TCP   4h
redis-service               ClusterIP   192.168.20.80     <none>        6380/TCP   4h

查看pod
[root@master k8s_airflow]# kubectl get pod -n airflow-server
NAME                                 READY   STATUS    RESTARTS   AGE
airflow-webserver-698bdb87bd-v9476   1/1     Running   0          113m

查看容器日志
kubectl logs -f airflow-webserver-698bdb87bd-v9476 -n airflow-server
```

> 这个时候还只能内部访问airflow-webserver,还不能外部访问，这个时候就需要用到在部署k8s集群安装的ingress-controller,我们需要创建一个ingress

ingress.yaml,声明了一个域名deploy.ingress.com,后端是刚才声明的airflow-webserver-service,端口为8080
```
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ingress-airflow
  namespace: airflow-server
spec:
  rules:
    #定义域名
    - host: deploy.ingress.com
      http:
        paths:
        - path: /
          pathType: Prefix
          backend:
             service:
               name: airflow-webserver-service
               port:
                 number: 8080
```

> 执行kubectl apply -f ingress.yaml

```
查看ingress,由于我在部署ingress-controller的时候，设置了hostNetwork:true,因此address对应的IP直接是宿主主机的IP，因此可以通过绑定host,然后访问http://deploy.ingress.com即可访问airflow的web界面了。
[root@master k8s_airflow]# kubectl get ingress -n airflow-server -o wide
NAME              CLASS    HOSTS                ADDRESS                 PORTS   AGE
ingress-airflow   <none>   deploy.ingress.com   10.0.3.164,10.0.3.165   80      22h

如果没设置hostNetwork:true，则通过端口映射访问，还是绑定164或者165宿主机的host，然后访问http://deploy.ingress.com:32202，因为svc使用nodeport方式做了端口映射
查看svc
[root@master k8s_airflow]# kubectl get svc -n ingress-nginx
NAME                                 TYPE        CLUSTER-IP        EXTERNAL-IP   PORT(S)                      AGE
ingress-nginx-controller             NodePort    192.168.206.166   <none>        80:32202/TCP,443:30702/TCP   22h
ingress-nginx-controller-admission   ClusterIP   192.168.221.2     <none>        443/TCP                      22h
```

4  **airflow-worker的服务部署**
airflow-worker.yaml
> worker属于集群内部的服务，因此它就不需要类似webserver需要创建一个svc
与webserver不同的是由于worker需要执行任务，因此它需要能够共享宿主机的部分Hosts,因此加入了hostAliases的配置，这样在容器内部可以访问到对应的hosts

>podAntiAffinity：pod反亲和力
requiredDuringSchedulingIgnoredDuringExecution: 不要将a应用与之匹配的应用部署在一块
preferredDuringSchedulingIgnoredDuringExecution: 尽量不要将a应用与之匹配的应用部署在一块
当使用requiredDuringSchedulingIgnoredDuringExecution策略的时候，worker的部署就会在不同的服务器节点上，不会存在1个服务器同时有2个worker的pod,也就是如果你有2台node节点，想部署三个worker的话，那么第三个worker是永远处于pending状态不会启动成功

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: airflow-worker
  namespace: airflow-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: airflow-worker-pod
  template:
    metadata:
      labels:
        app: airflow-worker-pod
    spec:
      affinity:
        podAntiAffinity: #反亲和力
          requiredDuringSchedulingIgnoredDuringExecution: #硬策略不要将a应用与之匹配的应用部署在一块
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - airflow-worker-pod 
            topologyKey: kubernetes.io/hostname
      terminationGracePeriodSeconds: 10
      hostAliases:
      - ip: "10.0.3.165"
        hostnames:
        - "deploy.ingress.com"
      containers:
        - name: airflow-worker
          image: apache/airflow:2.4.3
          securityContext:
            runAsUser: 50000
          args: ["celery","worker"]
          env:
            - name: AIRFLOW__CORE__EXECUTOR
              value: CeleryExecutor
            - name: AIRFLOW__DATABASE__SQL_ALCHEMY_CONN
              value: mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
            - name: AIRFLOW__CORE__SQL_ALCHEMY_CONN
              value: mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
            - name: AIRFLOW__CELERY__RESULT_BACKEND
              value: redis://redis-service:6380/0
            - name: AIRFLOW__CELERY__BROKER_URL
              value: redis://redis-service:6380/0
            - name: AIRFLOW__CORE__LOAD_EXAMPLES
              value: 'true'
          volumeMounts:
          - mountPath: /opt/airflow/dags
            name: airflow-dags
          - mountPath: /opt/airflow/logs
            name: airflow-logs
      volumes:
      - name: airflow-logs
        hostPath:
          path: /data/program/k8s/k8s_airflow/logs
      - name: airflow-dags
        hostPath:
          path: /data/program/k8s/k8s_airflow/dags
```

> 执行kubectl apply -f airflow-worker.yaml

```
[root@master k8s_airflow]# kubectl get pods -n airflow-server
NAME                                 READY   STATUS    RESTARTS   AGE
airflow-webserver-698bdb87bd-v9476   1/1     Running   0          18h
airflow-worker-74bcc6d5b-cl2pf       1/1     Running   0          17h
airflow-worker-74bcc6d5b-lcb6x       1/1     Running   0          17h
airflow-worker-74bcc6d5b-ppsp7       1/1     Running   0          17h
```

5  **airflow-scheduler的服务部署**

airflow-scheduler.yaml

> worker属于集群内部的服务，因此它就不需要类似webserver需要创建一个svc

```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: airflow-scheduler
  namespace: airflow-server
spec:
  replicas: 1
  selector:
    matchLabels:
      app: airflow-scheduler-pod
  template:
    metadata:
      labels:
        app: airflow-scheduler-pod
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:  # 硬策略
          - labelSelector:
               matchExpressions:
               - key: app
                 operator: In
                 values:
                 - airflow-scheduler-pod
            topologyKey: kubernetes.io/hostname
      terminationGracePeriodSeconds: 10
      containers:
        - name: airflow-scheduler
          image: apache/airflow:2.4.3
          securityContext:
            runAsUser: 50000
          args: ["scheduler"]
          env:
            - name: AIRFLOW__CORE__EXECUTOR
              value: CeleryExecutor
            - name: AIRFLOW__DATABASE__SQL_ALCHEMY_CONN
              value: mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
            - name: AIRFLOW__CORE__SQL_ALCHEMY_CONN
              value: mysql://airflow:0LKm7m1rjTtYZkld@mysql/airflow_test
            - name: AIRFLOW__CELERY__RESULT_BACKEND
              value: redis://redis-service:6380/0
            - name: AIRFLOW__CELERY__BROKER_URL
              value: redis://redis-service:6380/0
            - name: AIRFLOW__CORE__LOAD_EXAMPLES
              value: 'true'
          volumeMounts:
          - mountPath: /opt/airflow/dags
            name: airflow-dags
          - mountPath: /opt/airflow/logs
            name: airflow-logs
      volumes:
      - name: airflow-logs
        hostPath:
          path: /data/program/k8s/k8s_airflow/logs
      - name: airflow-dags
        hostPath:
          path: /data/program/k8s/k8s_airflow/dags
```

> 执行kubectl apply -f airflow-scheduler.yaml

```
[root@master k8s_airflow]# kubectl get pods -n airflow-server
NAME                                 READY   STATUS    RESTARTS   AGE
airflow-scheduler-c7b7595c6-mjzpw    1/1     Running   0          17h
airflow-webserver-698bdb87bd-v9476   1/1     Running   0          18h
airflow-worker-74bcc6d5b-cl2pf       1/1     Running   0          17h
airflow-worker-74bcc6d5b-lcb6x       1/1     Running   0          17h
airflow-worker-74bcc6d5b-ppsp7       1/1     Running   0          17h
```

> 至此可以看到集群中启动了1个webserver,1个scheduler,3个worker,如果需要启动2个scheduler,需要将MySQL升级到8版本
