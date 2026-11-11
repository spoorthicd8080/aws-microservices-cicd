# E-commerce Microservices – AWS DevOps CI/CD Project

A production-patterned e-commerce backend consisting of three independently deployable Spring Boot microservices, each owning its own PostgreSQL database, secured with RS256-signed JWTs and communicating over REST.

**Technology Stack:** Java 26 · Spring Boot 3.5 · PostgreSQL 16 · Docker · Docker Compose · Jenkins · AWS · Terraform · Amazon ECR · Nginx · Prometheus · Grafana · OpenFeign · Flyway · Testcontainers

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Quick Start](#quick-start)
5. [Security Model](#security-model)
6. [Design Decisions](#design-decisions)
7. [AWS Infrastructure](#aws-infrastructure)
8. [Source Code Management](#source-code-management)
9. [Docker Containerization and Deployment](#docker-containerization-and-deployment)
10. [Nginx Reverse Proxy](#nginx-reverse-proxy)
11. [Terraform Infrastructure as Code](#terraform-infrastructure-as-code)
12. [Jenkins CI/CD Pipeline](#jenkins-cicd-pipeline)
13. [Prometheus Monitoring](#prometheus-monitoring)
14. [Grafana Dashboards](#grafana-dashboards)
15. [Logging and Troubleshooting](#logging-and-troubleshooting)
16. [Failure Simulation and Recovery](#failure-simulation-and-recovery)
17. [Security Practices](#security-practices)
18. [Verification](#verification)
19. [Screenshots and Evidence](#screenshots-and-evidence)
20. [Documentation](#documentation)
21. [Known Limitations](#known-limitations)
22. [Cleanup](#cleanup)
23. [Roadmap](#roadmap)

---

## Project Overview

This project demonstrates the deployment and monitoring of an e-commerce microservices application using AWS and DevOps tools.

The application consists of three independently deployable services:

- **User Service:** Customer management, authentication and addresses.
- **Product Service:** Product catalogue, categories, search and pricing.
- **Order Service:** Order placement, lifecycle management and service orchestration.

The DevOps implementation automates infrastructure provisioning, containerization, CI/CD deployment, monitoring and troubleshooting.

---

## Architecture

| Service | Port | Database | Responsibility |
|---|---:|---|---|
| User Service | 8081 | ecommerce_user_db | Customers, addresses, authentication and token issuing |
| Product Service | 8082 | ecommerce_product_db | Catalogue, categories, search and batch price lookup |
| Order Service | 8083 | ecommerce_order_db | Order placement, lifecycle and cross-service orchestration |

Databases are logically isolated using separate logins. Services do not access one another's tables, and there are no cross-service foreign keys.

The Order Service communicates with the User and Product Services synchronously using OpenFeign and forwards the caller's JWT downstream.

### Application Architecture

```text
                         Client
                           |
                           v
                  +------------------+
                  |  Order Service   |
                  |      :8083       |
                  +--------+---------+
                           |
                    JWT forwarding
                           |
              +------------+------------+
              |                         |
              v                         v
      +---------------+        +----------------+
      | User Service  |        | Product Service|
      |     :8081     |        |      :8082     |
      +---------------+        +----------------+
              |                         |
              v                         v
       User Database             Product Database
              |
              v
       Order Database
```

### DevOps Deployment Architecture

```text
                  Developer
                      |
                      v
                 GitHub Repo
                      |
                      v
                 Jenkins CI/CD
                      |
              Build Docker Images
                      |
                      v
                  Amazon ECR
                      |
                      v
                AWS EC2 Instance
                      |
                Docker Compose
                      |
          +-----------+-----------+
          |           |           |
          v           v           v
        User       Product      Order
       Service     Service     Service
          |           |           |
          +-----------+-----------+
                      |
                  PostgreSQL
                      |
                      v
                    Nginx
                      |
                   Clients

Monitoring:

EC2 Node Exporter
        |
    SSH Tunnel
        |
        v
   Prometheus
        |
        v
     Grafana
```

---

## Technology Stack

| Category | Technologies |
|---|---|
| Programming Language | Java |
| Backend Framework | Spring Boot |
| Database | PostgreSQL 16 |
| Database Migration | Flyway |
| Service Communication | OpenFeign, REST |
| Authentication | JWT, RS256 |
| Containerization | Docker |
| Container Orchestration | Docker Compose |
| CI/CD | Jenkins |
| Source Control | Git, GitHub |
| Cloud Platform | AWS |
| Compute | Amazon EC2 |
| Container Registry | Amazon ECR |
| Infrastructure as Code | Terraform |
| Reverse Proxy | Nginx |
| Monitoring | Prometheus |
| Metrics Exporter | Node Exporter |
| Visualization | Grafana |

---

## Quick Start

### Prerequisites

Install the following tools:

- Java and Maven
- Git
- Docker Desktop
- Docker Compose
- AWS CLI
- Terraform
- Jenkins

### Local Application Setup

Clone the repository:

```bash
git clone https://github.com/spoorthicd8080/aws-microservices-cicd.git

cd ecommerce-microservices
```

Configure environment variables:

```bash
cp .env.example .env
```

Update the environment variables with appropriate values. Do not commit `.env` to GitHub.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Build the application:

```bash
mvn clean package -DskipTests
```

Start User Service first because it generates the RSA key pair on its first run.

```bash
set -a
source .env
set +a

mvn -pl user-service spring-boot:run
```

Then start Product Service and Order Service.

### Windows PowerShell

The project also provides PowerShell scripts:

```powershell
.\scripts\run.ps1 user

.\scripts\run.ps1 product

.\scripts\run.ps1 order
```

Additional validation scripts:

```powershell
.\scripts\doctor.ps1

.\scripts\seed-data.ps1

.\scripts\verify-phase2.ps1
```

API documentation is available through `/swagger-ui.html` on the respective service ports.

Health checks are available through `/actuator/health`.

---

## Security Model

Authentication is issued by User Service through:

`POST /api/v1/auth/login`

Every service verifies JWT signatures locally.

Tokens use RS256 asymmetric signing:

- User Service holds the private key and issues tokens.
- Product Service and Order Service use the public key for verification.
- The private key is not shared with the other services.

The key pair is generated during initial startup and stored in the git-ignored `keys/` directory.

### Security Rules

| Rule | Enforcement |
|---|---|
| Default deny | Authenticated requests required by default |
| Public endpoints | Login, registration, catalogue reads, Swagger and health probes |
| Ownership | Customer-scoped endpoint ownership validation |
| Roles | Catalogue writes and order fulfilment restricted to ADMIN |
| Token propagation | Order Service forwards the caller's JWT |
| Stateless authentication | No HTTP session is created |
| Password protection | Passwords are BCrypt-hashed |

Login attempts are rate limited per IP and email. Login failures return consistent responses to reduce account enumeration.

---

## Design Decisions

The reasoning behind the architecture and implementation is documented in `docs/phase-1-design.md` and `docs/CHANGELOG.md`.

- Database per service to reduce coupling.
- RS256 asymmetric token signing.
- Prices are calculated by the backend rather than accepted from clients.
- Orders retain snapshots of referenced product and address information.
- Remote service calls happen outside database transactions.
- Downstream errors are translated into appropriate HTTP responses.
- Order transitions are managed through an order state machine.
- Business identifiers are separate from database primary keys.
- Optimistic locking protects concurrent updates.
- Flyway manages database schema migrations.
- Testcontainers provides integration testing with PostgreSQL.

---

## AWS Infrastructure

AWS hosts the deployed microservices application.

### AWS Resources

| Resource | Purpose |
|---|---|
| Amazon EC2 | Ubuntu server for application deployment |
| Amazon ECR | Stores Docker images |
| AWS IAM Role | Provides AWS permissions to EC2 |
| IAM Instance Profile | Associates the IAM role with EC2 |
| Security Group | Controls network access |
| Amazon VPC | Provides network isolation |

### EC2 Deployment

The application is deployed on an Ubuntu EC2 instance.

The EC2 instance hosts:

- User Service
- Product Service
- Order Service
- PostgreSQL
- Docker containers
- Nginx reverse proxy
- Node Exporter

The three application services are deployed using Docker Compose.

### Amazon ECR Repositories

Three ECR repositories store the application images:

```text
ecommerce-devops-user-service

ecommerce-devops-product-service

ecommerce-devops-order-service
```

Jenkins builds and pushes Docker images to the corresponding repositories.

---

## Source Code Management

Git and GitHub are used for source code management.

GitHub repository:

https://github.com/spoorthicd8080/aws-microservices-cicd

Git provides version control and tracks changes to application source code and DevOps configuration.

The repository contains:

- Microservices source code.
- Docker configuration.
- Jenkins pipeline configuration.
- Terraform infrastructure configuration.
- Prometheus monitoring configuration.
- Documentation and deployment instructions.

Sensitive credentials, private keys and environment files must not be committed.

---

## Docker Containerization and Deployment

Docker is used to package the Spring Boot microservices into independent containers.

### Application Containers

```text
ecommerce-user-prod

ecommerce-product-prod

ecommerce-order-prod

ecommerce-postgres-prod
```

### Production Deployment

The production Docker Compose configuration is maintained on the EC2 instance at:

```text
/opt/ecommerce/docker-compose.prod.yml
```

Docker Compose manages the application containers and PostgreSQL database.

Container health and status can be checked using:

```bash
sudo docker ps
```

Application logs can be inspected using:

```bash
sudo docker logs --tail 30 ecommerce-user-prod

sudo docker logs --tail 30 ecommerce-product-prod

sudo docker logs --tail 30 ecommerce-order-prod
```

---

## Nginx Reverse Proxy

Nginx is configured as a reverse proxy to provide a common HTTP entry point for the application.

It routes incoming requests to the appropriate microservice.

| Service | Internal Port |
|---|---:|
| User Service | 8081 |
| Product Service | 8082 |
| Order Service | 8083 |

The application services use internal loopback bindings, while Nginx handles incoming HTTP traffic.

Nginx configuration is maintained on the EC2 instance.

Configuration validation:

```bash
sudo nginx -t
```

Reload after a valid configuration change:

```bash
sudo systemctl reload nginx
```

---

## Terraform Infrastructure as Code

Terraform is used to provision and manage AWS infrastructure.

Terraform configuration files are maintained in:

```text
infra/terraform/
```

### Terraform Workflow

Initialize Terraform:

```bash
terraform -chdir=infra/terraform init
```

Validate configuration:

```bash
terraform -chdir=infra/terraform validate
```

Generate an execution plan:

```bash
terraform -chdir=infra/terraform plan
```

Apply the configuration:

```bash
terraform -chdir=infra/terraform apply
```

### Infrastructure Management

Terraform manages the configured AWS resources, including:

- EC2 instance.
- ECR repositories.
- Security group.
- IAM permissions.
- Related networking configuration.

Terraform variables are maintained separately from the infrastructure resource definitions.

Terraform outputs provide information such as EC2 instance ID, public IP address, VPC ID and ECR repository URLs.

Terraform state files contain infrastructure metadata and must not be committed to a public GitHub repository.

---

## Jenkins CI/CD Pipeline

Jenkins automates the build and deployment workflow.

### Pipeline Stages

1. **Checkout:** Retrieves source code from GitHub.
2. **Verify Tools:** Checks the required build tools.
3. **Build:** Builds and validates the microservices.
4. **Docker Build:** Creates Docker images for the application services.
5. **ECR Authentication:** Authenticates with Amazon ECR.
6. **Image Push:** Pushes application images to ECR.
7. **Deployment:** Deploys the application containers to EC2.
8. **Verification:** Checks container status and application health.

### CI/CD Benefits

- Repeatable application builds.
- Automated Docker image creation.
- Centralized container image storage.
- Automated deployment workflow.
- Build status and console logs for troubleshooting.

The Jenkins pipeline configuration is maintained in the repository's `Jenkinsfile`.

---

## Prometheus Monitoring

Prometheus is used to collect and query infrastructure monitoring metrics.

### Monitoring Components

- Prometheus collects metrics at configured intervals.
- Node Exporter exposes EC2 system metrics.
- Prometheus monitors Node Exporter availability.
- Prometheus targets indicate whether monitoring endpoints are reachable.

### Node Exporter

Node Exporter runs on the EC2 instance and exposes system metrics, including:

- CPU statistics.
- Memory utilization.
- Disk usage.
- Filesystem information.
- System information.

### Secure Monitoring Connectivity

The monitoring stack runs locally using Docker Compose.

An SSH tunnel connects local Prometheus to the EC2 Node Exporter endpoint.

This avoids exposing Node Exporter publicly through an AWS security group rule.

Prometheus configuration is maintained in:

```text
monitoring/prometheus.yml
```

---

## Grafana Dashboards

Grafana is connected to Prometheus as its monitoring data source.

The Grafana dashboard provides a visual overview of EC2 infrastructure metrics.

### Dashboard Panels

| Panel | Purpose |
|---|---|
| EC2 CPU Usage | Displays CPU utilization |
| EC2 Memory Usage | Displays memory utilization |
| EC2 Disk Usage | Displays disk usage percentage |
| EC2 Node Exporter Availability | Displays monitoring target availability |

### Monitoring Stack

The monitoring containers are managed through:

```text
docker-compose.monitoring.yml
```

Local monitoring endpoints:

| Component | Local URL |
|---|---|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

Grafana provides dashboards for visualizing metrics collected by Prometheus.

---

## Logging and Troubleshooting

Docker logs and application health endpoints are used to identify deployment and runtime issues.

### Container Status

```bash
sudo docker ps
```

### Application Logs

```bash
sudo docker logs --tail 30 ecommerce-user-prod

sudo docker logs --tail 30 ecommerce-product-prod

sudo docker logs --tail 30 ecommerce-order-prod
```

### PostgreSQL Status

```bash
sudo docker ps
```

Verify that the PostgreSQL container reports a healthy status.

### Troubleshooting Areas

- Docker container startup failures.
- Application startup errors.
- PostgreSQL connectivity.
- Database migration failures.
- Nginx configuration issues.
- Jenkins pipeline failures.
- ECR authentication and image push issues.
- Prometheus target availability.
- Grafana data source connectivity.

---

## Failure Simulation and Recovery

A separate temporary Nginx container was used to demonstrate container failure and recovery.

### Failure Simulation

1. Start a temporary Nginx container.
2. Verify that the container is running.
3. Stop the container using Docker Desktop.
4. Verify that its status changes to Exited.

### Recovery

1. Start the stopped test container again.
2. Verify that its status changes to Running.
3. Confirm that the container has recovered.

This exercise demonstrates basic container troubleshooting and recovery without interrupting the production microservices or database.

---

## Security Practices

The project follows basic security practices across application deployment and infrastructure management.

- AWS credentials are not hardcoded in application source code.
- Sensitive environment files are excluded from Git.
- Private keys are not committed to the repository.
- EC2 uses an IAM role for AWS permissions.
- SSH access is restricted through security group rules.
- Internal application ports are not directly exposed to the public internet.
- Node Exporter is accessed through an SSH tunnel.
- Grafana and Prometheus are bound to the local environment.
- Terraform state and sensitive configuration files are excluded from version control.

**Production security note:** Application logs during the demonstration showed Spring Boot generated development security passwords. These defaults must be replaced with an explicitly configured production security setup before real production use.

---

## Verification

The deployment and monitoring implementation can be verified using the following checks.

| Component | Verification |
|---|---|
| EC2 | Instance state is Running |
| ECR | Three service repositories are available |
| Docker | Application containers are running |
| PostgreSQL | Database container reports healthy |
| User Service | Actuator health endpoint |
| Product Service | Actuator health endpoint |
| Order Service | Actuator health endpoint |
| Nginx | Gateway HTTP response |
| Jenkins | Successful pipeline execution |
| Prometheus | Monitoring targets report UP |
| Grafana | Dashboard displays infrastructure metrics |
| Failure Recovery | Temporary test container returns to Running |

---

## Screenshots and Evidence

Project screenshots are maintained in:

```text
screenshots/
```

The evidence includes:

- EC2 instance status.
- ECR repositories and service images.
- EC2 security group configuration.
- Application service responses.
- Terraform infrastructure configuration.
- Jenkins successful pipeline.
- CloudWatch monitoring.
- Prometheus targets.
- Grafana data source.
- EC2 CPU, memory and disk metrics.
- Grafana monitoring dashboard.
- Container failure simulation.
- Container recovery.

---

## Documentation

| Document | Contents |
|---|---|
| [Phase 1 Design](docs/phase-1-design.md) | Architecture, database design, API contract and communication |
| [Security Walkthrough](docs/security-walkthrough.md) | Authentication implementation and security reasoning |
| [Changelog](docs/CHANGELOG.md) | Project changes and implementation history |
| [Service Walkthroughs](docs/) | Component-by-component service explanations |
| [Beginner's Guide](docs/beginners-guide.md) | Project explanation in simple language |
| [Day 3 Setup](docs/day-3-setup.md) | Deployment and environment setup documentation |

---

## Known Limitations

The following improvements are documented for future development:

- The public key is distributed as a file. A JWKS endpoint would simplify key distribution and rotation.
- Tokens cannot be revoked immediately. Short expiry limits the impact of a leaked token.
- Login rate limits are maintained per instance. Redis could provide shared rate limiting.
- The PostgreSQL container has a fixed name, limiting simultaneous deployments of multiple checkouts.
- The application does not currently include a dedicated API gateway.
- Distributed tracing is not yet implemented.
- Monitoring currently focuses on infrastructure metrics and Node Exporter availability; dedicated application-level metrics can be added later.

---

## Cleanup

Review AWS and Docker resources before performing cleanup.

### AWS Resource Review

Check the EC2 instance, ECR repositories, security groups and IAM resources before removing anything.

### Docker Resource Review

Identify unused containers and images before cleanup.

### Important Data Protection

- Preserve production database volumes.
- Do not remove active production containers.
- Do not delete Terraform state files before reviewing infrastructure dependencies.
- Do not remove the EC2 instance while the deployment is still required.
- Remove unused resources only after confirming that they are no longer needed.

Terraform destruction should only be performed when the project deployment is no longer required.

---

## Roadmap

Future improvements:

Resilience using circuit breakers → Event-driven communication with Kafka → Redis caching → Dedicated API gateway → Distributed tracing → React frontend.

Prometheus and Grafana infrastructure monitoring have already been implemented as part of this DevOps project.

---

## Conclusion

This project demonstrates an end-to-end DevOps workflow for a microservices application using GitHub, Jenkins, Docker, AWS, Terraform, Amazon ECR, Nginx, Prometheus and Grafana.

It covers infrastructure provisioning, CI/CD automation, containerized deployment, application health verification, monitoring, logging, failure simulation and recovery.

The implementation provides a foundation for repeatable microservices deployment and infrastructure monitoring.

---