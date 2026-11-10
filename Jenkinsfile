pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        AWS_REGION = 'ap-south-1'
        DOCKER_HOST = 'tcp://127.0.0.1:2375'
        AWS_ACCOUNT_ID = '257312525299'
        ECR_REGISTRY = '257312525299.dkr.ecr.ap-south-1.amazonaws.com'
        EC2_HOST = '13.206.194.241'
    }

    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out source code from GitHub'
                checkout scm
            }
        }

        stage('Verify Tools') {
            steps {
                bat 'java -version'
                bat 'call "C:\\Users\\sudha\\Downloads\\apache-maven-3.10.0\\bin\\mvn.cmd" -version'
                bat 'docker --version'
                bat 'trivy --version'
                bat 'aws --version'
            }
        }

        stage('Build and Test') {
            steps {
                echo 'Building and testing all microservices'

                bat 'call "C:\\Users\\sudha\\Downloads\\apache-maven-3.10.0\\bin\\mvn.cmd" -B "-Dmaven.compiler.release=21" clean verify'
            }
        }

        stage('Build Docker Images') {
            steps {
                echo 'Building Docker images'

                withEnv([
                    'POSTGRES_SUPERUSER_PASSWORD=ci-only-root-password',
                    'PGADMIN_PASSWORD=ci-only-pgadmin-password',
                    'USER_DB_USERNAME=ci_user',
                    'USER_DB_PASSWORD=ci-only-user-password',
                    'PRODUCT_DB_USERNAME=ci_product',
                    'PRODUCT_DB_PASSWORD=ci-only-product-password',
                    'ORDER_DB_USERNAME=ci_order',
                    'ORDER_DB_PASSWORD=ci-only-order-password'
                ]) {
                    bat 'docker compose --profile apps build'
                }
            }
        }

        stage('Trivy Security Scan') {
            steps {
                bat 'if not exist security-reports mkdir security-reports'

                retry(3) {
                    bat 'trivy image --timeout 15m --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/user-service-trivy.json ecommerce-platform-user-service:latest'
                }

                retry(3) {
                    bat 'trivy image --timeout 15m --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/product-service-trivy.json ecommerce-platform-product-service:latest'
                }

                retry(3) {
                    bat 'trivy image --timeout 15m --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/order-service-trivy.json ecommerce-platform-order-service:latest'
                }
            }
        }

        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: 'security-reports/*.json',
                                 allowEmptyArchive: false
            }
        }

        stage('Verify AWS Access') {
    steps {
        withCredentials([
            string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            bat 'aws sts get-caller-identity'
        }
    }
}

       stage('Login to AWS ECR') {
    steps {
        withCredentials([
            string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            bat 'aws ecr get-login-password --region %AWS_REGION% | docker login --username AWS --password-stdin %ECR_REGISTRY%'
        }
    }
} 

        stage('Push Images to ECR') {
    steps {
        withCredentials([
            string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
            string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            bat '''
                docker tag ecommerce-platform-user-service:latest %ECR_REGISTRY%/ecommerce-devops-user-service:latest
                docker tag ecommerce-platform-product-service:latest %ECR_REGISTRY%/ecommerce-devops-product-service:latest
                docker tag ecommerce-platform-order-service:latest %ECR_REGISTRY%/ecommerce-devops-order-service:latest

                docker push %ECR_REGISTRY%/ecommerce-devops-user-service:latest
                docker push %ECR_REGISTRY%/ecommerce-devops-product-service:latest
                docker push %ECR_REGISTRY%/ecommerce-devops-order-service:latest
            '''
        }
    }
}

        stage('Deploy to AWS EC2') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ec2-ssh-key',
                        keyFileVariable: 'SSH_KEY',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    bat '''
                        if not exist "%USERPROFILE%\\.ssh" mkdir "%USERPROFILE%\\.ssh"

                        ssh-keyscan -H %EC2_HOST% >> "%USERPROFILE%\\.ssh\\known_hosts"

                        ssh -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=yes %SSH_USER%@%EC2_HOST% "cd /home/ubuntu/aws-microservices-cicd && aws ecr get-login-password --region ap-south-1 | sudo docker login --username AWS --password-stdin 257312525299.dkr.ecr.ap-south-1.amazonaws.com && sudo docker compose --env-file .env -f docker-compose.aws.yml pull && sudo docker compose --env-file .env -f docker-compose.aws.yml up -d"
                    '''
                }
            }
        }

       stage('Verify Deployment Health') {
    steps {
        withCredentials([
            sshUserPrivateKey(
                credentialsId: 'ec2-ssh-key',
                keyFileVariable: 'SSH_KEY',
                usernameVariable: 'SSH_USER'
            )
        ]) {
            bat '''
                ssh -i "%SSH_KEY%" -o BatchMode=yes -o StrictHostKeyChecking=yes %SSH_USER%@%EC2_HOST% "curl -fsS http://127.0.0.1:8081/actuator/health && curl -fsS http://127.0.0.1:8082/actuator/health && curl -fsS http://127.0.0.1:8083/actuator/health && curl -fsS http://127.0.0.1/ && curl -fsS http://127.0.0.1/api/v1/products"
            '''
        }
    }
} 
    }

    post {
        success {
            echo 'SUCCESS: CI/CD pipeline and AWS deployment completed!'
        }

        failure {
            echo 'FAILED: Check the Jenkins console output.'
        }

        always {
            echo 'Pipeline execution finished.'
        }
    }
}