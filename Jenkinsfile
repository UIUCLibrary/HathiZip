// ****************************************************************************
//  Constants
//
// ============================================================================
// Versions of python that are supported
// ----------------------------------------------------------------------------
SUPPORTED_MAC_VERSIONS = ['3.8', '3.9', '3.10', '3.11', '3.12']
SUPPORTED_LINUX_VERSIONS = ['3.8', '3.9', '3.10', '3.11', '3.12']
SUPPORTED_WINDOWS_VERSIONS = ['3.8', '3.9', '3.10', '3.11', '3.12']

// ============================================================================

def getDevpiConfig() {
    node(){
        configFileProvider([configFile(fileId: 'devpi_config', variable: 'CONFIG_FILE')]) {
            def configProperties = readProperties(file: CONFIG_FILE)
            configProperties.stagingIndex = {
                if (env.TAG_NAME?.trim()){
                    return 'tag_staging'
                } else{
                    return "${env.BRANCH_NAME}_staging"
                }
            }()
            return configProperties
        }
    }
}
def DEVPI_CONFIG = getDevpiConfig()

def getPypiConfig() {
    node(){
        configFileProvider([configFile(fileId: 'pypi_config', variable: 'CONFIG_FILE')]) {
            def config = readJSON( file: CONFIG_FILE)
            return config['deployment']['indexes']
        }
    }
}

// ****************************************************************************

node(){
    checkout scm
    devpi = load('ci/jenkins/scripts/devpi.groovy')
}

def startup(){
    parallel(
        [
            failFast: true,
            'Loading Reference Build Information': {
                node(){
                    checkout scm
                    discoverGitReferenceBuild()
                }
            },
            'Enable Git Forensics': {
                node(){
                    checkout scm
                    mineRepository()
                }
            },
            'Getting Distribution Info': {
                node('linux && docker') {
                    timeout(2){
                        ws{
                            checkout scm
                            try{
                                docker.image('python').inside {
                                    withEnv(['PIP_NO_CACHE_DIR=off']) {
                                        sh(
                                           label: 'Running setup.py with dist_info',
                                           script: '''python --version
                                                      python setup.py dist_info
                                                   '''
                                        )
                                    }
                                    stash includes: '*.dist-info/**', name: 'DIST-INFO'
                                    archiveArtifacts artifacts: '*.dist-info/**'
                                }
                            } finally{
                                deleteDir()
                            }
                        }
                    }
                }
            }
        ]
    )
}

def get_props(){
    stage('Reading Package Metadata'){
        node(){
            unstash 'DIST-INFO'
            def metadataFile = findFiles( glob: '*.dist-info/METADATA')[0]
            def metadata = readProperties(interpolate: true, file: metadataFile.path )
            echo """Version = ${metadata.Version}
Name = ${metadata.Name}
"""
            return metadata
        }
    }
}

startup()
def props = get_props()

pipeline {
    agent none
    parameters {
        string(name: 'PROJECT_NAME', defaultValue: 'HathiTrust Zip for Submit', description: 'Name given to the project')
        booleanParam(name: 'RUN_CHECKS', defaultValue: true, description: 'Run checks on code')
        booleanParam(name: 'USE_SONARQUBE', defaultValue: true, description: 'Send data test data to SonarQube')
        credentials(name: 'SONARCLOUD_TOKEN', credentialType: 'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', defaultValue: 'sonarcloud_token', required: false)
        booleanParam(name: 'TEST_RUN_TOX', defaultValue: false, description: 'Run Tox Tests')
        booleanParam(name: 'BUILD_PACKAGES', defaultValue: false, description: 'Build Python packages')
        booleanParam(name: 'TEST_PACKAGES', defaultValue: false, description: 'Test packages')
        booleanParam(name: 'INCLUDE_LINUX_ARM', defaultValue: false, description: 'Include ARM architecture for Linux')
        booleanParam(name: 'INCLUDE_LINUX_X86_64', defaultValue: true, description: 'Include x86_64 architecture for Linux')
        booleanParam(name: 'INCLUDE_MACOS_ARM', defaultValue: false, description: 'Include ARM(m1) architecture for Mac')
        booleanParam(name: 'INCLUDE_MACOS_X86_64', defaultValue: false, description: 'Include x86_64 architecture for Mac')
        booleanParam(name: 'INCLUDE_WINDOWS_X86_64', defaultValue: false, description: 'Include x86_64 architecture for Windows')
        booleanParam(name: 'DEPLOY_DEVPI', defaultValue: false, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: 'DEPLOY_DEVPI_PRODUCTION', defaultValue: false, description: 'Deploy to https://devpi.library.illinois.edu/production/release')
        booleanParam(name: 'DEPLOY_PYPI', defaultValue: false, description: 'Deploy to pypi')
        booleanParam(name: 'DEPLOY_DOCS', defaultValue: false, description: 'Update online documentation')
    }
    options {
        retry(conditions: [agent()], count: 2)
    }
    stages {
        stage('Building and Testing'){
            when{
                anyOf{
                    equals expected: true, actual: params.RUN_CHECKS
                    equals expected: true, actual: params.TEST_RUN_TOX
                    equals expected: true, actual: params.DEPLOY_DEVPI
                    equals expected: true, actual: params.DEPLOY_DOCS
                }
            }
            stages{
                stage('Build'){
                    agent {
                         dockerfile {
                             filename 'ci/docker/python/linux/jenkins/Dockerfile'
                             label 'linux && docker && x86'
                             additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST --build-arg PYTHON_VERSION=3.11 --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip'
                             args '--mount source=pipcache_hathizip,target=/.cache/pip'
                         }
                    }
                    options {
                        retry(conditions: [agent()], count: 3)
                    }
                    when{
                        anyOf{
                            equals expected: true, actual: params.RUN_CHECKS
                            equals expected: true, actual: params.DEPLOY_DEVPI
                            equals expected: true, actual: params.DEPLOY_DOCS
                        }
                        beforeAgent true
                    }
                    stages{
                        stage('Python build'){
                            steps {
                                tee('logs/build.log'){
                                    sh(script: '''mkdir -p logs
                                                  python setup.py build -b build
                                                  '''
                                    )
                                }
                            }
                            post{
                                always{
                                    archiveArtifacts artifacts: 'logs/build.log'
                                }
                                cleanup{
                                    cleanWs(
                                        deleteDirs: true,
                                        patterns: [
                                            [pattern: 'dist/', type: 'INCLUDE'],
                                            [pattern: 'build/', type: 'INCLUDE']
                                        ]
                                    )
                                }
                            }
                        }
                        stage('Building Sphinx Documentation'){
                            steps {
                                catchError(buildResult: 'UNSTABLE', message: 'Building documentation produced an error or a warning', stageResult: 'UNSTABLE') {
                                    sh(
                                        label: 'Building docs',
                                        script: '''mkdir -p logs
                                                   python -m sphinx docs/source build/docs/html -d build/docs/.doctrees -v -w logs/build_sphinx.log -W --keep-going
                                                   '''
                                    )
                                }
                            }
                            post{
                                always {
                                    recordIssues(tools: [sphinxBuild(name: 'Sphinx Documentation Build', pattern: 'logs/build_sphinx.log')])
                                    archiveArtifacts artifacts: 'logs/build_sphinx.log'
                                    zip archive: true, dir: 'build/docs/html', glob: '', zipFile: "dist/${props.Name}-${props.Version}.doc.zip"
                                    stash includes: 'dist/*.doc.zip,build/docs/html/**', name: 'DOCS_ARCHIVE'
                                }
                                success{
                                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'build/docs/html', reportFiles: 'index.html', reportName: 'Documentation', reportTitles: ''])
                                }
                                cleanup{
                                    cleanWs(
                                        deleteDirs: true,
                                        patterns: [
                                            [pattern: 'dist/', type: 'INCLUDE'],
                                            [pattern: 'build/', type: 'INCLUDE'],
                                            [pattern: 'HathiZip.dist-info/', type: 'INCLUDE'],
                                        ]
                                    )
                                }
                            }
                        }
                    }
                }
                stage('Checks') {
                    when{
                        equals expected: true, actual: params.RUN_CHECKS
                    }
                    stages{
                        stage('Code Quality'){
                            agent {
                                dockerfile {
                                    filename 'ci/docker/python/linux/jenkins/Dockerfile'
                                    label 'linux && docker && x86'
                                    additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST --build-arg PYTHON_VERSION=3.11 --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip'
                                    args '--mount source=sonar-cache-hathizip,target=/opt/sonar/.sonar/cache --mount source=pipcache_hathizip,target=/.cache/pip'
                                }
                            }
                            stages{
                                stage('Run Test'){
                                    stages{
                                        stage('Set up Tests'){
                                            steps{
                                                sh '''mkdir -p logs
                                                      python setup.py build
                                                      mkdir -p reports
                                                      '''
                                            }
                                        }
                                        stage('Run Tests'){
                                            parallel{
                                                stage('PyTest'){
                                                    steps{
                                                        sh(label: 'Running pytest',
                                                            script: 'coverage run --parallel-mode --source=hathizip -m pytest --junitxml=./reports/tests/pytest/pytest-junit.xml'
                                                        )
                                                    }
                                                    post {
                                                        always{
                                                            stash includes: 'reports/tests/pytest/*.xml', name: 'PYTEST_UNIT_TEST_RESULTS'
                                                            junit 'reports/tests/pytest/pytest-junit.xml'
                                                        }
                                                    }
                                                }
                                                stage('Run Pylint Static Analysis') {
                                                    steps{
                                                        catchError(buildResult: 'SUCCESS', message: 'Pylint found issues', stageResult: 'UNSTABLE') {
                                                            sh(label: 'Running pylint',
                                                                script: 'pylint hathizip -r n --msg-template="{path}:{line}: [{msg_id}({symbol}), {obj}] {msg}" > reports/pylint.txt'
                                                            )
                                                        }
                                                        sh(
                                                            script: 'pylint hathizip -r n --msg-template="{path}:{module}:{line}: [{msg_id}({symbol}), {obj}] {msg}" | tee reports/pylint_issues.txt',
                                                            label: 'Running pylint for sonarqube',
                                                            returnStatus: true
                                                        )
                                                    }
                                                    post{
                                                        always{
                                                            recordIssues(tools: [pyLint(pattern: 'reports/pylint.txt')])
                                                            stash includes: 'reports/pylint_issues.txt,reports/pylint.txt', name: 'PYLINT_REPORT'
                                                        }
                                                    }
                                                }
                                                stage('Doctest'){
                                                    steps{
                                                        unstash 'DOCS_ARCHIVE'
                                                        sh 'coverage run --parallel-mode --source=hathizip -m sphinx -b doctest docs/source build/docs -d build/docs/doctrees -v'
                                                    }
                                                    post{
                                                        failure{
                                                            sh 'ls -R build/docs/'
                                                        }
                                                    }
                                                }
                                                stage('Task Scanner'){
                                                    steps{
                                                        recordIssues(tools: [taskScanner(highTags: 'FIXME', includePattern: 'hathizip/**/*.py', normalTags: 'TODO')])
                                                    }
                                                }
                                                stage('MyPy'){
                                                    steps{
                                                        sh 'mkdir -p reports/mypy && mkdir -p logs'
                                                        catchError(buildResult: 'SUCCESS', message: 'mypy found some warnings', stageResult: 'UNSTABLE') {
                                                            tee('logs/mypy.log'){
                                                                sh(
                                                                    script: 'mypy -p hathizip --html-report reports/mypy/mypy_html'
                                                                )
                                                            }
                                                        }
                                                    }
                                                    post{
                                                        always {
                                                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/mypy/mypy_html', reportFiles: 'index.html', reportName: 'MyPy', reportTitles: ''])
                                                            recordIssues(tools: [myPy(name: 'MyPy', pattern: 'logs/mypy.log')])
                                                        }
                                                    }
                                                }
                                                stage('Run Flake8 Static Analysis') {
                                                    steps{
                                                        catchError(buildResult: 'SUCCESS', message: 'flake8 found some warnings', stageResult: 'UNSTABLE') {
                                                            sh(label: 'Running flake8',
                                                               script: 'flake8 hathizip --tee --output-file=logs/flake8.log'
                                                            )
                                                        }
                                                    }
                                                    post {
                                                        always {
                                                            stash includes: 'logs/flake8.log', name: 'FLAKE8_REPORT'
                                                            recordIssues(tools: [flake8(name: 'Flake8', pattern: 'logs/flake8.log')])
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    post{
                                        always{
                                            sh(label: 'combining coverage data',
                                               script: '''coverage combine
                                                          coverage xml -o ./reports/coverage.xml
                                                          '''
                                            )
                                            stash(includes: 'reports/coverage*.xml', name: 'COVERAGE_REPORT_DATA')
                                            publishCoverage(
                                                        adapters: [
                                                                coberturaAdapter('reports/coverage.xml')
                                                            ],
                                                        sourceFileResolver: sourceFiles('STORE_ALL_BUILD')
                                                    )
                                        }
                                    }
                                }
                                stage('Run Sonarqube Analysis'){
                                    options{
                                        lock('hathizip-sonarscanner')
                                    }
                                    when{
                                        allOf{
                                            equals expected: true, actual: params.USE_SONARQUBE
                                            expression{
                                                try{
                                                    withCredentials([string(credentialsId: params.SONARCLOUD_TOKEN, variable: 'dddd')]) {
                                                        echo 'Found credentials for sonarqube'
                                                    }
                                                } catch(e){
                                                    return false
                                                }
                                                return true
                                            }
                                        }
                                        beforeAgent true
                                        beforeOptions true
                                    }
                                    steps{
                                        script{
                                            def sonarqube = fileLoader.fromGit(
                                                'sonarqube',
                                                'https://github.com/UIUCLibrary/jenkins_helper_scripts.git',
                                                '3',
                                                null,
                                                ''
                                            )
                                            sonarqube.sonarcloudSubmit(
                                                credentialsId: params.SONARCLOUD_TOKEN,
                                                projectVersion: props.Version
                                            )
                                            milestone label: 'sonarcloud'
                                        }
                                    }
                                    post {
                                        always{
                                            recordIssues(tools: [sonarQube(pattern: 'reports/sonar-report.json')])
                                        }
                                    }
                                }
                            }
                            post{
                                cleanup{
                                    cleanWs(
                                        deleteDirs: true,
                                        patterns: [
                                            [pattern: '.coverage/', type: 'INCLUDE'],
                                            [pattern: '.mypy_cache/', type: 'INCLUDE'],
                                            [pattern: '.pytest_cache/', type: 'INCLUDE'],
                                            [pattern: '.scannerwork/', type: 'INCLUDE'],
                                            [pattern: '**/__pycache__/', type: 'INCLUDE'],
                                            [pattern: 'build/', type: 'INCLUDE'],
                                            [pattern: 'coverage/', type: 'INCLUDE'],
                                            [pattern: 'dist/', type: 'INCLUDE'],
                                            [pattern: 'logs/', type: 'INCLUDE'],
                                            [pattern: 'reports/', type: 'INCLUDE'],
                                        ]
                                    )
                                }
                            }
                        }
                    }
                }
                stage('Run Tox'){
                    when{
                        equals expected: true, actual: params.TEST_RUN_TOX
                    }
                    options {
                        lock(env.JOB_URL)
                    }
                    steps {
                        script{
                            def tox = fileLoader.fromGit(
                                    'tox',
                                    'https://github.com/UIUCLibrary/jenkins_helper_scripts.git',
                                    '8',
                                    null,
                                    ''
                                    )
                            def windowsJobs
                            def linuxJobs
                            stage('Scanning Tox Environments'){
                                parallel(
                                    'Linux':{
                                        linuxJobs = tox.getToxTestsParallel(
                                                envNamePrefix: 'Tox Linux',
                                                label: 'linux && docker',
                                                dockerfile: 'ci/docker/python/linux/tox/Dockerfile',
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip',
                                                dockerRunArgs: '-v pipcache_hathizip:/.cache/pip',
                                                retry: 2
                                            )
                                    },
                                    'Windows':{
                                        windowsJobs = tox.getToxTestsParallel(
                                                envNamePrefix: 'Tox Windows',
                                                label: 'windows && docker',
                                                dockerfile: 'ci/docker/python/windows/tox/Dockerfile',
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg CHOCOLATEY_SOURCE --build-arg PIP_DOWNLOAD_CACHE=c:/users/containeradministrator/appdata/local/pip',
                                                dockerRunArgs: '-v pipcache_hathizip:c:/users/containeradministrator/appdata/local/pip',
                                                retry: 2

                                            )
                                    },
                                    failFast: true
                                )
                            }
                            parallel(windowsJobs + linuxJobs)
                        }
                    }
                }
            }
        }
        stage('Packaging') {
            when{
                anyOf{
                    equals expected: true, actual: params.BUILD_PACKAGES
                    equals expected: true, actual: params.DEPLOY_PYPI
                    equals expected: true, actual: params.DEPLOY_DEVPI
                    equals expected: true, actual: params.DEPLOY_DEVPI_PRODUCTION
                }
                beforeAgent true
            }
            options {
                lock(env.JOB_URL)
            }
            stages{
                stage('Source and Wheel formats'){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/jenkins/Dockerfile'
                            label 'linux && docker && x86'
                            additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST --build-arg PYTHON_VERSION=3.11 --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip'
                            args '--mount source=pipcache_hathizip,target=/.cache/pip'
                        }
                    }
                    steps{
                        timeout(5){
                            sh 'python setup.py sdist -d dist bdist_wheel -d dist'
                        }
                    }
                    post{
                        success{
                            archiveArtifacts artifacts: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', fingerprint: true
                            stash includes: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', name: 'dist'
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: 'build/', type: 'INCLUDE'],
                                    [pattern: 'dist/', type: 'INCLUDE'],
                                    [pattern: 'logs/', type: 'INCLUDE'],
                                    [pattern: 'HathiZip.egg-info/', type: 'INCLUDE'],
                                ]
                            )
                        }
                    }
                }
                stage('Testing Python Packages'){
                    when{
                        equals expected: true, actual: params.TEST_PACKAGES
                        beforeAgent true
                    }
                    steps{
                        script{
                            def packages
                            node(){
                                checkout scm
                                packages = load 'ci/jenkins/scripts/packaging.groovy'
                            }
                            def windowsTests = [:]
                            if(params.INCLUDE_WINDOWS_X86_64 == true){
                                SUPPORTED_WINDOWS_VERSIONS.each{ pythonVersion ->
                                    windowsTests["Windows - Python ${pythonVersion}: sdist"] = {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=c:/users/containeradministrator/appdata/local/pip',
                                                    args: '-v pipcache_hathizip:c:/users/containeradministrator/appdata/local/pip'
                                                ]
                                            ],
                                            retryTimes: 3,
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: pythonVersion
                                        )
                                    }
                                    windowsTests["Windows - Python ${pythonVersion}: wheel"] = {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=c:/users/containeradministrator/appdata/local/pip',
                                                    args: '-v pipcache_hathizip:c:/users/containeradministrator/appdata/local/pip'
                                                ]
                                            ],
                                            retryTimes: 3,
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: pythonVersion
                                        )
                                    }
                                }
                            }

                            def linuxTests = [:]
                            SUPPORTED_LINUX_VERSIONS.each{ pythonVersion ->
                                def architectures = []
                                if(params.INCLUDE_LINUX_ARM == true){
                                    architectures.add("arm")
                                }
                                if(params.INCLUDE_LINUX_X86_64 == true){
                                    architectures.add("x86_64")
                                }
                                architectures.each{ processorArchitecture ->
                                    linuxTests["Linux ${processorArchitecture} - Python ${pythonVersion}-${processorArchitecture}: sdist"] = {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: "linux && docker && ${processorArchitecture}",
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip',
                                                    args: '-v pipcache_hathizip:/.cache/pip',
                                                ]
                                            ],
                                            retryTimes: 1,
                                            glob: 'dist/*.tar.gz',
                                            stash: 'dist',
                                            pythonVersion: pythonVersion

                                        )
                                    }
                                    linuxTests["Linux ${processorArchitecture} - Python ${pythonVersion}-${processorArchitecture}: wheel"] = {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: "linux && docker && ${processorArchitecture}",
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip',
                                                    args: '-v pipcache_hathizip:/.cache/pip',
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: pythonVersion,
                                            testSetup: {
                                                sh( script: 'printenv', label: 'checking env variables')
                                            }
                                        )
                                    }
                                }
                            }

                            def macTests = [:]
                            SUPPORTED_MAC_VERSIONS.each{ pythonVersion ->
                                def architectures = []
                                if(params.INCLUDE_MACOS_ARM == true){
                                    architectures.add('m1')
                                }
                                if(params.INCLUDE_MACOS_X86_64 == true){
                                    architectures.add('x86_64')
                                }
                                architectures.each{ processorArchitecture ->
                                    if (nodesByLabel("mac && ${processorArchitecture} && python${pythonVersion}").size() > 0){
                                        macTests["Mac ${processorArchitecture} - Python ${pythonVersion}: sdist"] = {
                                            packages.testPkg(
                                                    agent: [
                                                        label: "mac && python${pythonVersion} && ${processorArchitecture}",
                                                    ],
                                                    glob: 'dist/*.tar.gz,dist/*.zip',
                                                    stash: 'dist',
                                                    pythonVersion: pythonVersion,
                                                    toxExec: 'venv/bin/tox',
                                                    testSetup: {
                                                        checkout scm
                                                        unstash 'dist'
                                                        sh(
                                                            label:'Install Tox',
                                                            script: '''python3 -m venv venv
                                                                      . ./venv/bin/activate
                                                                      python -m pip install --upgrade pip
                                                                      pip install -r requirements/requirements_tox.txt
                                                                    '''
                                                        )
                                                    },
                                                    testTeardown: {
                                                        sh 'rm -r venv/'
                                                    }

                                                )
                                        }
                                        macTests["Mac ${processorArchitecture} - Python ${pythonVersion}: wheel"] = {
                                            packages.testPkg(
                                                    agent: [
                                                        label: "mac && python${pythonVersion} && ${processorArchitecture}",
                                                    ],
                                                    glob: 'dist/*.whl',
                                                    stash: 'dist',
                                                    pythonVersion: pythonVersion,
                                                    toxExec: 'venv/bin/tox',
                                                    testSetup: {
                                                        checkout scm
                                                        unstash 'dist'
                                                        sh(
                                                            label:'Install Tox',
                                                            script: '''python3 -m venv venv
                                                                       . ./venv/bin/activate
                                                                       python -m pip install --upgrade pip
                                                                       pip install -r requirements/requirements_tox.txt
                                                                    '''
                                                        )
                                                    },
                                                    testTeardown: {
                                                        sh 'rm -r venv/'
                                                    }

                                                )
                                        }
                                    } else {
                                        echo "Skipping testing on ${processorArchitecture} Mac with Python ${pythonVersion} due to no available agents."
                                    }
                                }
                            }
                            def tests = linuxTests + windowsTests + macTests
                            parallel(tests)
                        }
                    }
                }
            }
        }
        stage('Deploying to Devpi') {
            when {
                allOf{
                    anyOf{
                        equals expected: true, actual: params.DEPLOY_DEVPI
                    }
                    anyOf {
                        equals expected: 'master', actual: env.BRANCH_NAME
                        equals expected: 'dev', actual: env.BRANCH_NAME
                        tag '*'
                    }
                }
                beforeAgent true
                beforeOptions true
            }
            options{
                lock('HathiZip-devpi')
            }
            agent none
            stages{
                stage('Uploading to DevPi Staging'){
                    agent {
                        dockerfile {
                            additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                            filename 'ci/docker/python/linux/tox/Dockerfile'
                            label 'linux && docker && devpi-access'
                        }
                    }
                    options {
                        retry(2)
                    }
                    steps {
                        timeout(5){
                            unstash 'DOCS_ARCHIVE'
                            unstash 'dist'
                            script{
                                devpi.upload(
                                        server: DEVPI_CONFIG.server,
                                        credentialsId: DEVPI_CONFIG.credentialsId,
                                        index: DEVPI_CONFIG.stagingIndex,
                                        clientDir: './devpi'
                                    )
                            }
                        }
                    }
                    post{
                        always{
                            archiveArtifacts artifacts: 'devpi/*'
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: 'dist/', type: 'INCLUDE'],
                                    [pattern: '*.dist-info/', type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE']
                                ]
                            )
                        }
                    }
                }
                stage('Test DevPi packages') {
                    steps{
                        script{
                            def macPackages = [:]
                            SUPPORTED_MAC_VERSIONS.each{pythonVersion ->
                                def architectures = []
                                if(params.INCLUDE_MACOS_ARM == true){
                                    architectures.add('m1')
                                }
                                if(params.INCLUDE_MACOS_X86_64 == true){
                                    architectures.add('x86_64')
                                }
                                architectures.each{ processorArchitecture ->
                                    if (nodesByLabel("mac && ${processorArchitecture} && python${pythonVersion}").size() > 0){
                                        macPackages["Test Python ${pythonVersion}: wheel Mac ${processorArchitecture}"] = {
                                            withEnv(['PATH+EXTRA=./venv/bin']) {
                                                devpi.testDevpiPackage(
                                                    agent: [
                                                        label: "mac && python${pythonVersion} && devpi-access && ${processorArchitecture}"
                                                    ],
                                                    devpi: [
                                                        index: DEVPI_CONFIG.stagingIndex,
                                                        server: DEVPI_CONFIG.server,
                                                        credentialsId: DEVPI_CONFIG.credentialsId,
                                                        devpiExec: 'venv/bin/devpi'
                                                    ],
                                                    package:[
                                                        name: props.Name,
                                                        version: props.Version,
                                                        selector: 'whl'
                                                    ],
                                                    test:[
                                                        setup: {
                                                            checkout scm
                                                            sh(
                                                                label:'Installing Devpi client',
                                                                script: '''python3 -m venv venv
                                                                           . venv/bin/activate
                                                                           python -m pip install pip --upgrade
                                                                           python -m pip install 'devpi-client<7.0' -r requirements/requirements_tox.txt
                                                                        '''
                                                            )
                                                        },
                                                        toxEnv: "py${pythonVersion}".replace('.',''),
                                                        teardown: {
                                                            sh( label: 'Remove Devpi client', script: 'rm -r venv')
                                                        }
                                                    ]
                                                )
                                            }
                                        }
                                        macPackages["Test Python ${pythonVersion}: sdist Mac ${processorArchitecture}"]= {
                                            withEnv(['PATH+EXTRA=./venv/bin']) {
                                                devpi.testDevpiPackage(
                                                    agent: [
                                                        label: "mac && python${pythonVersion} && devpi-access && ${processorArchitecture}"
                                                    ],
                                                    devpi: [
                                                        index: DEVPI_CONFIG.stagingIndex,
                                                        server: DEVPI_CONFIG.server,
                                                        credentialsId: DEVPI_CONFIG.credentialsId,
                                                        devpiExec: 'venv/bin/devpi'
                                                    ],
                                                    package:[
                                                        name: props.Name,
                                                        version: props.Version,
                                                        selector: 'tar.gz'
                                                    ],
                                                    test:[
                                                        setup: {
                                                            checkout scm
                                                            sh(
                                                                label:'Installing Devpi client',
                                                                script: '''python3 -m venv venv
                                                                           . venv/bin/activate
                                                                           python -m pip install pip --upgrade
                                                                           python -m pip install 'devpi-client<7.0' -r requirements/requirements_tox.txt
                                                                        '''
                                                            )
                                                        },
                                                        toxEnv: "py${pythonVersion}".replace('.',''),
                                                        teardown: {
                                                            sh( label: 'Remove Devpi client', script: 'rm -r venv')
                                                        }
                                                    ]
                                                )
                                            }
                                        }
                                    } else {
                                        echo "Skipping testing on ${processorArchitecture} Mac with Python ${pythonVersion} due to no available agents."
                                    }
                                }
                            }
                            def windowsPackages = [:]
                            if(params.INCLUDE_WINDOWS_X86_64 == true){
                                SUPPORTED_WINDOWS_VERSIONS.each{pythonVersion ->
                                    windowsPackages["Test Python ${pythonVersion}: sdist Windows"] = {
                                        devpi.testDevpiPackage(
                                            agent: [
                                                dockerfile: [
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL',
                                                    label: 'windows && docker && devpi-access'
                                                ]
                                            ],
                                            retries: 3,
                                            devpi: [
                                                index: DEVPI_CONFIG.stagingIndex,
                                                server: DEVPI_CONFIG.server,
                                                credentialsId: DEVPI_CONFIG.credentialsId,
                                            ],
                                            package:[
                                                name: props.Name,
                                                version: props.Version,
                                                selector: 'tar.gz'
                                            ],
                                            test:[
                                                setup: {
                                                    powershell( script: 'dir env:', label: 'checking env variables')
                                                },
                                                toxEnv: "py${pythonVersion}".replace('.',''),
                                            ]
                                        )
                                    }
                                    windowsPackages["Test Python ${pythonVersion}: wheel Windows"] = {
                                        devpi.testDevpiPackage(
                                            agent: [
                                                dockerfile: [
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL',
                                                    label: 'windows && docker && devpi-access'
                                                ]
                                            ],
                                            retries: 3,
                                            devpi: [
                                                index: DEVPI_CONFIG.stagingIndex,
                                                server: DEVPI_CONFIG.server,
                                                credentialsId: DEVPI_CONFIG.credentialsId,
                                            ],
                                            package:[
                                                name: props.Name,
                                                version: props.Version,
                                                selector: 'whl'
                                            ],
                                            test:[
                                                setup: {
                                                    powershell( script: 'dir env:', label: 'checking env variables')
                                                },
                                                toxEnv: "py${pythonVersion}".replace('.',''),
                                            ]
                                        )
                                    }
                                }
                            }
                            def linuxPackages = [:]
                            SUPPORTED_LINUX_VERSIONS.each{pythonVersion ->
                                def architectures = []
                                if(params.INCLUDE_LINUX_X86_64 == true){
                                    architectures.add("x86_64")
                                }
                                architectures.each{ processorArchitecture ->
                                    linuxPackages["Test Python ${pythonVersion}: sdist Linux ${processorArchitecture}"] = {
                                        devpi.testDevpiPackage(
                                            agent: [
                                                dockerfile: [
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip',
                                                    label: "linux && docker && devpi-access && ${processorArchitecture}",
                                                    args: '-v pipcache_hathizip:/.cache/pip'
                                                ]
                                            ],
                                            retries: 3,
                                            devpi: [
                                                index: DEVPI_CONFIG.stagingIndex,
                                                server: DEVPI_CONFIG.server,
                                                credentialsId: DEVPI_CONFIG.credentialsId,
                                            ],

                                            package:[
                                                name: props.Name,
                                                version: props.Version,
                                                selector: 'tar.gz'
                                            ],
                                            test:[
                                                setup: {
                                                    sh( script: 'printenv', label: 'checking env variables')
                                                },
                                                toxEnv: "py${pythonVersion}".replace('.',''),
                                            ]
                                        )
                                    }
                                    linuxPackages["Test Python ${pythonVersion}: wheel Linux"] = {
                                        devpi.testDevpiPackage(
                                            agent: [
                                                dockerfile: [
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip',
                                                    label: "linux && docker && devpi-access && ${processorArchitecture}",
                                                    args: '-v pipcache_hathizip:/.cache/pip'
                                                ]
                                            ],
                                            retries: 3,
                                            devpi: [
                                                index: DEVPI_CONFIG.stagingIndex,
                                                server: DEVPI_CONFIG.server,
                                                credentialsId: DEVPI_CONFIG.credentialsId,
                                            ],
                                            package:[
                                                name: props.Name,
                                                version: props.Version,
                                                selector: 'whl'
                                            ],
                                            test:[
                                                toxEnv: "py${pythonVersion}".replace('.',''),
                                            ]
                                        )
                                    }
                                }
                            }
                            parallel(macPackages + windowsPackages + linuxPackages)
                        }
                    }
                }
                stage('Deploy to DevPi Production') {
                    when {
                        allOf{
                            equals expected: true, actual: params.DEPLOY_DEVPI_PRODUCTION
                            anyOf {
                                equals expected: 'master', actual: env.BRANCH_NAME
                                tag '*'
                            }
                        }
                        beforeAgent true
                        beforeInput true
                    }
                    options{
                        timeout(time: 1, unit: 'DAYS')
                    }
                    agent {
                        dockerfile {
                            additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip'
                            filename 'ci/docker/python/linux/tox/Dockerfile'
                            label 'linux && docker && devpi-access'
                        }
                    }
                    input {
                        message 'Release to DevPi Production?'
                    }
                    steps {
                        script{
                            devpi.pushPackageToIndex(
                                pkgName: props.Name,
                                pkgVersion: props.Version,
                                server: DEVPI_CONFIG.server,
                                indexSource: DEVPI_CONFIG.stagingIndex,
                                indexDestination: 'production/release',
                                credentialsId: DEVPI_CONFIG.credentialsId
                            )
                        }
                    }
                }
            }
            post{
                success{
                    node('linux && docker && devpi-access') {
                        script{
                            if (!env.TAG_NAME?.trim()){
                                docker.build("hathizip:devpi","-f ci/docker/python/linux/tox/Dockerfile --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL .").inside{
                                    devpi.pushPackageToIndex(
                                            pkgName: props.Name,
                                            pkgVersion: props.Version,
                                            server: DEVPI_CONFIG.server,
                                            indexSource: DEVPI_CONFIG.stagingIndex,
                                            indexDestination: "DS_Jenkins/${env.BRANCH_NAME}",
                                            credentialsId: DEVPI_CONFIG.credentialsId
                                        )
                                }
                            }
                        }
                    }
                }
                cleanup{
                    node('linux && docker && devpi-access') {
                       script{
                            docker.build('hathizip:devpi','-f ci/docker/python/linux/tox/Dockerfile --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL .').inside{
                                devpi.removePackage(
                                    pkgName: props.Name,
                                    pkgVersion: props.Version,
                                    index: DEVPI_CONFIG.stagingIndex,
                                    server: DEVPI_CONFIG.server,
                                    credentialsId: DEVPI_CONFIG.credentialsId,
                                )
                            }
                       }
                    }
                }
            }
        }
        stage('Deploy'){
            parallel{
                stage('Deploy to pypi') {
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/jenkins/Dockerfile'
                            label 'linux && docker && x86'
                            additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST --build-arg PYTHON_VERSION=3.11'
                        }
                    }
                    when{
                        equals expected: true, actual: params.DEPLOY_PYPI
                        beforeAgent true
                        beforeInput true
                    }
                    options{
                        retry(3)
                    }
                    input {
                        message 'Upload to pypi server?'
                        parameters {
                            choice(
                                choices: getPypiConfig(),
                                description: 'Url to the pypi index to upload python packages.',
                                name: 'SERVER_URL'
                            )
                        }
                    }
                    steps{
                        unstash 'dist'
                        script{
                            def pypi = fileLoader.fromGit(
                                    'pypi',
                                    'https://github.com/UIUCLibrary/jenkins_helper_scripts.git',
                                    '2',
                                    null,
                                    ''
                                )
                            pypi.pypiUpload(
                                credentialsId: 'jenkins-nexus',
                                repositoryUrl: SERVER_URL,
                                glob: 'dist/*'
                                )
                        }
                    }
                    post{
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                        [pattern: 'dist/', type: 'INCLUDE']
                                    ]
                            )
                        }
                    }
                }
                stage('Deploy Online Documentation') {
                    when{
                        equals expected: true, actual: params.DEPLOY_DOCS
                        beforeAgent true
                        beforeInput true
                    }

                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/jenkins/Dockerfile'
                            label 'linux && docker && x86'
                            additionalBuildArgs '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST --build-arg PYTHON_VERSION=3.11'
                        }
                    }
                    options{
                        timeout(time: 1, unit: 'DAYS')
                    }
                    input {
                        message 'Update project documentation?'
                    }
                    steps{
                        unstash 'DOCS_ARCHIVE'
                        withCredentials([usernamePassword(credentialsId: 'dccdocs-server', passwordVariable: 'docsPassword', usernameVariable: 'docsUsername')]) {
                            sh 'python utils/upload_docs.py --username=$docsUsername --password=$docsPassword --subroute=hathi_zip build/docs/html apache-ns.library.illinois.edu'
                        }
                    }
                    post{
                        cleanup{
                            cleanWs(
                                    deleteDirs: true,
                                    patterns: [
                                        [pattern: 'build/', type: 'INCLUDE'],
                                        [pattern: 'dist/', type: 'INCLUDE'],
                                        ]
                                )
                        }
                    }
                }
            }
        }
    }
}
