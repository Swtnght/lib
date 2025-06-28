# Jenkins Shared Library для CMake проектов

Этот репозиторий содержит Jenkins Shared Library, специально разработанную для автоматизации сборки и тестирования C/C++ проектов, использующих CMake.

## 🚀 Возможности

### Основные Pipeline
- **buildPipeline** - Универсальный pipeline для сборки проектов (Maven, Gradle)
- **dockerPipeline** - Pipeline для работы с Docker контейнерами
- **deployPipeline** - Pipeline для деплоя приложений
- **cmakePipeline** - Специализированный pipeline для CMake проектов

### CMake Pipeline Особенности
- ✅ Поддержка различных компиляторов (GCC, Clang, MSVC)
- ✅ Интеграция с пакетными менеджерами (Conan, vcpkg)
- ✅ Статический анализ кода (cppcheck, clang-tidy)
- ✅ Покрытие кода (gcov, lcov)
- ✅ Санитайзеры (AddressSanitizer, ThreadSanitizer, MemorySanitizer)
- ✅ Кросс-компиляция
- ✅ Параллельная сборка
- ✅ Создание пакетов (CPack)
- ✅ Интеграция с тестовыми фреймворками (Google Test, CTest)

## 📁 Структура проекта

```
jenkins-shared-library/
├── vars/                          # Основные pipeline функции
│   ├── globalVars.groovy         # Глобальные переменные
│   ├── buildPipeline.groovy      # Универсальный pipeline
│   ├── dockerPipeline.groovy     # Docker pipeline
│   ├── deployPipeline.groovy     # Deploy pipeline
│   └── cmakePipeline.groovy      # CMake pipeline
├── resources/                     # Шаблоны и ресурсы
│   └── cmake-templates/          # Шаблоны для CMake проектов
│       ├── CMakeLists.txt.template
│       ├── conanfile.txt.template
│       └── vcpkg.json.template
├── examples/                      # Примеры использования
│   ├── Jenkinsfile.cmake
│   ├── Jenkinsfile.cmake.debug
│   └── Jenkinsfile.cmake.cross
└── README.md
```

## 🛠️ Установка

### 1. Настройка Jenkins

1. Перейдите в **Manage Jenkins** → **Configure System**
2. Найдите секцию **Global Pipeline Libraries**
3. Добавьте новую библиотеку:
   - **Name**: `jenkins-shared-library`
   - **Source Code Management**: Git
   - **Project Repository**: URL вашего репозитория
   - **Credentials**: Добавьте credentials для доступа к репозиторию
   - **Branch**: `main` или `master`

### 2. Настройка Jenkins Agents

Убедитесь, что ваши Jenkins agents имеют установленные инструменты:

```bash
# Основные инструменты
sudo apt-get update
sudo apt-get install -y cmake build-essential gcc g++ clang

# Для тестирования
sudo apt-get install -y libgtest-dev google-mock

# Для статического анализа
sudo apt-get install -y cppcheck clang-tidy

# Для покрытия кода
sudo apt-get install -y lcov gcovr

# Для пакетных менеджеров
pip install conan
git clone https://github.com/Microsoft/vcpkg.git
cd vcpkg && ./bootstrap-vcpkg.sh
```

## 📖 Использование

### Базовый CMake Pipeline

```groovy
@Library('jenkins-shared-library') _

cmakePipeline([
    buildType: 'Release',
    compiler: 'gcc',
    cppStandard: '17',
    enableTests: true,
    packageManager: 'conan'
])
```

### Отладочная сборка с покрытием

```groovy
@Library('jenkins-shared-library') _

cmakePipeline([
    buildType: 'Debug',
    compiler: 'clang',
    cppStandard: '20',
    enableTests: true,
    enableCoverage: true,
    enableSanitizers: true,
    sanitizerType: 'address',
    enableStaticAnalysis: true,
    staticAnalyzer: 'clang-tidy',
    packageManager: 'vcpkg'
])
```

### Кросс-компиляция

```groovy
@Library('jenkins-shared-library') _

cmakePipeline([
    buildType: 'Release',
    compiler: 'gcc',
    crossCompile: true,
    targetPlatform: 'Linux',
    targetArch: 'aarch64',
    packageManager: 'conan'
])
```

## ⚙️ Конфигурация

### Параметры CMake Pipeline

| Параметр | Тип | По умолчанию | Описание |
|----------|-----|--------------|----------|
| `buildType` | String | 'Release' | Тип сборки (Debug, Release, RelWithDebInfo, MinSizeRel) |
| `buildDir` | String | 'build' | Директория для сборки |
| `sourceDir` | String | '.' | Директория с исходным кодом |
| `compiler` | String | 'gcc' | Компилятор (gcc, clang, msvc) |
| `cppStandard` | String | '17' | Стандарт C++ |
| `enableTests` | Boolean | true | Включить тесты |
| `enableCoverage` | Boolean | false | Включить покрытие кода |
| `enableSanitizers` | Boolean | false | Включить санитайзеры |
| `sanitizerType` | String | 'address' | Тип санитайзера |
| `enableStaticAnalysis` | Boolean | false | Включить статический анализ |
| `staticAnalyzer` | String | 'cppcheck' | Анализатор (cppcheck, clang-tidy) |
| `packageManager` | String | 'conan' | Пакетный менеджер (conan, vcpkg, none) |
| `parallelJobs` | Integer | 4 | Количество параллельных задач |
| `timeout` | Integer | 60 | Таймаут в минутах |
| `nodeLabel` | String | 'linux' | Метка Jenkins agent |

### Пакетные менеджеры

#### Conan
```groovy
cmakePipeline([
    packageManager: 'conan',
    conanProfile: 'default'
])
```

#### vcpkg
```groovy
cmakePipeline([
    packageManager: 'vcpkg',
    vcpkgTriplet: 'x64-linux'
])
```

## 🔧 Шаблоны проектов

### CMakeLists.txt
Используйте шаблон `resources/cmake-templates/CMakeLists.txt.template` как основу для вашего проекта.

### Conan
Создайте `conanfile.txt` на основе `resources/cmake-templates/conanfile.txt.template`.

### vcpkg
Создайте `vcpkg.json` на основе `resources/cmake-templates/vcpkg.json.template`.

## 📊 Отчеты и артефакты

Pipeline автоматически создает и архивирует:
- Скомпилированные библиотеки и исполняемые файлы
- Результаты тестов (JUnit, Google Test)
- Отчеты о покрытии кода (Cobertura)
- Пакеты (CPack)
- Результаты статического анализа

## 🔔 Уведомления

Pipeline поддерживает уведомления через:
- Slack (настраивается через переменные окружения)
- Email (настраивается через параметры)

## 🐛 Отладка

### Логи
Все этапы pipeline логируются с временными метками и цветным выводом.

### Переменные окружения
Pipeline устанавливает переменные окружения для каждого этапа, что упрощает отладку.

### Очистка
Автоматическая очистка рабочего пространства после успешной сборки.

## 🤝 Вклад в проект

1. Форкните репозиторий
2. Создайте ветку для новой функции
3. Внесите изменения
4. Добавьте тесты
5. Создайте Pull Request

## 📄 Лицензия

MIT License - см. файл LICENSE для деталей.

## 🆘 Поддержка

Если у вас есть вопросы или проблемы:
1. Проверьте документацию
2. Посмотрите примеры в папке `examples/`
3. Создайте Issue в репозитории

## 🔗 Полезные ссылки

- [CMake Documentation](https://cmake.org/documentation/)
- [Conan Documentation](https://docs.conan.io/)
- [vcpkg Documentation](https://github.com/microsoft/vcpkg)
- [Jenkins Pipeline Documentation](https://www.jenkins.io/doc/book/pipeline/)
- [Google Test Documentation](https://google.github.io/googletest/) 