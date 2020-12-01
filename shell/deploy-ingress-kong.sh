#!/bin/bash
#projectName 小写项目名
#nameSpaces 命名空间
#sub_domain 二级域名
#Yml_path yaml文件保存路径
#is_plugins是否添加插件，目前只有app-auth这个鉴权插件

set -x

projectName=$1
nameSpaces=$2
sub_domain=$3
Yml_path=$4
is_plugins=$5

if [ n${nameSpaces} == n"master" -o n${nameSpaces} == n"feiteng-master" ];then
host=${sub_domain}
else
host="t-"${sub_domain}
fi

cd ${Yml_path}

if [ n${is_plugins} == n"1" ];then
cat >${project_name}-${nameSpaces}-ingress-kong.yaml<<EOF
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${projectName}-tls
  namespace: ${nameSpaces}
  annotations:
    konghq.com/plugins: app-auth
    kubernetes.io/ingress.class: "kong"
spec:
  tls:
  - secretName: xueerqin-cert
    hosts:
    - '*.xueerqin.net'
  rules:
  - host: ${host}.xueerqin.net
    http:
      paths:
      - path: /${projectName}/
        backend:
          serviceName: ${projectName}
          servicePort: 80
  - host: ${host}.xueerqin.local
    http:
      paths:
      - path: /${projectName}/
        backend:
          serviceName: ${projectName}
          servicePort: 80
EOF
else
cat >${project_name}-${nameSpaces}-ingress-kong.yaml<<EOF
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${projectName}-tls
  namespace: ${nameSpaces}
  annotations:
    kubernetes.io/ingress.class: "kong"
spec:
  tls:
  - secretName: xueerqin-cert
    hosts:
    - '*.xueerqin.net'
  rules:
  - host: ${host}.xueerqin.net
    http:
      paths:
      - path: /${projectName}/
        backend:
          serviceName: ${projectName}
          servicePort: 80
  - host: ${host}.xueerqin.local
    http:
      paths:
      - path: /${projectName}/
        backend:
          serviceName: ${projectName}
          servicePort: 80
EOF
fi
