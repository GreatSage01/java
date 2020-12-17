#!/bin/sh
#保存目录
#domainName 完整域名
#serviceName 项目名称
#deployEnv 部署环境
#healthCheck检测路径
#Language 开发组名

set -x

Yml_path=$1
domainName=$2
serviceName=$3
deployEnv=$4
healthCheck=$5
Language=$6

if [ ! $# == 6 ]; then
  echo "Usage: $0 Yml_path domainName serviceName deployEnv healthCheck Language"
  exit
fi


consul_url='http://172.16.0.94:8500/v1/agent/service/register?replace-existing-checks=1'


json_file="${Yml_path}/${serviceName}.json"


cat>${json_file}<<EOF
{
"id": "${serviceName}",
"name": "${Language}",
"address": "https://${domainName}${healthCheck}",
"port": 80,
"meta":{
        "Group": "${Language}",
        "Project":"${serviceName}"
},
"tags": ["${deployEnv}"],
"checks": [
{"http": "https://${domainName}${healthCheck}",
"interval": "60s"}]
}
EOF

curl --request PUT --data @${json_file} ${consul_url}
