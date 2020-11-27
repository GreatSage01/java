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


@Library('Jenkins-fuyu') _



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
                    
                    //发布环境
                    env.deployEnv="${project_branch}"

                    //部署k8s认证信息
                    (k8s_url,k8s_credentials)=k8s.kube([deployEnv: "${deployEnv}",
                                                                Prod_k8sUrl: "${com_values.Prod_k8sUrl}",
                                                                Prod_k8sCred: "${com_values.Prod_k8sCred}",
                                                                Dev_k8sUrl: "${com_values.Prod_k8sUrl}",
                                                                Dev_k8sCred: "${com_values.Prod_k8sCred}"
                                                                ])
                    
                    println "k8s_url:"+k8s_url
                    println "k8s_credentials:"+k8s_credentials

                    //项目yaml文件保存路径
                    env.Yml_path="/home/jenkins/deployment/${deployEnv}/${projectName}"
                    
                    //docker镜像,IMAGE_Name
                    //public_mod.Harbor_tag([deployEnv: "${deployEnv}",projectName:"${projectName}"])

                    //k8s资源确认
                    //public_mod.K8s_exist([Language:"${env.Language}",serviceName:"${projectName}",nameSpaces:"${nameSpaces}"])

                    //左侧展示
                    //public_mod.Wrap([user_name: "${user_name}",projectName:"${projectName}",reversion:"${tag_reversion}",deployEnv:"${deployEnv}"])


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
                //Svn_Url: http://fy-svn.fjfuyu.net/svn
                if ( "${Deploy_env}" == "master" ) {
                    env.subversionURL = "${com_values.SVN_Url}/Deploy/prod/${project_name}"
                }else if ( "${Deploy_env}" == "dev" ) {
                    env.subversionURL = "${com_values.SVN_Url}/Deploy/test/${project_name}"
                }
                else{
                    error("${Deploy_env}分支错误！")
                }

                // 检出SVN上的源代码
                git.gitcheckout([projectName:"${env.projectName}",
                                Deploy_env:"${env.Deploy_env}",
                                SVN_Cred:"${com_values.SVN_Cred}",
                                SVN_Url:"${env.subversionURL}"])
            }

        }
    }

    stage('echo') {
      steps {
        echo com_values.Prod_k8sUrl
        println  com_values.SVN_Url
        println proj_values.Approver
        println "projectName"+projectName
        
      }
    }
  }
}