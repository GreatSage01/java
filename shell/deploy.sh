#!/bin/bash -x

projectName=$1
podNum=$2
nameSpaces=$3
Yml_path=$4
deployEnv=$5
IMAGE_Name=$6
ws_port=$7


if [ ! $# == 7 ]; then
  echo "Usage: $0 projectName podNum nameSpaces Yml_path deployEnv IMAGE_Name ws_port"
  exit
fi


mkdir -p ${Yml_path}

cd ${Yml_path}

cat >${projectName}-${deployEnv}.yaml<<EOF
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${projectName}
  namespace: ${deployEnv}
spec:
  replicas: ${podNum}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 2
      maxUnavailable: 0
  selector:
    matchLabels:
      app: ${projectName}
  template:
    metadata:
      labels:
        app: ${projectName}
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
                - ${environment}
      imagePullSecrets:
      - name: hubsecret
      volumes:
      - name: ${projectName}-config
        configMap:
          name: ${projectName}-config
      - name: skw-agent-volume
        emptyDir: {}
      initContainers:
        - name: skywalking-agent
          image: hub.xueerqin.net/base/skywalking-agent:7.0.0
          command: ['cp','-rf','/skywalking/agent/','/tmp/']
          volumeMounts:
            - mountPath: /tmp
              name: skw-agent-volume
      containers:
      - name: ${projectName}
        image: ${IMAGE_Name}
        imagePullPolicy: Always
        env:
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
        - name: ${projectName}-config
          mountPath: /opt/config
          readOnly: true
        - name: skw-agent-volume
          mountPath: /opt/agent
        ports:
        - containerPort: 80
          name: web
          protocol: TCP
        readinessProbe:
          httpGet:
            path: /${projectName}/health
            port: 80
            scheme: HTTP
          initialDelaySeconds: 30
          periodSeconds: 15
          timeoutSeconds: 5
        livenessProbe:
          httpGet:
            path: /${projectName}/health
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
      app: ${projectName}
  name: ${projectName}
  namespace: ${deployEnv}
spec:
  ports:
  - port: 80
    targetPort: 80
    name: web
EOF
if [ ${ws_port} != 0 ]
then
  j=0
  for i in ${ws_port[@]}
  do 
    echo >>${projectName}-${deployEnv}.yaml <<EOF
  - port: ${i}
    targetPort: ${i}
    name: ws-${j}
EOF
    j+=1
  done
fi 
echo >>${projectName}-${deployEnv}.yaml <<EOF
  selector:
    app: ${projectName}
EOF
