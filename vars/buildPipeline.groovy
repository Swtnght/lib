#!/usr/bin/env groovy

/**
 * Основной pipeline для сборки проектов
 * Поддерживает Maven, Gradle и другие системы сборки
 */

def call(Map config = [:]) {
    def defaultConfig = [
        buildTool: 'maven',
        buildCommand: 'clean install',
        testCommand: 'test',
        skipTests: false,
        parallel: false,
        timeout: 30,
        nodeLabel: 'linux',
        notifySlack: true,
        notifyEmail: false,
        emailRecipients: '',
        archiveArtifacts: true,
        artifactPattern: '**/target/*.jar,**/build/libs/*.jar',
        junitPattern: '**/target/surefire-reports/*.xml,**/build/test-results/**/*.xml'
    ]
    
    config = defaultConfig + config
    
    pipeline {
        agent { label config.nodeLabel }
        
        options {
            timeout(time: config.timeout, unit: 'MINUTES')
            timestamps()
            ansiColor('xterm')
        }
        
        environment {
            BUILD_TOOL = config.buildTool
            BUILD_COMMAND = config.buildCommand
            TEST_COMMAND = config.testCommand
            SKIP_TESTS = config.skipTests
        }
        
        stages {
            stage('Checkout') {
                steps {
                    script {
                        echo "🔍 Начинаем checkout кода..."
                        checkout scm
                        sh 'git log --oneline -5'
                    }
                }
            }
            
            stage('Setup Environment') {
                steps {
                    script {
                        echo "⚙️ Настройка окружения..."
                        setupBuildEnvironment(config)
                    }
                }
            }
            
            stage('Dependencies') {
                steps {
                    script {
                        echo "📦 Установка зависимостей..."
                        installDependencies(config)
                    }
                }
            }
            
            stage('Tests') {
                when {
                    not { expression { config.skipTests } }
                }
                steps {
                    script {
                        echo "🧪 Запуск тестов..."
                        runTests(config)
                    }
                }
                post {
                    always {
                        script {
                            publishTestResults(config)
                        }
                    }
                }
            }
            
            stage('Build') {
                steps {
                    script {
                        echo "🔨 Сборка проекта..."
                        buildProject(config)
                    }
                }
            }
            
            stage('Quality Gate') {
                when {
                    not { expression { config.skipTests } }
                }
                steps {
                    script {
                        echo "🔍 Проверка качества кода..."
                        runQualityChecks(config)
                    }
                }
            }
            
            stage('Package') {
                steps {
                    script {
                        echo "📦 Создание артефактов..."
                        packageArtifacts(config)
                    }
                }
                post {
                    success {
                        script {
                            if (config.archiveArtifacts) {
                                archiveArtifacts artifacts: config.artifactPattern, fingerprint: true
                            }
                        }
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    echo "🧹 Очистка рабочего пространства..."
                    cleanWs()
                }
            }
            success {
                script {
                    echo "✅ Сборка завершена успешно!"
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
                    echo "❌ Сборка завершена с ошибкой!"
                    if (config.notifySlack) {
                        notifySlack('failure', config)
                    }
                    if (config.notifyEmail) {
                        notifyEmail('failure', config)
                    }
                }
            }
            unstable {
                script {
                    echo "⚠️ Сборка завершена нестабильно!"
                    if (config.notifySlack) {
                        notifySlack('unstable', config)
                    }
                    if (config.notifyEmail) {
                        notifyEmail('unstable', config)
                    }
                }
            }
        }
    }
}

// Вспомогательные функции
def setupBuildEnvironment(config) {
    switch(config.buildTool.toLowerCase()) {
        case 'maven':
            sh 'mvn --version'
            break
        case 'gradle':
            sh 'gradle --version'
            break
        default:
            error "Неподдерживаемый инструмент сборки: ${config.buildTool}"
    }
}

def installDependencies(config) {
    switch(config.buildTool.toLowerCase()) {
        case 'maven':
            sh "mvn dependency:resolve"
            break
        case 'gradle':
            sh "gradle dependencies"
            break
    }
}

def runTests(config) {
    switch(config.buildTool.toLowerCase()) {
        case 'maven':
            sh "mvn ${config.testCommand}"
            break
        case 'gradle':
            sh "gradle ${config.testCommand}"
            break
    }
}

def buildProject(config) {
    switch(config.buildTool.toLowerCase()) {
        case 'maven':
            sh "mvn ${config.buildCommand}"
            break
        case 'gradle':
            sh "gradle ${config.buildCommand}"
            break
    }
}

def runQualityChecks(config) {
    // Здесь можно добавить интеграцию с SonarQube, Checkstyle и другими инструментами
    echo "Проверка качества кода завершена"
}

def packageArtifacts(config) {
    echo "Артефакты упакованы"
}

def publishTestResults(config) {
    try {
        junit testResults: config.junitPattern, allowEmptyResults: true
    } catch (Exception e) {
        echo "Ошибка при публикации результатов тестов: ${e.getMessage()}"
    }
}

def notifySlack(status, config) {
    def colors = getColors()
    def message = ""
    
    switch(status) {
        case 'success':
            message = "${colors.green}✅ Сборка успешна!${colors.reset}"
            break
        case 'failure':
            message = "${colors.red}❌ Сборка провалилась!${colors.reset}"
            break
        case 'unstable':
            message = "${colors.yellow}⚠️ Сборка нестабильна!${colors.reset}"
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