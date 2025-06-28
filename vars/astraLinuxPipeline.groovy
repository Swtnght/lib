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
    
    // Проверка необходимых инструментов
    commonUtils.checkRequiredTools()
    
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
            
            stage('Validate AstraLinux Environment') {
                steps {
                    script {
                        astraUtils.validateAstraLinuxEnvironment(mergedConfig)
                    }
                }
            }
            
            stage('Generate Dockerfile') {
                steps {
                    script {
                        astraUtils.generateAstraLinuxDockerfile(mergedConfig)
                    }
                }
            }
            
            stage('Docker Build') {
                steps {
                    script {
                        echo "🐳 Сборка Docker образа AstraLinux: ${fullImageName}"
                        astraUtils.buildAstraLinuxImage(mergedConfig)
                    }
                }
            }
            
            stage('Security Scan') {
                when {
                    expression { mergedConfig.securityScan }
                }
                steps {
                    script {
                        astraUtils.scanAstraLinuxImage(mergedConfig)
                    }
                }
            }
            
            stage('AstraLinux Test') {
                when {
                    expression { mergedConfig.runTests }
                }
                steps {
                    script {
                        astraUtils.testAstraLinuxImage(mergedConfig)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    astraUtils.cleanupAstraLinuxResources(mergedConfig)
                }
            }
            success {
                script {
                    commonUtils.sendNotification("AstraLinux pipeline завершен успешно!", "success")
                    echo "📦 Образ сохранен локально: ${fullImageName}"
                }
            }
            failure {
                script {
                    commonUtils.sendNotification("AstraLinux pipeline завершен с ошибкой!", "error")
                }
            }
        }
    }
} 