pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
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
                echo 'Building Docker images with temporary CI environment values'

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
                echo 'Scanning Docker images for vulnerabilities'

                bat '''
                    if not exist security-reports mkdir security-reports

                    trivy image --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/user-service-trivy.json ecommerce-platform-user-service:latest

                    trivy image --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/product-service-trivy.json ecommerce-platform-product-service:latest

                    trivy image --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/order-service-trivy.json ecommerce-platform-order-service:latest
                '''
            }
        }

        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: 'security-reports/*.json',
                                 allowEmptyArchive: true
            }
        }
    }

    post {
        success {
            echo 'CI pipeline completed successfully!'
        }

        failure {
            echo 'CI pipeline failed. Check the Jenkins console output.'
        }

        always {
            echo 'Pipeline execution finished.'
        }
    }
}