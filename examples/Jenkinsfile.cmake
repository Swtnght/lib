@Library('jenkins-shared-library') _

// Пример использования CMake pipeline
cmakePipeline([
    buildType: 'Release',
    compiler: 'gcc',
    cppStandard: '17',
    enableTests: true,
    enableCoverage: false,
    enableSanitizers: false,
    enableStaticAnalysis: true,
    staticAnalyzer: 'cppcheck',
    packageManager: 'conan',
    parallelJobs: 4,
    timeout: 60,
    nodeLabel: 'linux',
    notifySlack: true
]) 