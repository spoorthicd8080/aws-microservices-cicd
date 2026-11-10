pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        MAVEN_HOME = 'C:\\Users\\sudha\\Downloads\\apache-maven-3.10.0'
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
                bat 'call "%MAVEN_HOME%\\bin\\mvn.cmd" -version'
                bat 'docker --version'
                bat 'docker compose version'
                bat 'trivy --version'
            }
        }

        stage('Build and Test') {
            steps {
                echo 'Building User Service'
                bat 'call "%MAVEN_HOME%\\bin\\mvn.cmd" -B -f user-service\\pom.xml clean verify'

                echo 'Building Product Service'
                bat 'call "%MAVEN_HOME%\\bin\\mvn.cmd" -B -f product-service\\pom.xml clean verify'

                echo 'Building Order Service'
                bat 'call "%MAVEN_HOME%\\bin\\mvn.cmd" -B -f order-service\\pom.xml clean verify'
            }
        }

        stage('Build Docker Images') {
            steps {
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

                bat 'trivy image --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/user-service-trivy.json ecommerce-platform-user-service'

                bat 'trivy image --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/product-service-trivy.json ecommerce-platform-product-service'

                bat 'trivy image --severity HIGH,CRITICAL --exit-code 0 --format json --output security-reports/order-service-trivy.json ecommerce-platform-order-service'
            }
        }

        stage('Archive Reports') {
            steps {
                archiveArtifacts artifacts: 'security-reports/*.json',
                                 allowEmptyArchive: false
            }
        }
    }

    post {
        success {
            echo 'SUCCESS: All three microservices built and security scans completed!'
        }

        failure {
            echo 'BUILD FAILED: Check Jenkins Console Output.'
        }

        always {
            echo 'Pipeline execution completed.'
        }
    }
}