#!/bin/bash
#projectName 小写项目名
#nameSpaces 命名空间
#domainName 完整的域名
#Yml_path yaml文件保存路径
#is_plugins是否鉴权，0为假不需要，1为真需要，目前只有app-auth这个鉴权插件
#WebSocket 端口，shell列表形式：(123,1245,111)

set -x

projectName=$1
nameSpaces=$2
domainName=$3
Yml_path=$4
is_plugins=$5
ws_port=$6
ws_domainName="websocket.xueerqin.net"

if [ ! $# == 5 ]; then
  echo "Usage: $0 projectName nameSpaces domainName Yml_path is_plugins ws_port"
  exit
fi


#自签证书
cert_manager="0"
if [[ "$(echo $domainName|grep "fjfuyu")" != ""   ]]
then
  cert_manager="1"
fi

#域名
if [[ "$(echo $nameSpaces|grep "dev")" != "" ]]
then
  domainName="t-${domainName}"
  ws_domainName="t-${ws_domainName}"
elif [[ "$(echo $nameSpaces|grep "master")" != "" ]]
then
  domainName=${domainName}
  ws_domainName=${ws_domainName}
else
  echo "命名空间错误：${nameSpaces} "
  exit 1
fi

#ssl证书泛域名
result=${domainName#*.}
SSL_secret="*."${result}
SSL_secretName=${result//./-}

echo "Ingress 域名是：$domainName"
echo "Ingress SSL证书泛域名： ${SSL_secret}"

cd ${Yml_path}


cat >${projectName}-${nameSpaces}-ingress-kong.yaml<<EOF
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: ${projectName}-tls
  namespace: ${nameSpaces}
  annotations:
EOF

if [[ "${is_plugins}" == "1" ]];then
cat >>${projectName}-${nameSpaces}-ingress-kong.yaml<<EOF
    konghq.com/plugins: app-auth
EOF
fi

if [[ "${is_plugins}" == "1" ]];then
cat >>${projectName}-${nameSpaces}-ingress-kong.yaml<<EOF
    cert-manager.io/cluster-issuer: letsencrypt-fjfuyu
EOF
fi

cat >>${projectName}-${nameSpaces}-ingress-kong.yaml<<EOF
    konghq.com/override: https-only
    kubernetes.io/ingress.class: "kong"
spec:
  tls:
  - secretName: ${SSL_secretName}
    hosts:
    - "${SSL_secret}"
  rules:
  - host: ${domainName}
    http:
      paths:
      - path: /${projectName}/
        backend:
          serviceName: ${projectName}
          servicePort: 80
EOF

if [ ${ws_port} != 0 ]
then
  j=0
  for i in ${ws_port[@]}
  do
    echo >>${projectName}-${deployEnv}.yaml <<EOF
  - host: ${ws_domainName}
    http:
      paths:
      - backend:
          serviceName: ${projectName}-${j}
          servicePort: ${i}
        path: /${projectName}/
EOF
  done 
fi 

