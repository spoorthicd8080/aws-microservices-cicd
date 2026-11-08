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

        stage('Prepare CI JWT Public Key') {
            steps {
                echo 'Generating temporary RSA public key for CI tests'

                bat '''
                    if not exist keys mkdir keys

                    powershell -NoProfile -ExecutionPolicy Bypass -Command "$rsa = [System.Security.Cryptography.RSA]::Create(2048); $b64 = [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()); $lines = [regex]::Matches($b64, '.{1,64}') | ForEach-Object { $_.Value }; $pem = @('-----BEGIN PUBLIC KEY-----') + $lines + @('-----END PUBLIC KEY-----'); Set-Content -Path 'keys\\jwt-public.pem' -Value $pem -Encoding ascii"

                    if not exist keys\\jwt-public.pem exit /b 1

                    echo CI public key generated successfully.
                '''
            }
        }

        stage('Build and Test') {
            steps {
                echo 'Building and testing all microservices'

                bat 'call "C:\\Users\\sudha\\Downloads\\apache-maven-3.10.0\\bin\\mvn.cmd" -B -Dmaven.compiler.release=21 clean verify'
            }
        }

        stage('Build Docker Images') {
            steps {
                echo 'Building all microservice Docker images'

                bat 'docker compose --profile apps build'
            }
        }

        stage('Trivy Security Scan') {
            steps {
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