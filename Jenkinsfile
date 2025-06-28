@Library('lib@develop') _

astraLinuxPipeline([
    astraVersion: 'orel',
    baseImage: 'astra/orel:latest',
    imageName: 'test-app',
    imageTag: 'latest',
    enableQt: false,
    projectType: 'generic',
    buildSystem: 'cmake',
    parallelJobs: 2,
    securityScan: false,
    runTests: false
]) 