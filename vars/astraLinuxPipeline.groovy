#!/usr/bin/env groovy

/**
 * Pipeline для сборки Docker образов с AstraLinux Orel
 * Специализирован для российских проектов и требований безопасности
 */

def call(Map config = [:]) {
    def defaultConfig = [
        astraVersion: 'orel', // orel, smolensk
        baseImage: 'astra/orel:latest',
        dockerfile: 'Dockerfile.astra',
        context: '.',
        imageName: '',
        imageTag: '',
        registry: 'registry.astralinux.ru',
        namespace: 'mycompany',
        pushImage: false, // Отключено по умолчанию
        scanImage: true,
        runTests: true,
        testPort: 8080,
        healthCheck: true,
        healthCheckUrl: 'http://localhost:8080/health',
        enableQt: false,
        qtVersion: '5.15.2',
        enableSecurity: true,
        securityScan: true,
        timeout: 45,
        nodeLabel: 'astra-linux',
        notifySlack: false, // Отключено по умолчанию
        buildArgs: [:],
        multiStage: true,
        buildSystem: 'cmake', // cmake, qmake
        projectType: 'qt-app', // qt-app, qownnotes, custom
        enableTranslations: false,
        parallelJobs: 4
    ]
    
    config = defaultConfig + config
    
    // Генерируем имя образа если не указано
    if (!config.imageName) {
        config.imageName = env.JOB_NAME.toLowerCase().replaceAll(/[^a-z0-9]/, '-')
    }
    
    // Генерируем тег если не указан
    if (!config.imageTag) {
        config.imageTag = "${config.astraVersion}-${env.BUILD_NUMBER}"
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
            ASTRA_VERSION = config.astraVersion
            BASE_IMAGE = config.baseImage
            DOCKER_IMAGE = fullImageName
            DOCKERFILE = config.dockerfile
            DOCKER_CONTEXT = config.context
            QT_VERSION = config.qtVersion
            ENABLE_QT = config.enableQt
            ENABLE_SECURITY = config.enableSecurity
            BUILD_SYSTEM = config.buildSystem
            PROJECT_TYPE = config.projectType
            PARALLEL_JOBS = config.parallelJobs
        }
        
        stages {
            stage('Checkout') {
                steps {
                    script {
                        echo "🔍 Checkout кода для AstraLinux ${config.astraVersion}..."
                        checkout scm
                        sh 'git log --oneline -5'
                    }
                }
                post {
                    success {
                        script {
                            if (config.projectType == 'qownnotes') {
                                echo "📝 Подготовка QOwnNotes проекта..."
                                prepareQOwnNotesProject(config)
                            }
                        }
                    }
                }
            }
            
            stage('Validate AstraLinux Environment') {
                steps {
                    script {
                        echo "🔍 Проверка окружения AstraLinux..."
                        validateAstraLinuxEnvironment(config)
                    }
                }
            }
            
            stage('Generate Dockerfile') {
                steps {
                    script {
                        echo "📝 Генерация Dockerfile для AstraLinux..."
                        generateAstraLinuxDockerfile(config)
                    }
                }
            }
            
            stage('Docker Build') {
                steps {
                    script {
                        echo "🐳 Сборка Docker образа AstraLinux: ${fullImageName}"
                        buildAstraLinuxImage(config)
                    }
                }
            }
            
            stage('Security Scan') {
                when {
                    expression { config.securityScan }
                }
                steps {
                    script {
                        echo "🔒 Сканирование безопасности образа AstraLinux..."
                        scanAstraLinuxImage(config)
                    }
                }
            }
            
            stage('AstraLinux Test') {
                when {
                    expression { config.runTests }
                }
                steps {
                    script {
                        echo "🧪 Тестирование образа AstraLinux..."
                        testAstraLinuxImage(config)
                    }
                }
            }
        }
        
        post {
            always {
                script {
                    echo "🧹 Очистка AstraLinux ресурсов..."
                    cleanupAstraLinuxResources(config)
                }
            }
            success {
                script {
                    echo "✅ AstraLinux pipeline завершен успешно!"
                    echo "📦 Образ сохранен локально: ${fullImageName}"
                }
            }
            failure {
                script {
                    echo "❌ AstraLinux pipeline завершен с ошибкой!"
                }
            }
        }
    }
}

// Вспомогательные функции
def prepareQOwnNotesProject(config) {
    try {
        echo "🔧 Подготовка QOwnNotes проекта..."
        
        // Проверяем наличие .pro файла
        if (fileExists('src/QOwnNotes.pro')) {
            echo "✅ QOwnNotes.pro найден в src/"
        } else if (fileExists('QOwnNotes.pro')) {
            echo "✅ QOwnNotes.pro найден в корне"
        } else {
            echo "⚠️ QOwnNotes.pro не найден, возможно это не QOwnNotes проект"
        }
        
        // Проверяем наличие git submodules
        if (fileExists('.gitmodules')) {
            echo "📦 Обновление git submodules..."
            sh 'git submodule update --init --recursive'
        }
        
        echo "✅ QOwnNotes проект подготовлен"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка подготовки QOwnNotes проекта: ${e.getMessage()}"
    }
}

def validateAstraLinuxEnvironment(config) {
    try {
        // Проверяем доступность базового образа AstraLinux
        sh "docker pull ${config.baseImage}"
        
        // Проверяем версию AstraLinux
        sh """
            docker run --rm ${config.baseImage} cat /etc/os-release | grep -i astra
        """
        
        echo "✅ Окружение AstraLinux ${config.astraVersion} валидно"
        
    } catch (Exception e) {
        error "❌ Ошибка валидации окружения AstraLinux: ${e.getMessage()}"
    }
}

def generateAstraLinuxDockerfile(config) {
    try {
        def dockerfileContent = generateDockerfileContent(config)
        writeFile file: config.dockerfile, text: dockerfileContent
        
        echo "✅ Dockerfile для AstraLinux ${config.astraVersion} сгенерирован"
        
    } catch (Exception e) {
        error "❌ Ошибка генерации Dockerfile: ${e.getMessage()}"
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
WORKDIR /app
COPY . .

# Подготовка проекта
"""
        
        if (config.projectType == 'qownnotes') {
            content += """
# Подготовка QOwnNotes
RUN git submodule update --init --recursive || echo "No submodules found"
"""
        }
        
        content += """
# Сборка приложения
"""
        
        if (config.buildSystem == 'qmake') {
            content += """
RUN cd src && \\
    lrelease QOwnNotes.pro && \\
    qmake && \\
    make -j${config.parallelJobs}
"""
        } else {
            content += """
RUN mkdir -p build && cd build && \\
    cmake .. && \\
    make -j${config.parallelJobs}
"""
        }
        
        content += """
# Финальный образ
FROM ${config.baseImage} as runtime

# Установка runtime зависимостей
RUN apt-get update && apt-get install -y \\
    ca-certificates \\
    && rm -rf /var/lib/apt/lists/*

"""
        
        if (config.enableQt) {
            content += """
# Установка Qt runtime
RUN apt-get update && apt-get install -y \\
    libqt5core5a \\
    libqt5gui5 \\
    libqt5widgets5 \\
    libqt5network5 \\
    libqt5websockets5 \\
    && rm -rf /var/lib/apt/lists/*

"""
        }
        
        content += """
# Копирование собранного приложения
"""
        
        if (config.buildSystem == 'qmake') {
            content += """
COPY --from=builder /app/src/QOwnNotes /usr/local/bin/
COPY --from=builder /app/src/translations /usr/local/share/QOwnNotes/translations/
"""
        } else {
            content += """
COPY --from=builder /app/build/app /usr/local/bin/
COPY --from=builder /app/build/lib*.so* /usr/local/lib/
"""
        }
        
        content += """
# Настройка пользователя
RUN useradd -m -s /bin/bash appuser
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
    CMD curl -f ${config.healthCheckUrl} || exit 1

EXPOSE ${config.testPort}
"""
        
        if (config.projectType == 'qownnotes') {
            content += """CMD ["/usr/local/bin/QOwnNotes"]"""
        } else {
            content += """CMD ["/usr/local/bin/app"]"""
        }
        
    } else {
        content += """
# Одноэтапная сборка для AstraLinux ${config.astraVersion}
FROM ${config.baseImage}

# Установка зависимостей
RUN apt-get update && apt-get install -y \\
    build-essential \\
    cmake \\
    git \\
    pkg-config \\
    wget \\
    curl \\
    ca-certificates \\
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
    libqt5core5a \\
    libqt5gui5 \\
    libqt5widgets5 \\
    libqt5network5 \\
    libqt5websockets5 \\
    && rm -rf /var/lib/apt/lists/*

"""
        }
        
        content += """
# Рабочая директория
WORKDIR /app

# Копирование исходного кода
COPY . .

# Подготовка проекта
"""
        
        if (config.projectType == 'qownnotes') {
            content += """
# Подготовка QOwnNotes
RUN git submodule update --init --recursive || echo "No submodules found"
"""
        }
        
        content += """
# Сборка приложения
"""
        
        if (config.buildSystem == 'qmake') {
            content += """
RUN cd src && \\
    lrelease QOwnNotes.pro && \\
    qmake && \\
    make -j${config.parallelJobs} && \\
    make install
"""
        } else {
            content += """
RUN mkdir -p build && cd build && \\
    cmake .. && \\
    make -j${config.parallelJobs} && \\
    make install
"""
        }
        
        content += """
# Настройка пользователя
RUN useradd -m -s /bin/bash appuser
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \\
    CMD curl -f ${config.healthCheckUrl} || exit 1

EXPOSE ${config.testPort}
"""
        
        if (config.projectType == 'qownnotes') {
            content += """CMD ["/usr/local/bin/QOwnNotes"]"""
        } else {
            content += """CMD ["/usr/local/bin/app"]"""
        }
    }
    
    return content
}

def buildAstraLinuxImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        // Подготавливаем аргументы сборки
        def buildArgs = []
        config.buildArgs.each { key, value ->
            buildArgs.add("--build-arg ${key}=${value}")
        }
        
        // Добавляем стандартные аргументы для AstraLinux
        buildArgs.add("--build-arg ASTRA_VERSION=${config.astraVersion}")
        buildArgs.add("--build-arg QT_VERSION=${config.qtVersion}")
        buildArgs.add("--build-arg ENABLE_QT=${config.enableQt}")
        buildArgs.add("--build-arg BUILD_SYSTEM=${config.buildSystem}")
        buildArgs.add("--build-arg PROJECT_TYPE=${config.projectType}")
        buildArgs.add("--build-arg PARALLEL_JOBS=${config.parallelJobs}")
        
        def buildCommand = """
            docker build \\
                -f ${config.dockerfile} \\
                -t ${fullImageName} \\
                ${buildArgs.join(' ')} \\
                ${config.context}
        """
        
        sh buildCommand
        
        echo "✅ Docker образ AstraLinux успешно собран: ${fullImageName}"
        echo "📦 Образ сохранен локально и готов к использованию"
        
    } catch (Exception e) {
        error "❌ Ошибка при сборке Docker образа AstraLinux: ${e.getMessage()}"
    }
}

def scanAstraLinuxImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    
    try {
        echo "🔍 Сканирование образа AstraLinux ${fullImageName} на уязвимости..."
        
        // Проверка на известные уязвимости в пакетах
        sh """
            docker run --rm ${fullImageName} \\
            bash -c 'apt-get update && apt-get install -y apt-show-versions && apt-show-versions | grep -i security'
        """
        
        // Проверка на подозрительные файлы
        sh """
            docker run --rm ${fullImageName} \\
            find / -type f -executable -exec file {} \\; | grep -i "executable"
        """
        
        // Проверка на открытые порты
        sh """
            docker run --rm ${fullImageName} \\
            netstat -tuln || echo "netstat not available"
        """
        
        echo "✅ Сканирование безопасности AstraLinux завершено"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось выполнить полное сканирование безопасности: ${e.getMessage()}"
    }
}

def testAstraLinuxImage(config) {
    def fullImageName = "${config.registry}/${config.namespace}/${config.imageName}:${config.imageTag}"
    def containerName = "test-astra-${config.imageName}-${config.imageTag}"
    
    try {
        // Запускаем контейнер
        sh "docker run -d --name ${containerName} -p ${config.testPort}:${config.testPort} ${fullImageName}"
        
        // Ждем запуска контейнера
        sleep(15)
        
        // Проверяем версию AstraLinux в контейнере
        sh """
            docker exec ${containerName} cat /etc/os-release | grep -i astra
        """
        
        // Проверяем наличие собранного приложения
        if (config.projectType == 'qownnotes') {
            sh """
                docker exec ${containerName} ls -la /usr/local/bin/QOwnNotes
            """
        } else {
            sh """
                docker exec ${containerName} ls -la /usr/local/bin/app
            """
        }
        
        // Проверяем здоровье контейнера
        if (config.healthCheck) {
            def healthStatus = sh(
                script: "curl -f ${config.healthCheckUrl} || echo 'HEALTH_CHECK_FAILED'",
                returnStdout: true
            ).trim()
            
            if (healthStatus == 'HEALTH_CHECK_FAILED') {
                error "❌ Проверка здоровья контейнера AstraLinux не прошла"
            }
        }
        
        // Проверяем работу приложения
        sh """
            docker exec ${containerName} ps aux
        """
        
        echo "✅ Тестирование образа AstraLinux завершено успешно"
        
    } catch (Exception e) {
        error "❌ Ошибка при тестировании образа AstraLinux: ${e.getMessage()}"
    } finally {
        // Останавливаем и удаляем тестовый контейнер
        sh "docker stop ${containerName} || true"
        sh "docker rm ${containerName} || true"
    }
}

def cleanupAstraLinuxResources(config) {
    try {
        // Удаляем неиспользуемые образы AstraLinux (кроме нашего)
        sh "docker image prune -f"
        
        // Удаляем неиспользуемые контейнеры
        sh "docker container prune -f"
        
        // Удаляем неиспользуемые сети
        sh "docker network prune -f"
        
        // Очищаем временные файлы
        sh "rm -f ${config.dockerfile} || true"
        
        echo "✅ Очистка ресурсов AstraLinux завершена"
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось очистить ресурсы AstraLinux: ${e.getMessage()}"
    }
} 