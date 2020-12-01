//Active Choices
//https://plugins.jenkins.io/uno-choice/
//Generate with "pipeline-syntax" > "properties: Set job properties"
//properties([
//    parameters([choice(choices: ['deploy', 'rollback'], description: 'deploy------部署<br>rollback-----回滚', name: 'project_switch'),
//        [$class: 'ChoiceParameter',
//        choiceType: 'PT_MULTI_SELECT',
//        description: '项目',
//        filterLength: 1,
//        filterable: false,
//        name: 'project_name',
//        randomName: 'choice-parameter-734759034940966',
//        script: [$class: 'GroovyScript',
//            fallbackScript: [classpath: [],
//                sandbox: false,
//                script: 'return [\'error\']'],
//            script: [classpath: [],
//                sandbox: false,
//                script: 'return [\'curl\', \'http://172.16.0.94:8888/v1/subversion?url=https://192.168.66.186:8443/svn/Deploy/test/\'].execute().text.readLines()']
//        ]],
//        choice(choices: ['dev', 'master'], description: 'dev---------测试<br>master-----正式', name: 'project_branch'),
//        choice(choices: ['hub.xueerqin.net/base/oracle-jdk1.8-alpine3.10:0.0.3', 'hub.xueerqin.net/base/oracle-jdk1.8-yushilib-centos7:0.0.6'], description: '选择基础镜像（注：宇视、培训平台请选择centos7镜像、其他默认使用alpine3镜像）', name: 'base_image'),
//        choice(choices: ['api', 'room', 'cloudcourse', 'busi'], description: '子域名', name: 'sub_domain')
//    ])
//])



@Library('Jenkins-fuyu@dev')
def public_mod = new org.devops.public_mod()
def svn = new org.devops.svn()

pipeline{
    agent{
        kubernetes {
            yamlFile "jenkinsfile/config/jenkins-slave.yaml"
        }
    }
    options {
        // The Timestamper plugin adds timestamps to the console output of Jenkins jobs
        // https://plugins.jenkins.io/timestamper/
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30'))
    }

    environment{

        //技术栈
        Language='java'
    }



    stages{
        stage("配置读取"){
            steps{
                script{
                    com_values = com.loadValuesYaml("${env.WORKSPACE}/jenkinsfile/Values/com_values.yaml")
                    proj_values = com.loadValuesYaml("${env.WORKSPACE}/jenkinsfile/Values/project_values.yaml")
                }
            }
        }

        stage("初始化"){
            steps{
                script{
                    //定义基础镜像
                    if ( "${choice_image}" == "alpine3" ){
                        base_image="${oracle_image}"
                    }else if ( "${choice_image}" == "centos7"){
                        base_image="${yushi_image}"
                    }else {
                        error("ERROR: choice_image Error")
                    }
                    env.base_image="${com_values.HUB_Url}/${base_image}"
                    println "base_image:"+base_image
                    
                    // 镜像标签转小写
                    env.projectName="${project_name}".toLowerCase()

                    //domainName
                    env.domainName="${sub_domain}"
                    
                    //发布环境
                    env.deployEnv="${project_branch}"

                    //部署k8s认证信息
                    (K8s_url,K8s_credentials)=k8s.KubeConfig([deployEnv: "${deployEnv}",
                                                        Prod_k8sUrl: "${com_values.Prod_k8sUrl}",
                                                        Prod_k8sCred: "${com_values.Prod_k8sCred}",
                                                        Dev_k8sUrl: "${com_values.Prod_k8sUrl}",
                                                        Dev_k8sCred: "${com_values.Prod_k8sCred}"
                                                        ])
                    
                    //项目yaml文件保存路径
                    env.Yml_path="/home/jenkins/deployment/${deployEnv}/${projectName}"
                    
                    //docker镜像,IMAGE_Name
                    public_mod.Harbor_tag([deployEnv: "${deployEnv}",
                                           projectName:"${projectName}",
                                           HUB_Url:"${com_values.HUB_Url}"])

                    //k8s资源确认
                    public_mod.K8s_exist([Language:"${env.Language}",
                                          serviceName:"${projectName}",
                                          nameSpaces:"${deployEnv}",
                                          k8s_url:"${K8s_url}",
                                          k8s_credentials:"${K8s_credentials}"])

                    //左侧展示
                    public_mod.Wrap([user_name: "${env.BUILD_USER}",
                                     projectName:"${projectName}",
                                     reversion:"${tag_reversion}",
                                     deployEnv:"${deployEnv}"])


                }
            }
        }

        stage('检出SVN'){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    // 拼接SVN链接地址
                    if ( "${env.project_branch}" == "master" ) {
                        env.subversionURL = "${com_values.SVN_Url}/Deploy/prod/${project_name}"
                    }else if ( "${env.project_branch}" == "dev" ) {
                        env.subversionURL = "${com_values.SVN_Url}/Deploy/test/${project_name}"
                    }else if ( "${env.project_branch}" == "feiteng-master" ){
                        env.subversionURL = "${com_values.SVN_Url}/Deploy/feiteng-prod/${project_name}"
                    }else if ( "${env.project_branch}" == "feiteng-dev" ){
                        env.subversionURL = "${com_values.SVN_Url}/Deploy/feiteng-test/${project_name}"
                    }
                    else{
                        error("${project_branch}分支错误！")
                    }
    
                    // 检出SVN上的源代码
                    svn.svncheckout([projectName:"${env.projectName}",
                                    Deploy_env:"${env.deployEnv}",
                                    SVN_Cred:"${com_values.SVN_Cred}",
                                    SVN_Url:"${env.subversionURL}"])
                }
            }
        }
    
        stage('构建'){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{

                //生成项目Dockerfile
                sh '''/bin/bash
# generate Dockerfile
generateDockerfile(){
cat > "${WORKSPACE}/${deployEnv}/${projectName}"/Dockerfile <<EOF
# Base image
FROM ${base_image}

# MAINTAINER
MAINTAINER java
LABEL operator=xueerqin
LABEL app=${projectName}
LABEL env=${deployEnv}

# SET WORKDIR
WORKDIR /opt

# CREATE DIRECTORY
RUN mkdir -p logs/sdklog

# ADD
ADD target/${project_name}.jar "\\${WORKDIR}"

EXPOSE 80

ENTRYPOINT ["java", "-server", "-Xms512m", "-Xmx2048m", "-javaagent:/opt/agent/agent/skywalking-agent.jar", "-Dskywalking.agent.service_name=${deployEnv}-${projectName}", "-Dskywalking.collector.backend_service=skywalking7-skywalking-oap.skywalking:11800", "-jar", "/opt/${project_name}.jar", "--server.port=80"]
EOF
}

generateDockerfile

                echo "java Dcoekrfile 制作成功！"
            '''
    
            script{

                //java配置文件读取
                config_Path="${env.WORKSPACE}/${deployEnv}/${projectName}/values/${deployEnv}-values.json"
                Config_values=java.ReadConfig("${config_Path}")
    
                //构建
                sh '''#!/bin/bash -xe
                    rm -rf /home/jenkins/.m2/repository/com/yiyu
                    mvn -D maven.test.skip=true -B -U -f "${WORKSPACE}/${deployEnv}/${projectName}"/pom.xml clean install
                    echo "Build App Done !"
                '''
                }
            }
        }
    
        stage('Docker Image'){
            when{
                environment name: 'project_switch',value: 'deploy'
            }
            steps{
                script{
                    if( env.deployEnv == 'master' || env.deployEnv == 'feiteng-master' ){
                        timeout(time: 30, unit: 'MINUTES') {
                            input message:'deploy to master?', submitter: "${proj_values.approver}"
                            echo "master docker image build"
                        }
                    }else if( env.deployEnv == 'dev' || env.deployEnv == 'feiteng-dev' ){
                            echo "dev docker image build"
                    }
                    java.BuildImage([projectName:"${projectName}",
                                    deployEnv:"${deployEnv}",
                                    IMAGE_Name:"${IMAGE_Name}",
                                    HUB_Url:"${com_values.HUB_Url}",
                                    HUB_Cred:"${com_values.HUB_Cred}"])
    
                }
            }
        }
    
        stage('部署K8s'){
            when{
                environment name: 'project_switch',value: 'deploy'
            }
            steps{
                script{
                    withKubeConfig(credentialsId: "${K8s_credentials}", serverUrl: "${K8s_url}"){
                        //ComfigMap
                        java.BuildConfigMap([projectName: "${projectName}",
                                             deployEnv: "${deployEnv}",
                                             configmap_exist: "${env.configmap_exist}"
                                            ])
    
                        //deployment
                        sh '''#!/bin/bash
                            if [ "${env.svc_exist}" == "${projectName}"  ];then
                                sh ${WORKSPACE}/jenkinsfile/shell/deploy.sh ${projectName} ${podNum} ${nameSpaces} ${Yml_path} ${deployEnv} ${IMAGE_Name}
                                kubectl apply -f ${Yml_path}/${projectName}-${nameSpaces}.yaml  --record
                                #kubectl set image deployment/${projectName} ${projectName}="${IMAGE_Name}" -n ${nameSpaces} --record
                            else
                                sh ${WORKSPACE}/jenkinsfile/shell/deploy.sh ${projectName} ${podNum} ${nameSpaces} ${Yml_path} ${deployEnv} ${IMAGE_Name}
                                kubectl apply -f ${Yml_path}/${projectName}-${nameSpaces}.yaml  --record
                            fi
                        '''
    
                        //ingress
                        if ( "${env.ingress_exist}" != "${env.projectName}-tls" ) {
                            sh '''#!/bin/bash
                                sh ${WORKSPACE}/jenkinsfile/shell/deploy-ingress-kong.sh ${projectName} ${nameSpaces} ${DOMAIN_NAME} ${Yml_path} ${is_plugins}
                                kubectl apply -f ${Yml_path}/${projectName}-${nameSpaces}-ingress-kong.yaml --record
                                echo "${projectName} ingress 发布成功！"
                            '''
                        }
                        
                        if ( env.deployEnv == 'master' && "${env.ingress_exist}" != "${env.projectName}-tls" ){
                                com.Create_consul([deployEnv: "${deployEnv}",
                                                    Yml_path: "${Yml_path}",
                                                    domainName: "${domainName}",
                                                    serviceName: "${projectName}",
                                                    healthCheck: "/${projectName}/health",
                                                    Language: "${Language}"
                                                ])
                        }
                    }
                    
                    println  deployEnv+"."+projectName+"部署到k8s成功！"
    
                }
            }
        }
    
    

        stage('echo') {
            steps {
                echo com_values.Prod_k8sUrl
                println  com_values.SVN_Url
                println proj_values.Approver
                println "projectName"+projectName
                println "k8s_url:"+K8s_url
                println "k8s_credentials:"+K8s_credentials
                println "Tag_latest:" + Tag_latest
                println "Tag_reversion:" + Tag_reversion
            }
        }   
    }
}