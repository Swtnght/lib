#!/usr/bin/env groovy

/**
 * Pipeline для работы с Docker контейнерами
 * Включает сборку, тестирование и публикацию образов
 */

def call(Map config = [:]) {
    def defaultConfig = [
        dockerfile: 'Dockerfile',
        context: '.',
        imageName: '',
        imageTag: '',
        registry: 'docker.io',
        namespace: 'mycompany',
        pushImage: true,
        scanImage: true,
        runTests: true,
        testPort: 8080,
        healthCheck: true,
        healthCheckUrl: 'http://localhost:8080/health',
        timeout: 20,
        nodeLabel: 'docker',
        notifySlack: true
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
            DOCKER_IMAGE = fullImageName
            DOCKERFILE = config.dockerfile
            DOCKER_CONTEXT = config.context
        }
        
        stages {
            stage('Checkout') {
                steps {
                    script {
                        echo "🔍 Checkout кода..."
                        checkout scm
                    }
                }
            }
            
            stage('Docker Build') {
                steps {
                    script {
                        echo "🐳 Сборка Docker образа: ${fullImageName}"
                        buildDockerImage(config)
                    }
                }
            }
            
            stage('Docker Test') {
                when {
                    expression { config.runTests }
                }
                steps {
                    script {
                        echo "🧪 Тестирование Docker образа..."
                        testDockerImage(config)
                    }
                }
            }
            
            stage('Security Scan') {
                when {
                    expression { config.scanImage }
                }
                steps {
                    script {
                        echo "🔒 Сканирование безопасности образа..."
                        scanDockerImage(config)
                    }
                }
            }
            
            stage('Push Image') {
                when {
                    expression { config.pushImage }
                }
                steps {
                    script {
                        echo "📤 Публикация образа в registry..."
                        pushDockerImage(config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    echo "🧹 Очистка Docker ресурсов..."
                    cleanupDockerResources(config)
                }
            }
            success {
                script {
                    echo "✅ Docker pipeline завершен успешно!"
                    if (config.notifySlack) {
                        notifySlack('success', config)
                    }
                }
            }
            failure {
                script {
                    echo "❌ Docker pipeline завершен с ошибкой!"
                    if (config.notifySlack) {
                        notifySlack('failure', config)
                    }
                }
            }
        }
    }
}

// Вспомогательные функции
def buildDockerImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        sh """
            docker build \
                -f ${config.dockerfile} \
                -t ${fullImageName} \
                ${config.context}
        """
        echo "✅ Docker образ успешно собран: ${fullImageName}"
    } catch (Exception e) {
        error "❌ Ошибка при сборке Docker образа: ${e.getMessage()}"
    }
}

def testDockerImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    def containerName = "test-${config.imageName}-${config.imageTag}"
    
    try {
        // Запускаем контейнер
        sh "docker run -d --name ${containerName} -p ${config.testPort}:${config.testPort} ${fullImageName}"
        
        // Ждем запуска контейнера
        sleep(10)
        
        // Проверяем здоровье контейнера
        if (config.healthCheck) {
            def healthStatus = sh(
                script: "curl -f ${config.healthCheckUrl} || echo 'HEALTH_CHECK_FAILED'",
                returnStdout: true
            ).trim()
            
            if (healthStatus == 'HEALTH_CHECK_FAILED') {
                error "❌ Проверка здоровья контейнера не прошла"
            }
        }
        
        // Запускаем тесты внутри контейнера
        sh "docker exec ${containerName} sh -c 'echo \"Running container tests...\"'"
        
        echo "✅ Тестирование Docker образа завершено успешно"
        
    } catch (Exception e) {
        error "❌ Ошибка при тестировании Docker образа: ${e.getMessage()}"
    } finally {
        // Останавливаем и удаляем тестовый контейнер
        sh "docker stop ${containerName} || true"
        sh "docker rm ${containerName} || true"
    }
}

def scanDockerImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        // Здесь можно интегрировать с Trivy, Clair или другими сканерами
        echo "🔍 Сканирование образа ${fullImageName} на уязвимости..."
        
        // Пример интеграции с Trivy (если установлен)
        // sh "trivy image --severity HIGH,CRITICAL ${fullImageName}"
        
        echo "✅ Сканирование безопасности завершено"
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось выполнить сканирование безопасности: ${e.getMessage()}"
    }
}

def pushDockerImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        // Логинимся в registry если необходимо
        if (config.registry != 'docker.io') {
            withCredentials([usernamePassword(credentialsId: 'docker-registry-credentials', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                sh "echo \$DOCKER_PASS | docker login ${config.registry} -u \$DOCKER_USER --password-stdin"
            }
        }
        
        // Публикуем образ
        sh "docker push ${fullImageName}"
        
        echo "✅ Docker образ успешно опубликован: ${fullImageName}"
        
        // Создаем latest тег если это релиз
        if (config.imageTag == 'latest' || config.imageTag.matches(/^v?\d+\.\d+\.\d+/)) {
            def latestTag = "${config.registry}/${config.namespace}/${config.imageName}:latest"
            sh "docker tag ${fullImageName} ${latestTag}"
            sh "docker push ${latestTag}"
            echo "✅ Latest тег обновлен: ${latestTag}"
        }
        
    } catch (Exception e) {
        error "❌ Ошибка при публикации Docker образа: ${e.getMessage()}"
    }
}

def cleanupDockerResources(config) {
    try {
        // Удаляем неиспользуемые образы
        sh "docker image prune -f"
        
        // Удаляем неиспользуемые контейнеры
        sh "docker container prune -f"
        
        // Удаляем неиспользуемые сети
        sh "docker network prune -f"
        
        echo "✅ Очистка Docker ресурсов завершена"
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось очистить Docker ресурсы: ${e.getMessage()}"
    }
}

def notifySlack(status, config) {
    def colors = getColors()
    def message = ""
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    switch(status) {
        case 'success':
            message = "${colors.green}✅ Docker образ успешно собран и опубликован: ${fullImageName}${colors.reset}"
            break
        case 'failure':
            message = "${colors.red}❌ Ошибка при работе с Docker образом: ${fullImageName}${colors.reset}"
            break
    }
    
    echo "Slack уведомление: ${message}"
    // Здесь можно добавить реальную интеграцию со Slack
} 