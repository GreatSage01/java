#!/bin/bash -e
#serviceName 小写项目名
#podNum pod容器副本数
#nameSpaces 命名空间
#Yml_path yaml文件保存路径
#deployEnv 部署环境: dev,pre,prod
#IMAGE_Name 部署镜像
#WebSocket 端口，shell列表形式：(123,1245,111)
#healthCheck 监控健康检查地址 /school/health

serviceName=$1
podNum=$2
nameSpaces=$3
Yml_path=$4
deployEnv=$5
IMAGE_Name=$6
ws_port=$7
healthCheck=$8


if [ ! $# == 8 ]; then
  echo "Usage: $0 serviceName podNum nameSpaces Yml_path deployEnv IMAGE_Name ws_port healthCheck"
  exit
fi

if [[ ${deployEnv} != 'prod'  ]];then
  env='dev'
else
  env='master'
fi

mkdir -p ${Yml_path}

cd ${Yml_path}

cat >${serviceName}-${deployEnv}.yaml<<EOF
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${serviceName}
  namespace: ${nameSpaces}
spec:
  replicas: ${podNum}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2
      maxUnavailable: 0
  selector:
    matchLabels:
      app: ${serviceName}
  template:
    metadata:
      labels:
        app: ${serviceName}
    spec:
      affinity:
        nodeAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            preference:
              matchExpressions:
              - key: environment
                operator: In
                values:
                - ${env}
      imagePullSecrets:
      - name: hubsecret
      volumes:
      - name: skw-agent-volume
        emptyDir: {}
      initContainers:
        - name: skywalking-agent
          #image: hub.xueerqin.net/base/skywalking-agent:7.0.0
          image: hub.xueerqin.net/base/skywalking-agent:7.0.1
          command: ['cp','-rf','/skywalking/agent/','/tmp/']
          volumeMounts:
            - mountPath: /tmp
              name: skw-agent-volume
      containers:
      - name: ${serviceName}
        image: ${IMAGE_Name}
        imagePullPolicy: Always
        env:
EOF

if [[ ${deployEnv} == 'pre' ]];then
cat >>${serviceName}-${deployEnv}.yaml<<EOF
          - name: JAVA_TOOL_OPTIONS
            value: -Xms512m -Xmx512m
EOF
fi

cat >>${serviceName}-${deployEnv}.yaml<<EOF
          - name: TZ
            value: Asia/Shanghai
        resources:
          limits:
            cpu: 2000m
            memory: 4Gi
          requests:
            cpu: 50m
            memory: 50Mi
        volumeMounts:
        - name: skw-agent-volume
          mountPath: /opt/agent
        ports:
        - containerPort: 80
          name: web
          protocol: TCP
        readinessProbe:
          httpGet:
            path: ${healthCheck}
            port: 80
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 15
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: ${healthCheck}
            port: 80
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 15
          timeoutSeconds: 5

---
kind: Service
apiVersion: v1
metadata:
  labels:
      app: ${serviceName}
  name: ${serviceName}
  namespace: ${nameSpaces}
spec:
  ports:
  - port: 80
    targetPort: 80
    name: web
EOF

if [[ ${ws_port} != 0 ]]
then
  j=0
  for i in ${ws_port[@]}
  do 
    cat >>${serviceName}-${deployEnv}.yaml <<EOF
  - port: ${i}
    targetPort: ${i}
    name: ws-${j}
EOF
    j+=1
  done
fi

cat >>${serviceName}-${deployEnv}.yaml <<EOF
  selector:
    app: ${serviceName}
EOF
