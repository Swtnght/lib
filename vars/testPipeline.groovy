#!/usr/bin/env groovy

import com.company.utils.CommonUtils

def call() {
    echo "🧪 Тестирование импорта утилитарных классов..."
    
    try {
        def commonUtils = new CommonUtils(this)
        echo "✅ CommonUtils успешно импортирован"
        
        def defaultConfig = commonUtils.getDefaultConfig()
        echo "✅ getDefaultConfig() работает: ${defaultConfig}"
        
    } catch (Exception e) {
        error "❌ Ошибка импорта: ${e.getMessage()}"
    }
    
    pipeline {
        agent any
        
        stages {
            stage('Test') {
                steps {
                    echo "✅ Тест завершен успешно"
                }
            }
        }
    }
} 