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
                    proj_values = com.loadValuesYaml("${env.WORKSPACE}/jenkinsfile/Values/values.yaml")
                }
            }
        }
    stage('Deploy') {
      steps {
        echo com_values.Prod_k8sUrl
        println  com_values.Svn_Url
        println proj_values.Approver
      }
    }
  }
}