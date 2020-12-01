#!/bin/bash

projectName=$1
podNum=$2
nameSpaces=$3
Yml_path=$4
environment=$5
IMAGE_Name=$6

if [ ! $# == 6 ]; then

echo "Usage: $0 projectName  podNum nameSpaces Yml_path environment IMAGE_Name"

exit

fi

mkdir -p ${Yml_path}

cd ${Yml_path}

cat >${projectName}-${nameSpaces}.yaml<<EOF
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${projectName}
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
  namespace: ${nameSpaces}
spec:
  ports:
  - port: 80
    targetPort: 80
    name: web
  selector:
    app: ${projectName}
EOF
