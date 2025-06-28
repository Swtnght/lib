#!/usr/bin/env groovy

import com.company.utils.AstraLinuxUtils
import com.company.utils.CommonUtils

def call(Map config = [:]) {
    // Инициализация утилит
    def commonUtils = new CommonUtils(this)
    def astraUtils = new AstraLinuxUtils(this)
    
    // Объединение конфигурации с дефолтными значениями
    def mergedConfig = commonUtils.mergeConfig(config)
    
    // Валидация обязательных полей
    def requiredFields = ['astraVersion', 'baseImage']
    commonUtils.validateConfig(mergedConfig, requiredFields)
    
    // Генерация полного имени образа
    def fullImageName = commonUtils.generateImageName(mergedConfig)
    mergedConfig.imageName = fullImageName
    
    // Логирование информации о pipeline
    commonUtils.logPipelineInfo(mergedConfig)
    
    pipeline {
        agent any
        
        environment {
            ASTRA_VERSION = "${mergedConfig.astraVersion}"
            BASE_IMAGE = "${mergedConfig.baseImage}"
            DOCKER_IMAGE = "${fullImageName}"
            DOCKERFILE = "${mergedConfig.dockerfile}"
            DOCKER_CONTEXT = "${mergedConfig.context}"
            QT_VERSION = "${mergedConfig.qtVersion}"
            ENABLE_QT = "${mergedConfig.enableQt}"
            ENABLE_SECURITY = "${mergedConfig.enableSecurity}"
            BUILD_SYSTEM = "${mergedConfig.buildSystem}"
            PROJECT_TYPE = "${mergedConfig.projectType}"
            PARALLEL_JOBS = "${mergedConfig.parallelJobs}"
        }
        
        stages {
            stage('Checkout') {
                steps {
                    script {
                        echo "🔍 Checkout кода для AstraLinux ${mergedConfig.astraVersion}..."
                        checkout scm
                        sh 'git log --oneline -5'
                    }
                }
                post {
                    success {
                        script {
                            if (mergedConfig.projectType == 'qownnotes') {
                                astraUtils.prepareQOwnNotesProject(mergedConfig)
                            }
                        }
                    }
                }
            }
            
            stage('Validate Environment') {
                steps {
                    script {
                        echo "🔍 Проверка окружения..."
                        commonUtils.checkRequiredTools()
                    }
                }
            }
            
            stage('Generate Dockerfile') {
                steps {
                    script {
                        echo "📝 Генерация Dockerfile..."
                        astraUtils.generateAstraLinuxDockerfile(mergedConfig)
                    }
                }
            }
            
            stage('Success') {
                steps {
                    script {
                        echo "✅ Упрощенный AstraLinux pipeline завершен успешно!"
                        echo "📦 Образ будет: ${fullImageName}"
                    }
                }
            }
        }
        
        post {
            success {
                script {
                    commonUtils.sendNotification("Упрощенный AstraLinux pipeline завершен успешно!", "success")
                }
            }
            failure {
                script {
                    commonUtils.sendNotification("Упрощенный AstraLinux pipeline завершен с ошибкой!", "error")
                }
            }
        }
    }
} 