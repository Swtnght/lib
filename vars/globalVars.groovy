#!/usr/bin/env groovy

/**
 * Глобальные переменные для Jenkins Shared Library
 * Этот файл содержит общие переменные и константы, используемые в pipeline
 */

// Версия библиотеки
def call() {
    return [
        version: '1.0.0',
        name: 'jenkins-shared-library'
    ]
}

// Конфигурация по умолчанию
def getDefaultConfig() {
    return [
        // Настройки Maven
        maven: [
            version: '3.8.6',
            settings: 'settings.xml',
            localRepo: '.m2/repository'
        ],
        
        // Настройки Docker
        docker: [
            registry: 'docker.io',
            namespace: 'mycompany',
            tagSuffix: ''
        ],
        
        // Настройки SonarQube
        sonar: [
            host: 'http://sonarqube:9000',
            token: 'SONAR_TOKEN'
        ],
        
        // Настройки Slack
        slack: [
            channel: '#jenkins-notifications',
            webhook: 'SLACK_WEBHOOK_URL'
        ],
        
        // Настройки Jira
        jira: [
            url: 'https://your-company.atlassian.net',
            project: 'PROJ'
        ],
        
        // Настройки Nexus
        nexus: [
            url: 'http://nexus:8081',
            repository: 'releases'
        ]
    ]
}

// Цвета для вывода в консоль
def getColors() {
    return [
        red: '\033[0;31m',
        green: '\033[0;32m',
        yellow: '\033[1;33m',
        blue: '\033[0;34m',
        purple: '\033[0;35m',
        cyan: '\033[0;36m',
        white: '\033[1;37m',
        reset: '\033[0m'
    ]
}

// Статусы сборки
def getBuildStatuses() {
    return [
        SUCCESS: 'SUCCESS',
        UNSTABLE: 'UNSTABLE',
        FAILURE: 'FAILURE',
        ABORTED: 'ABORTED'
    ]
} 