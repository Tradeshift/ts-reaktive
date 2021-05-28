// This Jenkinsfile uses the declarative syntax. If you need help, check:
// Overview and structure: https://jenkins.io/doc/book/pipeline/syntax/
// For plugins and steps:  https://jenkins.io/doc/pipeline/steps/
// For Github integration: https://github.com/jenkinsci/pipeline-github-plugin
// For credentials:        https://jenkins.io/doc/book/pipeline/jenkinsfile/#handling-credentials
// For credential IDs:     https://ci.ts.sv/credentials/store/system/domain/_/
// Docker:                 https://jenkins.io/doc/book/pipeline/docker/
// Custom commands:        https://github.com/Tradeshift/jenkinsfile-common/tree/master/vars
// Environment variables:  env.VARIABLE_NAME

pipeline {
    agent { label 'sbt' }
    triggers {
        issueCommentTrigger('^retest$')
    }
    options {
        ansiColor('xterm')
        timestamps()
        timeout(time: 30, unit: 'MINUTES') // Kill the job if it gets stuck for too long
    }

    tools {
        jdk 'oracle-java8u202-jdk'
    }

    environment {
        P12_PASSWORD = credentials 'client-cert-password'
        JAVA_OPTS = "-Xmx4096m -Xss512m -XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=6G \
                        -Djavax.net.ssl.keyStore=/var/lib/jenkins/.m2/certs/jenkins.p12 \
                        -Djavax.net.ssl.keyStoreType=pkcs12 \
                        -Djavax.net.ssl.keyStorePassword=$P12_PASSWORD"
        SBT_OPTS = "-Dsbt.log.noformat=true"
    }

    stages {
        stage('Build and Test') {
            parallel {
                stage('Helm check') {
                    steps {
                        githubNotify(status: 'PENDING', context: 'helm', description: 'Helm check')
                        helmCheck()
                    }
                    post {
                        success {
                            githubNotify(status: 'SUCCESS', context: 'helm', description: 'Helm check passed')
                        }
                        failure {
                            githubNotify(status: 'FAILURE', context: 'helm', description: 'Helm check failure')
                        }
                    }
                }
                stage('Test') {
                    steps {
                        githubNotify(status: 'PENDING', context: 'test', description: 'Unit tests running')
                        sh 'sbt test'
                    }
                    post {
                        success {
                            githubNotify(status: 'SUCCESS', context: 'test', description: 'Unit tests passed')
                        }
                        failure {
                            githubNotify(status: 'FAILURE', context: 'test', description: 'Unit test failures')
                        }
                    }
                }
            }
        }
        stage('Sonarqube') {
            when {
                // Only run Sonarqube on master and PRs
                anyOf {
                    branch 'master'
                    changeRequest()
                }
            }
            steps {
                sonarqube()
            }
        }
    }
}
