# Git Branching Strategy

## 1. Overview
This project follows a structured Git branching strategy to manage source code development, integration, and production releases.

## 2. Branches

### Main Branch
- Branch name: `main`
- Contains stable and production-ready code.
- Changes are merged after proper review and testing.

### Develop Branch
- Branch name: `develop`
- Used for integrating development changes.
- Acts as the integration branch before production releases.

### Feature Branches
- Naming convention: `feature/<feature-name>`
- Created from the develop branch.
- Used to implement individual features or configurations.
- Changes are submitted through Pull Requests.

## 3. Development Workflow

1. Developers start from the develop branch.
2. Create a separate feature branch.
3. Implement and test changes.
4. Commit and push changes to GitHub.
5. Create a Pull Request targeting develop.
6. Review and merge approved changes into develop.
7. Merge tested and approved changes into main for production releases.

## 4. Benefits

- Organized development workflow.
- Better collaboration between developers.
- Easier code reviews.
- Reduced risk of affecting production code.
- Improved source code management.

## 5. Repository

GitHub Repository: https://github.com/spoorthicd8080/aws-microservices-cicd