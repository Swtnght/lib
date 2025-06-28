package com.company.utils

class AstraLinuxUtils implements Serializable {
    def script
    
    AstraLinuxUtils(script) {
        this.script = script
    }
    
    def prepareQOwnNotesProject(config) {
        try {
            script.echo "🔧 Подготовка QOwnNotes проекта..."
            
            // Проверяем наличие .pro файла
            if (script.fileExists('src/QOwnNotes.pro')) {
                script.echo "✅ QOwnNotes.pro найден в src/"
            } else if (script.fileExists('QOwnNotes.pro')) {
                script.echo "✅ QOwnNotes.pro найден в корне"
            } else {
                script.echo "⚠️ QOwnNotes.pro не найден, возможно это не QOwnNotes проект"
            }
            
            // Проверяем наличие git submodules
            if (script.fileExists('.gitmodules')) {
                script.echo "📦 Обновление git submodules..."
                script.sh 'git submodule update --init --recursive'
            }
            
            script.echo "✅ QOwnNotes проект подготовлен"
            
        } catch (Exception e) {
            script.echo "⚠️ Предупреждение: ошибка подготовки QOwnNotes проекта: ${e.getMessage()}"
        }
    }
    
    def validateAstraLinuxEnvironment(config) {
        try {
            // Проверяем доступность базового образа AstraLinux
            script.sh "docker pull ${config.baseImage}"
            
            // Проверяем версию AstraLinux
            script.sh """
                docker run --rm ${config.baseImage} cat /etc/os-release | grep -i astra
            """
            
            script.echo "✅ Окружение AstraLinux ${config.astraVersion} валидно"
            
        } catch (Exception e) {
            script.error "❌ Ошибка валидации окружения AstraLinux: ${e.getMessage()}"
        }
    }
    
    def generateAstraLinuxDockerfile(config) {
        try {
            def dockerfileContent = generateDockerfileContent(config)
            script.writeFile file: config.dockerfile, text: dockerfileContent
            
            script.echo "✅ Dockerfile для AstraLinux ${config.astraVersion} сгенерирован"
            
        } catch (Exception e) {
            script.error "❌ Ошибка генерации Dockerfile: ${e.getMessage()}"
        }
    }
    
    def generateDockerfileContent(config) {
        def content = ""
        
        if (config.multiStage) {
            content += """
# Многоэтапная сборка для AstraLinux ${config.astraVersion}
FROM ${config.baseImage} as builder

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \\
    build-essential \\
    cmake \\
    git \\
    pkg-config \\
    wget \\
    curl \\
    && rm -rf /var/lib/apt/lists/*

"""
            
            if (config.enableQt) {
                content += """
# Установка Qt ${config.qtVersion}
RUN apt-get update && apt-get install -y \\
    qt5-default \\
    qtbase5-dev \\
    qtchooser \\
    qt5-qmake \\
    qtbase5-dev-tools \\
    libqt5svg5-dev \\
    libqt5webkit5-dev \\
    qtwebsockets5-dev \\
    && rm -rf /var/lib/apt/lists/*
"""
            }
            
            content += """
# Копирование исходного кода
COPY . /app
WORKDIR /app

# Сборка приложения
RUN cmake . && make -j${config.parallelJobs}

# Финальный образ
FROM ${config.baseImage}
"""
        } else {
            content += """
# Одноэтапная сборка для AstraLinux ${config.astraVersion}
FROM ${config.baseImage}

# Установка системных зависимостей
RUN apt-get update && apt-get install -y \\
    build-essential \\
    cmake \\
    git \\
    pkg-config \\
    wget \\
    curl \\
    && rm -rf /var/lib/apt/lists/*
"""
            
            if (config.enableQt) {
                content += """
# Установка Qt ${config.qtVersion}
RUN apt-get update && apt-get install -y \\
    qt5-default \\
    qtbase5-dev \\
    qtchooser \\
    qt5-qmake \\
    qtbase5-dev-tools \\
    libqt5svg5-dev \\
    libqt5webkit5-dev \\
    qtwebsockets5-dev \\
    && rm -rf /var/lib/apt/lists/*
"""
            }
            
            content += """
# Копирование исходного кода
COPY . /app
WORKDIR /app

# Сборка приложения
RUN cmake . && make -j${config.parallelJobs}
"""
        }
        
        if (config.enableSecurity) {
            content += """
# Настройки безопасности
RUN apt-get update && apt-get install -y \\
    apparmor \\
    apparmor-utils \\
    && rm -rf /var/lib/apt/lists/*

# Создание непривилегированного пользователя
RUN useradd -m -s /bin/bash appuser
USER appuser
"""
        }
        
        content += """
# Экспорт портов
EXPOSE 8080

# Команда по умолчанию
CMD ["./app"]
"""
        
        return content
    }
    
    def buildAstraLinuxImage(config) {
        try {
            def buildArgs = ""
            if (config.buildArgs) {
                buildArgs = config.buildArgs.collect { k, v -> "--build-arg ${k}=${v}" }.join(" ")
            }
            
            script.sh """
                docker build ${buildArgs} \\
                    -f ${config.dockerfile} \\
                    -t ${config.imageName}:${config.imageTag} \\
                    ${config.context}
            """
            
            script.echo "✅ Docker образ AstraLinux ${config.astraVersion} собран успешно"
            
        } catch (Exception e) {
            script.error "❌ Ошибка сборки Docker образа: ${e.getMessage()}"
        }
    }
    
    def scanAstraLinuxImage(config) {
        try {
            script.echo "🔒 Сканирование образа на уязвимости..."
            
            // Проверяем наличие Trivy
            def trivyAvailable = script.sh(
                script: "which trivy || echo 'not_found'",
                returnStdout: true
            ).trim()
            
            if (trivyAvailable != 'not_found') {
                script.sh """
                    trivy image --severity HIGH,CRITICAL ${config.imageName}:${config.imageTag}
                """
            } else {
                script.echo "⚠️ Trivy не найден, пропускаем сканирование безопасности"
            }
            
        } catch (Exception e) {
            script.echo "⚠️ Предупреждение: ошибка сканирования безопасности: ${e.getMessage()}"
        }
    }
    
    def testAstraLinuxImage(config) {
        try {
            script.echo "🧪 Запуск тестов для образа AstraLinux..."
            
            // Базовые тесты
            script.sh """
                docker run --rm ${config.imageName}:${config.imageTag} echo "Тест 1: Проверка запуска контейнера"
                docker run --rm ${config.imageName}:${config.imageTag} which cmake
                docker run --rm ${config.imageName}:${config.imageTag} which gcc
            """
            
            if (config.enableQt) {
                script.sh """
                    docker run --rm ${config.imageName}:${config.imageTag} which qmake
                    docker run --rm ${config.imageName}:${config.imageTag} which qt5-qmake
                """
            }
            
            script.echo "✅ Тесты AstraLinux ${config.astraVersion} пройдены успешно"
            
        } catch (Exception e) {
            script.error "❌ Ошибка тестирования образа: ${e.getMessage()}"
        }
    }
    
    def cleanupAstraLinuxResources(config) {
        try {
            script.echo "🧹 Очистка временных ресурсов..."
            
            // Удаление временных образов
            script.sh """
                docker image prune -f || true
                docker container prune -f || true
            """
            
        } catch (Exception e) {
            script.echo "⚠️ Предупреждение: ошибка очистки ресурсов: ${e.getMessage()}"
        }
    }
} 