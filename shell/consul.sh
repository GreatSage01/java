#!/bin/sh
#sub_domain 三级域名
#projectName 项目名称
#group 开发组名

Yml_path=$1
domainName=$2
serviceName=$3
deployEnv=$4
healthCheck=$5
Language=$6


consul_url='http://172.16.0.94:8500/v1/agent/service/register?replace-existing-checks=1'


json_file="${Yml_path}/${serviceName}.json"


cat>${json_file}<<EOF
{
"id": "${serviceName}",
"name": "${Language}",
"address": "https://${domainName}.xueerqin.net${healthCheck}",
"port": 80,
"meta":{
        "Group": "${Language}",
        "Project":"${serviceName}"
},
"tags": ["${deployEnv}"],
"checks": [
{"http": "https://${domainName}.xueerqin.net${healthCheck}",
"interval": "60s"}]
}
EOF

curl --request PUT --data @${json_file} ${consul_url}
