package com.company.utils

class CommonUtils implements Serializable {
    def script
    
    CommonUtils(script) {
        this.script = script
    }
    
    def validateConfig(config, requiredFields = []) {
        try {
            script.echo "🔍 Валидация конфигурации..."
            
            requiredFields.each { field ->
                if (!config.containsKey(field) || config[field] == null) {
                    script.error "❌ Обязательное поле '${field}' отсутствует в конфигурации"
                }
            }
            
            script.echo "✅ Конфигурация валидна"
            
        } catch (Exception e) {
            script.error "❌ Ошибка валидации конфигурации: ${e.getMessage()}"
        }
    }
    
    def getDefaultConfig() {
        return [
            astraVersion: 'orel',
            baseImage: 'astra/astra:orel',
            imageName: 'astra-app',
            imageTag: 'latest',
            dockerfile: 'Dockerfile.astra',
            context: '.',
            qtVersion: '5.15',
            enableQt: false,
            enableSecurity: true,
            buildSystem: 'cmake',
            projectType: 'generic',
            parallelJobs: 4,
            multiStage: false,
            securityScan: true,
            runTests: true,
            buildArgs: [:]
        ]
    }
    
    def mergeConfig(userConfig) {
        def defaultConfig = getDefaultConfig()
        def mergedConfig = defaultConfig.clone()
        
        userConfig.each { key, value ->
            mergedConfig[key] = value
        }
        
        return mergedConfig
    }
    
    def generateImageName(config) {
        def baseName = config.imageName ?: 'astra-app'
        def tag = config.imageTag ?: 'latest'
        def version = config.astraVersion ?: 'orel'
        
        return "${baseName}:${tag}-${version}"
    }
    
    def logPipelineInfo(config) {
        script.echo """
🚀 Запуск AstraLinux Pipeline
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 Конфигурация:
   • Версия AstraLinux: ${config.astraVersion}
   • Базовый образ: ${config.baseImage}
   • Имя образа: ${config.imageName}
   • Тег: ${config.imageTag}
   • Qt поддержка: ${config.enableQt ? 'Да' : 'Нет'}
   • Безопасность: ${config.enableSecurity ? 'Да' : 'Нет'}
   • Система сборки: ${config.buildSystem}
   • Тип проекта: ${config.projectType}
   • Параллельные задачи: ${config.parallelJobs}
   • Многоэтапная сборка: ${config.multiStage ? 'Да' : 'Нет'}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
    }
    
    def checkDockerAvailability() {
        try {
            script.sh 'docker --version'
            script.echo "✅ Docker доступен"
            return true
        } catch (Exception e) {
            script.error "❌ Docker не доступен: ${e.getMessage()}"
            return false
        }
    }
    
    def checkRequiredTools() {
        try {
            script.echo "🔧 Проверка необходимых инструментов..."
            
            // Проверяем Docker
            checkDockerAvailability()
            
            // Проверяем Git
            script.sh 'git --version'
            
            // Проверяем Make
            script.sh 'make --version'
            
            script.echo "✅ Все необходимые инструменты доступны"
            
        } catch (Exception e) {
            script.error "❌ Ошибка проверки инструментов: ${e.getMessage()}"
        }
    }
    
    def createBuildDirectory() {
        try {
            script.sh 'mkdir -p build'
            script.echo "✅ Директория сборки создана"
        } catch (Exception e) {
            script.echo "⚠️ Предупреждение: ошибка создания директории сборки: ${e.getMessage()}"
        }
    }
    
    def archiveArtifacts(pattern = '**/*') {
        try {
            script.archiveArtifacts artifacts: pattern, fingerprint: true
            script.echo "✅ Артефакты заархивированы"
        } catch (Exception e) {
            script.echo "⚠️ Предупреждение: ошибка архивирования артефактов: ${e.getMessage()}"
        }
    }
    
    def sendNotification(message, type = 'info') {
        try {
            def emoji = type == 'success' ? '✅' : type == 'error' ? '❌' : type == 'warning' ? '⚠️' : 'ℹ️'
            script.echo "${emoji} ${message}"
            
            // Здесь можно добавить отправку уведомлений в Slack, Email и т.д.
            
        } catch (Exception e) {
            script.echo "⚠️ Ошибка отправки уведомления: ${e.getMessage()}"
        }
    }
} 