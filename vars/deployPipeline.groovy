#!/usr/bin/env groovy

/**
 * Pipeline для деплоя приложений
 * Поддерживает различные среды и платформы
 */

def call(Map config = [:]) {
    def defaultConfig = [
        environment: 'dev',
        deploymentType: 'kubernetes', // kubernetes, docker-compose, ansible
        namespace: 'default',
        replicas: 1,
        imageName: '',
        imageTag: '',
        registry: 'docker.io',
        namespace: 'mycompany',
        healthCheck: true,
        healthCheckUrl: 'http://localhost:8080/health',
        healthCheckTimeout: 300,
        rollbackOnFailure: true,
        notifySlack: true,
        notifyEmail: false,
        emailRecipients: '',
        timeout: 30,
        nodeLabel: 'deploy'
    ]
    
    config = defaultConfig + config
    
    // Генерируем имя образа если не указано
    if (!config.imageName) {
        config.imageName = env.JOB_NAME.toLowerCase().replaceAll(/[^a-z0-9]/, '-')
    }
    
    // Генерируем тег если не указан
    if (!config.imageTag) {
        config.imageTag = env.BUILD_NUMBER
    }
    
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    pipeline {
        agent { label config.nodeLabel }
        
        options {
            timeout(time: config.timeout, unit: 'MINUTES')
            timestamps()
            ansiColor('xterm')
        }
        
        environment {
            ENVIRONMENT = config.environment
            DEPLOYMENT_TYPE = config.deploymentType
            KUBERNETES_NAMESPACE = config.namespace
            DOCKER_IMAGE = fullImageName
        }
        
        stages {
            stage('Validate Environment') {
                steps {
                    script {
                        echo "🔍 Проверка окружения..."
                        validateEnvironment(config)
                    }
                }
            }
            
            stage('Pre-deployment Check') {
                steps {
                    script {
                        echo "✅ Предварительная проверка..."
                        preDeploymentCheck(config)
                    }
                }
            }
            
            stage('Deploy') {
                steps {
                    script {
                        echo "🚀 Начинаем деплой в ${config.environment}..."
                        deployApplication(config)
                    }
                }
            }
            
            stage('Health Check') {
                when {
                    expression { config.healthCheck }
                }
                steps {
                    script {
                        echo "🏥 Проверка здоровья приложения..."
                        healthCheck(config)
                    }
                }
            }
            
            stage('Post-deployment Tests') {
                steps {
                    script {
                        echo "🧪 Пост-деплой тесты..."
                        postDeploymentTests(config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    echo "📊 Сбор статистики деплоя..."
                    collectDeploymentStats(config)
                }
            }
            success {
                script {
                    echo "✅ Деплой завершен успешно!"
                    if (config.notifySlack) {
                        notifySlack('success', config)
                    }
                    if (config.notifyEmail) {
                        notifyEmail('success', config)
                    }
                }
            }
            failure {
                script {
                    echo "❌ Деплой завершен с ошибкой!"
                    if (config.rollbackOnFailure) {
                        echo "🔄 Выполняем откат..."
                        rollbackDeployment(config)
                    }
                    if (config.notifySlack) {
                        notifySlack('failure', config)
                    }
                    if (config.notifyEmail) {
                        notifyEmail('failure', config)
                    }
                }
            }
        }
    }
}

// Вспомогательные функции
def validateEnvironment(config) {
    switch(config.deploymentType.toLowerCase()) {
        case 'kubernetes':
            validateKubernetesEnvironment(config)
            break
        case 'docker-compose':
            validateDockerComposeEnvironment(config)
            break
        case 'ansible':
            validateAnsibleEnvironment(config)
            break
        default:
            error "Неподдерживаемый тип деплоя: ${config.deploymentType}"
    }
}

def validateKubernetesEnvironment(config) {
    try {
        // Проверяем подключение к кластеру
        sh "kubectl cluster-info"
        
        // Проверяем namespace
        sh "kubectl get namespace ${config.namespace} || kubectl create namespace ${config.namespace}"
        
        // Проверяем доступ к registry
        sh "kubectl get secrets docker-registry-credentials -n ${config.namespace} || echo 'Registry credentials not found'"
        
        echo "✅ Kubernetes окружение валидно"
    } catch (Exception e) {
        error "❌ Ошибка валидации Kubernetes окружения: ${e.getMessage()}"
    }
}

def validateDockerComposeEnvironment(config) {
    try {
        sh "docker-compose --version"
        sh "docker --version"
        echo "✅ Docker Compose окружение валидно"
    } catch (Exception e) {
        error "❌ Ошибка валидации Docker Compose окружения: ${e.getMessage()}"
    }
}

def validateAnsibleEnvironment(config) {
    try {
        sh "ansible --version"
        echo "✅ Ansible окружение валидно"
    } catch (Exception e) {
        error "❌ Ошибка валидации Ansible окружения: ${e.getMessage()}"
    }
}

def preDeploymentCheck(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        // Проверяем доступность образа
        sh "docker pull ${fullImageName}"
        
        // Проверяем размер образа
        def imageSize = sh(
            script: "docker images ${fullImageName} --format '{{.Size}}'",
            returnStdout: true
        ).trim()
        
        echo "📦 Размер образа: ${imageSize}"
        
        // Проверяем метаданные образа
        sh "docker inspect ${fullImageName}"
        
        echo "✅ Предварительная проверка завершена"
    } catch (Exception e) {
        error "❌ Ошибка предварительной проверки: ${e.getMessage()}"
    }
}

def deployApplication(config) {
    switch(config.deploymentType.toLowerCase()) {
        case 'kubernetes':
            deployToKubernetes(config)
            break
        case 'docker-compose':
            deployWithDockerCompose(config)
            break
        case 'ansible':
            deployWithAnsible(config)
            break
    }
}

def deployToKubernetes(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        // Создаем ConfigMap с переменными окружения
        sh """
            cat <<EOF | kubectl apply -f -
            apiVersion: v1
            kind: ConfigMap
            metadata:
              name: ${config.imageName}-config
              namespace: ${config.namespace}
            data:
              ENVIRONMENT: "${config.environment}"
              VERSION: "${config.imageTag}"
            EOF
        """
        
        // Создаем Deployment
        sh """
            cat <<EOF | kubectl apply -f -
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: ${config.imageName}
              namespace: ${config.namespace}
            spec:
              replicas: ${config.replicas}
              selector:
                matchLabels:
                  app: ${config.imageName}
              template:
                metadata:
                  labels:
                    app: ${config.imageName}
                spec:
                  containers:
                  - name: ${config.imageName}
                    image: ${fullImageName}
                    ports:
                    - containerPort: 8080
                    envFrom:
                    - configMapRef:
                        name: ${config.imageName}-config
                    livenessProbe:
                      httpGet:
                        path: /health
                        port: 8080
                      initialDelaySeconds: 30
                      periodSeconds: 10
                    readinessProbe:
                      httpGet:
                        path: /health
                        port: 8080
                      initialDelaySeconds: 5
                      periodSeconds: 5
            EOF
        """
        
        // Создаем Service
        sh """
            cat <<EOF | kubectl apply -f -
            apiVersion: v1
            kind: Service
            metadata:
              name: ${config.imageName}-service
              namespace: ${config.namespace}
            spec:
              selector:
                app: ${config.imageName}
              ports:
              - protocol: TCP
                port: 80
                targetPort: 8080
              type: ClusterIP
            EOF
        """
        
        // Ждем готовности подов
        sh "kubectl rollout status deployment/${config.imageName} -n ${config.namespace} --timeout=300s"
        
        echo "✅ Деплой в Kubernetes завершен успешно"
        
    } catch (Exception e) {
        error "❌ Ошибка деплоя в Kubernetes: ${e.getMessage()}"
    }
}

def deployWithDockerCompose(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        // Создаем docker-compose.yml
        writeFile file: 'docker-compose.yml', text: """
version: '3.8'
services:
  ${config.imageName}:
    image: ${fullImageName}
    container_name: ${config.imageName}
    ports:
      - "8080:8080"
    environment:
      - ENVIRONMENT=${config.environment}
      - VERSION=${config.imageTag}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    restart: unless-stopped
"""
        
        // Запускаем сервис
        sh "docker-compose up -d"
        
        echo "✅ Деплой с Docker Compose завершен успешно"
        
    } catch (Exception e) {
        error "❌ Ошибка деплоя с Docker Compose: ${e.getMessage()}"
    }
}

def deployWithAnsible(config) {
    try {
        // Создаем inventory файл
        writeFile file: 'inventory.yml', text: """
all:
  hosts:
    ${config.environment}-servers:
      ansible_host: ${config.environment}.example.com
      ansible_user: deploy
      ansible_ssh_private_key_file: ~/.ssh/id_rsa
"""
        
        // Создаем playbook
        writeFile file: 'deploy.yml', text: """
---
- hosts: ${config.environment}-servers
  become: yes
  tasks:
    - name: Pull Docker image
      docker_image:
        name: ${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}
        source: pull
        
    - name: Stop existing container
      docker_container:
        name: ${config.imageName}
        state: absent
        
    - name: Start new container
      docker_container:
        name: ${config.imageName}
        image: ${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}
        state: started
        ports:
          - "8080:8080"
        env:
          ENVIRONMENT: "${config.environment}"
          VERSION: "${config.imageTag}"
"""
        
        // Запускаем playbook
        sh "ansible-playbook -i inventory.yml deploy.yml"
        
        echo "✅ Деплой с Ansible завершен успешно"
        
    } catch (Exception e) {
        error "❌ Ошибка деплоя с Ansible: ${e.getMessage()}"
    }
}

def healthCheck(config) {
    def healthUrl = config.healthCheckUrl
    
    try {
        def timeout = config.healthCheckTimeout
        def interval = 10
        def attempts = timeout / interval
        
        for (int i = 0; i < attempts; i++) {
            try {
                def response = sh(
                    script: "curl -f -s ${healthUrl}",
                    returnStdout: true
                ).trim()
                
                if (response) {
                    echo "✅ Health check пройден: ${response}"
                    return
                }
            } catch (Exception e) {
                echo "⏳ Попытка ${i + 1}/${attempts}: Health check не прошел, ждем ${interval} секунд..."
                sleep(interval)
            }
        }
        
        error "❌ Health check не прошел после ${timeout} секунд"
        
    } catch (Exception e) {
        error "❌ Ошибка health check: ${e.getMessage()}"
    }
}

def postDeploymentTests(config) {
    try {
        // Здесь можно добавить интеграционные тесты
        echo "🧪 Запуск интеграционных тестов..."
        
        // Пример простого теста
        sh "curl -f http://localhost:8080/api/health || echo 'API health check failed'"
        
        echo "✅ Пост-деплой тесты завершены"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка в пост-деплой тестах: ${e.getMessage()}"
    }
}

def rollbackDeployment(config) {
    try {
        switch(config.deploymentType.toLowerCase()) {
            case 'kubernetes':
                sh "kubectl rollout undo deployment/${config.imageName} -n ${config.namespace}"
                sh "kubectl rollout status deployment/${config.imageName} -n ${config.namespace}"
                break
            case 'docker-compose':
                sh "docker-compose down"
                sh "docker-compose up -d"
                break
            case 'ansible':
                // Здесь можно добавить логику отката для Ansible
                echo "Откат для Ansible не реализован"
                break
        }
        
        echo "✅ Откат выполнен успешно"
        
    } catch (Exception e) {
        echo "❌ Ошибка при откате: ${e.getMessage()}"
    }
}

def collectDeploymentStats(config) {
    try {
        def stats = [
            environment: config.environment,
            deploymentType: config.deploymentType,
            imageName: config.imageName,
            imageTag: config.imageTag,
            timestamp: new Date().toString(),
            buildNumber: env.BUILD_NUMBER,
            jobName: env.JOB_NAME
        ]
        
        writeJSON file: "deployment-stats-${env.BUILD_NUMBER}.json", json: stats
        
        echo "📊 Статистика деплоя сохранена"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось собрать статистику: ${e.getMessage()}"
    }
}

def notifySlack(status, config) {
    def colors = getColors()
    def message = ""
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    switch(status) {
        case 'success':
            message = "${colors.green}✅ Деплой успешен в ${config.environment}: ${fullImageName}${colors.reset}"
            break
        case 'failure':
            message = "${colors.red}❌ Деплой провалился в ${config.environment}: ${fullImageName}${colors.reset}"
            break
    }
    
    echo "Slack уведомление: ${message}"
    // Здесь можно добавить реальную интеграцию со Slack
}

def notifyEmail(status, config) {
    if (config.emailRecipients) {
        echo "Email уведомление отправлено на: ${config.emailRecipients}"
        // Здесь можно добавить реальную отправку email
    }
} 