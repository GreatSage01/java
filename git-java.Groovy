//Active Choices
//https://plugins.jenkins.io/uno-choice/
//Generate with "pipeline-syntax" > "properties: Set job properties"
properties([
    parameters([choice(choices: ['deploy', 'rollback'], description: 'deploy------部署<br>rollback-----回滚', name: 'project_switch'),
        [$class: 'ChoiceParameter',
        choiceType: 'PT_SINGLE_SELECT',
        description: '项目',
        filterLength: 1,
        filterable: false,
        name: 'project_name',
        randomName: 'choice-parameter-734759034940966',
        script: [$class: 'GroovyScript',
            fallbackScript: [classpath: [],
                sandbox: false,
                script: 'return [\'error\']'],
            script: [classpath: [],
                sandbox: false,
                script: 'return [\'curl\', \'http://172.16.0.94:9991/v1/gitlab?token=9jAzgpnqiXgxqbKu1KF2&groupname=java\'].execute().text.readLines()']
        ]],
        choice(choices: ['dev', 'pre','prod'], description: 'dev---------测试环境<br>pre-----预发布环境<br>prod-----正式环境', name: 'deployEnv'),
        choice(choices: ['alpine3', 'centos7'], description: '选择基础镜像（注：宇视、培训平台请选择centos7镜像、其他默认使用alpine3镜像）', name: 'choise_image'),
        string(defaultValue: '', description: '接收gitlab webhook触发用户（注：无需填写）', name: 'user_name', trim: true),
    ])
])


@Library('Jenkins-fuyu@dev')
def public_mod = new org.devops.public_mod()
def svn = new org.devops.svn()
def git = new org.devops.git()

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
        timeout(time: 30, unit: 'MINUTES')
    }

    environment{

        //技术栈
        Language='java'

        //k8s集群部署地方
        k8s_ENV="huaweiyun"

        //默认域名
        domainName="api.xueerqin.net"

    }



    stages{
        stage("配置读取"){
            steps{
                script{
                    com_values = com.loadValuesYaml("${env.WORKSPACE}/jenkinsfile/Values/com_values.yaml")
                    proj_values = com.loadValuesYaml("${env.WORKSPACE}/jenkinsfile/Values/project_values.yaml")
                                 
                    //根据发布环境确认k8s部署命名空间和gtilab上的分支
                    (nameSpaces,project_branch)=com.get_NS([deployEnv: "${deployEnv}",Language:"${Language}"])

                }
            }
        }

        stage("Git检出"){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    //清理
                    dir(path: "${env.WORKSPACE}/${project_name}"){
                        deleteDir()
                    }

                    // 检出GIT上的源代码
                    def git_Url=com_values.Gitlab_Url+'/'+project_name+'.git'
                    git.gitcheckout([project_name:"${project_name}",
                               Gitlab_Branch: "${project_branch}",
                               Gitlab_Cred:"${com_values.Gitlab_Cred}",
                               Gitlab_Url:"${git_Url}"])

                }
            }
        }


        stage("初始化"){
            steps{
                script{
                    // 镜像标签转小写
                    env.projectName="${project_name}".toLowerCase()
                    
                    //部署k8s认证信息
                    (K8s_url,K8s_credentials)=k8s.KubeConfig([k8s_ENV: "${k8s_ENV}",
                                                        Prod_k8sUrl: "${com_values.Prod_k8sUrl}",
                                                        Prod_k8sCred: "${com_values.Prod_k8sCred}",
                                                        Dev_k8sUrl: "${com_values.Prod_k8sUrl}",
                                                        Dev_k8sCred: "${com_values.Prod_k8sCred}"
                                                        ])



                    //项目yaml文件保存路径
                    env.Yml_path="/home/jenkins/deployment/${deployEnv}/${projectName}"

                    //java部署参数
                    java.build_values("${env.WORKSPACE}/${project_name}/deploy-config/values.yaml")

                    println "-------->发布环境："+deployEnv+', k8s部署空间:'+nameSpaces+' <------------'                    
                    println '------>WebSocket端口列表:'+ ws_port+'<--------'

                    //定义基础镜像
                    def base_im
                    if ( env.choise_image == 'alpine3' ){
                        println 'alpine3'
                        base_im='base/oracle-jdk1.8-alpine3.10'
                    }else if ( choise_image == 'centos7' ){
                        println 'centos7'
                        base_im='base/oracle-jdk1.8-yushilib-centos7'
                    }
                    withEnv(["base_im=${base_im}"]){
                        def base_tag=sh(script: "curl http://172.16.0.94:9991/v1/harbor_tag?repo_name=${base_im}", returnStdout: true)
                        env.image_base=com_values.HUB_Url+'/'+base_im+':'+base_tag
                    }
                    println '----------->本次 image_base 是:'+image_base+'<----------------'

                    //docker镜像,IMAGE_Name
                    (IMAGE_Name,tag_reversion)=public_mod.Harbor_tag([nameSpaces: "${nameSpaces}",
                                                    Language: "${Language}",
                                                    projectName:"${serviceName}",
                                                    HUB_Url:"${com_values.HUB_Url}"])

                    println '--------->本次发布的镜像是：'+IMAGE_Name                    

                    //k8s资源确认
                    public_mod.K8s_exist([Language:"${env.Language}",
                                          serviceName:"${serviceName}",
                                          nameSpaces:"${nameSpaces}",
                                          k8s_url:"${K8s_url}",
                                          k8s_credentials:"${K8s_credentials}"])

                    //左侧展示
                    public_mod.Wrap([user_name: "${user_name}",
                                     projectName:"${serviceName}",
                                     reversion:"${tag_reversion}",
                                     deployEnv:"${deployEnv}"])
                }
            }
        }

        //stage('检出SVN'){
        //    when{
        //        expression { "${project_switch}" == 'deploy' }
        //    }
        //    steps{
        //        script{
        //            // 拼接SVN链接地址
        //            if ( "${env.project_branch}" == "master" ) {
        //                env.subversionURL = "${com_values.SVN_Url}/Deploy/prod/${project_name}"
        //            }else if ( "${env.project_branch}" == "dev" ) {
        //                env.subversionURL = "${com_values.SVN_Url}/Deploy/test/${project_name}"
        //            }else if ( "${env.project_branch}" == "feiteng-master" ){
        //                env.subversionURL = "${com_values.SVN_Url}/Deploy/feiteng-prod/${project_name}"
        //            }else if ( "${env.project_branch}" == "feiteng-dev" ){
        //                env.subversionURL = "${com_values.SVN_Url}/Deploy/feiteng-test/${project_name}"
        //            }
        //            else{
        //                error("${project_branch}分支错误！")
        //            }
    //
        //            // 检出SVN上的源代码
        //            svn.svncheckout([projectName:"${env.projectName}",
        //                            Deploy_env:"${env.deployEnv}",
        //                            SVN_Cred:"${com_values.SVN_Cred}",
        //                            SVN_Url:"${env.subversionURL}"])
        //        }
        //    }
        //}
    
        stage('构建'){
            when{
                expression { "${project_switch}" == 'deploy' };

            }
            steps{
                script{
                    dir(path: "${env.WORKSPACE}/${project_name}/"){
                        //生成Dockerfile
                        java.Build_dockerfile([image_base: "${image_base}",
                                                projectName: "${projectName}",                          
                                                deployEnv: "${deployEnv}"
                                                ])

                        //编译
                        java.build([projectName: "${projectName}"])
                    }
                }
            }
        }
    
        stage('Docker Image'){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    //if( env.deployEnv == 'master' || env.deployEnv == 'feiteng-master' ){
                    //    timeout(time: 30, unit: 'MINUTES') {
                    //        input message:'deploy to master?', submitter: "${proj_values.Approver}"
                    //        echo "master docker image build"
                    //    }
                    //}else if( env.deployEnv == 'dev' || env.deployEnv == 'feiteng-dev' ){
                    //        echo "dev docker image build"
                    //}
                    dir(path: "${env.WORKSPACE}/${project_name}" ){
                        java.BuildImage([IMAGE_Name:"${IMAGE_Name}",
                                    HUB_Url:"${com_values.HUB_Url}",
                                    HUB_Cred:"${com_values.HUB_Cred}"])
                    }
                }
            }
        }
    
        stage('部署K8s'){
            when{
                environment name: 'project_switch',value: 'deploy'
            }
            steps{
                script{
                    if( env.deployEnv == 'prod' ){
                        //正式线发布确认
                        input(message:'是否发布到prod环境?',
                            ok: '确定',
                            submitter: "${proj_values.Approver}",
                            submitterParameter: 'APPROVER_name'
                        )
                        //正式线镜像从uat环境获取
                        //uat_tag=public_mod.Env_image([projectName: "${serviceName}",Language: "${Language}",deployEnv: "uat"])
                        //println '--->uat docker tag 是：'+uat_tag+'<----------------'
                        //def uat_Image="${com_values.HUB_Url}/${Language}-uat/${projectName}:${uat_tag}".trim()
                        println '--------->pord环境本次更新的镜像是：'+IMAGE_Name+'<-----------------'
                        //uat镜像推送到prod
                        //com.DIMG_Chage([HUB_Url:"${com_values.HUB_Url}",
                        //            HUB_Cred:"${com_values.HUB_Cred}",
                        //            uat_Image: "${uat_Image}",
                        //            pord_Image: "${IMAGE_Name}"
                        //            ])
                    }
                    withKubeConfig(credentialsId: "${K8s_credentials}", serverUrl: "${K8s_url}"){
                        withEnv(["podNum=${proj_values.podNum}"]){
                            dir(path: "${env.WORKSPACE}/${project_name}"){
                                //ComfigMap
                                java.BuildConfigMap([serviceName: "${serviceName}",
                                                    deployEnv: "${deployEnv}",
                                                    nameSpaces: "${nameSpaces}",
                                                    configmap_exist: "${env.configmap_exist}"
                                                ])

                                //deployment
                                java.build_deploy([svc_exist: "${svc_exist}", serviceName: "${serviceName}",
                                                    deployEnv: "${deployEnv}", IMAGE_Name: "${IMAGE_Name}",
                                                    nameSpaces: "${nameSpaces}", podNum: "${podNum}",
                                                    Yml_path: "${Yml_path}", ws_port: "${ws_port}"
                                                ])

                                //ingress
                                if ( env.ingress_exist != "${serviceName}-tls" ){
                                    java.build_ingress([serviceName: "${serviceName}", nameSpaces: "${nameSpaces}",
                                                        domainName: "${domainName}",deployEnv: "${deployEnv}",
                                                         Yml_path: "${Yml_path}",
                                                        is_plugins: "${is_plugins}", ws_port: "${ws_port}"
                                                    ])

                                    //consul告警
                                    if ( env.deployEnv == 'pord' ){
                                        com.Create_consul([deployEnv: "${deployEnv}",
                                                        Yml_path: "${Yml_path}",
                                                        domainName: "${domainName}",
                                                        serviceName: "${serviceName}",
                                                        healthCheck: "${healthCheck}",
                                                        Language: "${Language}"
                                                    ])
                                    }
                                }
                            }
                        }
                    }

                    println  deployEnv+"."+projectName+"部署到k8s成功！"
    
                }
            }
        }
    
    

        //清理环境
        stage('清理'){
            when{
                expression { "${project_switch}" == 'deploy' }
            }
            steps{
                script{
                    withEnv(["IMAGE_Name=${IMAGE_Name}"]){
                        sh '''/bin/bash
                            docker rmi -f ${IMAGE_Name}
                        '''
                    }
                }
            }
        }

        stage("回滚"){
           when{
               allOf{
                   expression { "${project_switch}" == "rollback" }
               }
            }
            steps {
                timeout(time: 30, unit: 'MINUTES') {
                    script {
                        com.Roll_back([deployEnv: "${deployEnv}",
                                        nameSpaces: "${nameSpaces}",
                                        serviceName: "${serviceName}",
                                        Approver: "${proj_values.Approver}",
                                        HUB_Url: "${com_values.HUB_Url}",
                                        HUB_Cred: "${com_values.HUB_Cred}",
                                        k8s_credentials: "${K8s_credentials}",
                                        k8s_url: "${K8s_url}",
                                        svc_exist: "${svc_exist}"
                                    ])
                        
                    }
                }
            }
        }
    }
}