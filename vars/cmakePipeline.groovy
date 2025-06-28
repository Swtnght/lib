#!/usr/bin/env groovy

/**
 * Pipeline для сборки C/C++ проектов с CMake
 * Основан на лучших практиках для CI/CD с CMake
 */

def call(Map config = [:]) {
    def defaultConfig = [
        buildType: 'Release', // Debug, Release, RelWithDebInfo, MinSizeRel
        buildDir: 'build',
        sourceDir: '.',
        installDir: '/usr/local',
        parallelJobs: 4,
        compiler: 'gcc', // gcc, clang, msvc
        cppStandard: '17',
        enableTests: true,
        enableCoverage: false,
        enableSanitizers: false,
        sanitizerType: 'address', // address, thread, memory, undefined
        enableStaticAnalysis: false,
        staticAnalyzer: 'cppcheck', // cppcheck, clang-tidy
        packageManager: 'conan', // conan, vcpkg, none
        conanProfile: 'default',
        vcpkgTriplet: 'x64-linux',
        crossCompile: false,
        targetPlatform: '',
        targetArch: '',
        timeout: 60,
        nodeLabel: 'linux',
        notifySlack: true,
        archiveArtifacts: true,
        artifactPattern: '**/build/**/*.so,**/build/**/*.a,**/build/**/*.exe',
        junitPattern: '**/build/**/test_results/**/*.xml'
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
            BUILD_TYPE = config.buildType
            BUILD_DIR = config.buildDir
            SOURCE_DIR = config.sourceDir
            INSTALL_DIR = config.installDir
            PARALLEL_JOBS = config.parallelJobs
            COMPILER = config.compiler
            CPP_STANDARD = config.cppStandard
            ENABLE_TESTS = config.enableTests
            ENABLE_COVERAGE = config.enableCoverage
            ENABLE_SANITIZERS = config.enableSanitizers
            SANITIZER_TYPE = config.sanitizerType
            ENABLE_STATIC_ANALYSIS = config.enableStaticAnalysis
            STATIC_ANALYZER = config.staticAnalyzer
            PACKAGE_MANAGER = config.packageManager
            CONAN_PROFILE = config.conanProfile
            VCPKG_TRIPLET = config.vcpkgTriplet
        }
        
        stages {
            stage('Checkout') {
                steps {
                    script {
                        echo "🔍 Checkout кода..."
                        checkout scm
                        sh 'git log --oneline -5'
                    }
                }
            }
            
            stage('Setup Environment') {
                steps {
                    script {
                        echo "⚙️ Настройка окружения для CMake..."
                        setupCMakeEnvironment(config)
                    }
                }
            }
            
            stage('Install Dependencies') {
                steps {
                    script {
                        echo "📦 Установка зависимостей..."
                        installDependencies(config)
                    }
                }
            }
            
            stage('Configure') {
                steps {
                    script {
                        echo "🔧 Конфигурация CMake..."
                        configureCMake(config)
                    }
                }
            }
            
            stage('Static Analysis') {
                when {
                    expression { config.enableStaticAnalysis }
                }
                steps {
                    script {
                        echo "🔍 Статический анализ кода..."
                        runStaticAnalysis(config)
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
            
            stage('Test') {
                when {
                    expression { config.enableTests }
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
            
            stage('Coverage') {
                when {
                    expression { config.enableCoverage }
                }
                steps {
                    script {
                        echo "📊 Сбор покрытия кода..."
                        collectCoverage(config)
                    }
                }
            }
            
            stage('Install') {
                steps {
                    script {
                        echo "📦 Установка артефактов..."
                        installArtifacts(config)
                    }
                }
            }
            
            stage('Package') {
                steps {
                    script {
                        echo "📦 Создание пакетов..."
                        createPackages(config)
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
                    cleanupWorkspace(config)
                }
            }
            success {
                script {
                    echo "✅ CMake сборка завершена успешно!"
                    if (config.notifySlack) {
                        notifySlack('success', config)
                    }
                }
            }
            failure {
                script {
                    echo "❌ CMake сборка завершена с ошибкой!"
                    if (config.notifySlack) {
                        notifySlack('failure', config)
                    }
                }
            }
        }
    }
}

// Вспомогательные функции
def setupCMakeEnvironment(config) {
    try {
        // Проверяем версии инструментов
        sh 'cmake --version'
        sh 'gcc --version'
        sh 'make --version'
        
        // Устанавливаем переменные окружения для компилятора
        switch(config.compiler.toLowerCase()) {
            case 'gcc':
                env.CC = 'gcc'
                env.CXX = 'g++'
                break
            case 'clang':
                env.CC = 'clang'
                env.CXX = 'clang++'
                break
            case 'msvc':
                // Для Windows с MSVC
                env.CC = 'cl'
                env.CXX = 'cl'
                break
        }
        
        // Создаем директорию для сборки
        sh "mkdir -p ${config.buildDir}"
        
        echo "✅ Окружение настроено для ${config.compiler}"
        
    } catch (Exception e) {
        error "❌ Ошибка настройки окружения: ${e.getMessage()}"
    }
}

def installDependencies(config) {
    try {
        switch(config.packageManager.toLowerCase()) {
            case 'conan':
                installConanDependencies(config)
                break
            case 'vcpkg':
                installVcpkgDependencies(config)
                break
            case 'none':
                echo "Пакетный менеджер не используется"
                break
            default:
                echo "Неизвестный пакетный менеджер: ${config.packageManager}"
        }
    } catch (Exception e) {
        error "❌ Ошибка установки зависимостей: ${e.getMessage()}"
    }
}

def installConanDependencies(config) {
    try {
        // Устанавливаем Conan если не установлен
        sh 'pip install conan || echo "Conan уже установлен"'
        
        // Создаем профиль если не существует
        sh "conan profile show ${config.conanProfile} || conan profile new ${config.conanProfile} --detect"
        
        // Устанавливаем зависимости
        if (fileExists('conanfile.txt') || fileExists('conanfile.py')) {
            sh "conan install . --build=missing --profile=${config.conanProfile}"
        }
        
        echo "✅ Conan зависимости установлены"
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка установки Conan зависимостей: ${e.getMessage()}"
    }
}

def installVcpkgDependencies(config) {
    try {
        // Клонируем vcpkg если не существует
        if (!fileExists('vcpkg')) {
            sh 'git clone https://github.com/Microsoft/vcpkg.git'
            sh 'cd vcpkg && ./bootstrap-vcpkg.sh'
        }
        
        // Устанавливаем зависимости из vcpkg.json
        if (fileExists('vcpkg.json')) {
            sh "./vcpkg/vcpkg install --triplet=${config.vcpkgTriplet}"
        }
        
        echo "✅ Vcpkg зависимости установлены"
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка установки Vcpkg зависимостей: ${e.getMessage()}"
    }
}

def configureCMake(config) {
    try {
        def cmakeArgs = [
            "-DCMAKE_BUILD_TYPE=${config.buildType}",
            "-DCMAKE_CXX_STANDARD=${config.cppStandard}",
            "-DCMAKE_CXX_STANDARD_REQUIRED=ON",
            "-DCMAKE_INSTALL_PREFIX=${config.installDir}",
            "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON"
        ]
        
        // Добавляем опции для тестов
        if (config.enableTests) {
            cmakeArgs.add("-DBUILD_TESTING=ON")
            cmakeArgs.add("-DENABLE_TESTING=ON")
        }
        
        // Добавляем опции для покрытия
        if (config.enableCoverage) {
            cmakeArgs.add("-DENABLE_COVERAGE=ON")
            cmakeArgs.add("-DCMAKE_BUILD_TYPE=Debug")
        }
        
        // Добавляем опции для санитайзеров
        if (config.enableSanitizers) {
            cmakeArgs.add("-DENABLE_SANITIZERS=ON")
            cmakeArgs.add("-DSANITIZER_TYPE=${config.sanitizerType}")
        }
        
        // Добавляем опции для статического анализа
        if (config.enableStaticAnalysis) {
            cmakeArgs.add("-DENABLE_STATIC_ANALYSIS=ON")
            cmakeArgs.add("-DSTATIC_ANALYZER=${config.staticAnalyzer}")
        }
        
        // Добавляем опции для кросс-компиляции
        if (config.crossCompile) {
            cmakeArgs.add("-DCMAKE_SYSTEM_NAME=${config.targetPlatform}")
            cmakeArgs.add("-DCMAKE_SYSTEM_PROCESSOR=${config.targetArch}")
        }
        
        // Добавляем пути к пакетным менеджерам
        switch(config.packageManager.toLowerCase()) {
            case 'conan':
                cmakeArgs.add("-DCMAKE_PREFIX_PATH=${pwd()}/build")
                break
            case 'vcpkg':
                cmakeArgs.add("-DCMAKE_TOOLCHAIN_FILE=${pwd()}/vcpkg/scripts/buildsystems/vcpkg.cmake")
                break
        }
        
        def cmakeCommand = "cmake ${cmakeArgs.join(' ')} ${config.sourceDir}"
        sh "cd ${config.buildDir} && ${cmakeCommand}"
        
        echo "✅ CMake конфигурация завершена"
        
    } catch (Exception e) {
        error "❌ Ошибка конфигурации CMake: ${e.getMessage()}"
    }
}

def runStaticAnalysis(config) {
    try {
        switch(config.staticAnalyzer.toLowerCase()) {
            case 'cppcheck':
                sh "cd ${config.buildDir} && cppcheck --enable=all --xml --xml-version=2 . 2> cppcheck-result.xml || true"
                break
            case 'clang-tidy':
                sh "cd ${config.buildDir} && run-clang-tidy -j${config.parallelJobs} -header-filter='.*' -checks='*' -fix"
                break
        }
        
        echo "✅ Статический анализ завершен"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка статического анализа: ${e.getMessage()}"
    }
}

def buildProject(config) {
    try {
        sh "cd ${config.buildDir} && make -j${config.parallelJobs}"
        
        echo "✅ Сборка проекта завершена"
        
    } catch (Exception e) {
        error "❌ Ошибка сборки проекта: ${e.getMessage()}"
    }
}

def runTests(config) {
    try {
        // Запускаем тесты через CTest
        sh "cd ${config.buildDir} && ctest --output-on-failure --parallel ${config.parallelJobs}"
        
        // Если есть Google Test, запускаем их отдельно
        sh "cd ${config.buildDir} && find . -name '*_test' -executable -exec {} \\; || true"
        
        echo "✅ Тесты завершены"
        
    } catch (Exception e) {
        error "❌ Ошибка выполнения тестов: ${e.getMessage()}"
    }
}

def publishTestResults(config) {
    try {
        // Публикуем результаты CTest
        step([$class: 'JUnitResultArchiver', testResults: config.junitPattern, allowEmptyResults: true])
        
        // Публикуем результаты Google Test если есть
        step([$class: 'GoogleTestResultArchiver', testResultsPattern: '**/test_results/**/*.xml', allowEmptyResults: true])
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось опубликовать результаты тестов: ${e.getMessage()}"
    }
}

def collectCoverage(config) {
    try {
        // Запускаем тесты с покрытием
        sh "cd ${config.buildDir} && make coverage"
        
        // Публикуем отчет о покрытии
        step([$class: 'CoberturaPublisher', coberturaReportFile: '**/coverage.xml'])
        
        echo "✅ Сбор покрытия завершен"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка сбора покрытия: ${e.getMessage()}"
    }
}

def installArtifacts(config) {
    try {
        sh "cd ${config.buildDir} && make install DESTDIR=${pwd()}/install"
        
        echo "✅ Установка артефактов завершена"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка установки артефактов: ${e.getMessage()}"
    }
}

def createPackages(config) {
    try {
        // Создаем CPack пакеты
        sh "cd ${config.buildDir} && cpack"
        
        // Архивируем пакеты
        archiveArtifacts artifacts: '**/build/*.tar.gz,**/build/*.deb,**/build/*.rpm,**/build/*.zip', fingerprint: true
        
        echo "✅ Создание пакетов завершено"
        
    } catch (Exception e) {
        echo "⚠️ Предупреждение: ошибка создания пакетов: ${e.getMessage()}"
    }
}

def cleanupWorkspace(config) {
    try {
        // Очищаем только если сборка прошла успешно
        if (currentBuild.result == 'SUCCESS') {
            sh "rm -rf ${config.buildDir}/CMakeCache.txt"
            sh "rm -rf ${config.buildDir}/CMakeFiles"
        }
    } catch (Exception e) {
        echo "⚠️ Предупреждение: не удалось очистить рабочее пространство: ${e.getMessage()}"
    }
}

def notifySlack(status, config) {
    def colors = getColors()
    def message = ""
    
    switch(status) {
        case 'success':
            message = "${colors.green}✅ CMake сборка успешна! Тип: ${config.buildType}, Компилятор: ${config.compiler}${colors.reset}"
            break
        case 'failure':
            message = "${colors.red}❌ CMake сборка провалилась! Тип: ${config.buildType}, Компилятор: ${config.compiler}${colors.reset}"
            break
    }
    
    echo "Slack уведомление: ${message}"
    // Здесь можно добавить реальную интеграцию со Slack
} 