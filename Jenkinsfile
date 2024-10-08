library identifier: 'JenkinsPythonHelperLibrary@2024.1.2', retriever: modernSCM(
  [$class: 'GitSCMSource',
   remote: 'https://github.com/UIUCLibrary/JenkinsPythonHelperLibrary.git',
   ])

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


def getPypiConfig() {
    node(){
        configFileProvider([configFile(fileId: 'pypi_config', variable: 'CONFIG_FILE')]) {
            def config = readJSON( file: CONFIG_FILE)
            return config['deployment']['indexes']
        }
    }
}

// ****************************************************************************


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
                                            recordCoverage(tools: [[parser: 'COBERTURA', pattern: 'reports/coverage.xml']])
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
                                            def sonarqube = load 'ci/jenkins/scripts/sonarqube.groovy'
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
                stage('Tox'){
                    when{
                        equals expected: true, actual: params.TEST_RUN_TOX
                    }
                    options {
                        lock(env.JOB_URL)
                    }
                    steps {
                        script{
                            def windowsJobs
                            def linuxJobs
                            stage('Scanning Tox Environments'){
                                parallel(
                                    'Linux':{
                                        linuxJobs = getToxTestsParallel(
                                                envNamePrefix: 'Tox Linux',
                                                label: 'linux && docker',
                                                dockerfile: 'ci/docker/python/linux/tox/Dockerfile',
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip --build-arg UV_CACHE_DIR=/.cache/uv',
                                                dockerRunArgs: '-v pipcache_hathizip:/.cache/pip',
                                                retry: 2
                                            )
                                    },
                                    'Windows':{
                                        windowsJobs = getToxTestsParallel(
                                                envNamePrefix: 'Tox Windows',
                                                label: 'windows && docker',
                                                dockerfile: 'ci/docker/python/windows/tox/Dockerfile',
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg CHOCOLATEY_SOURCE --build-arg chocolateyVersion --build-arg PIP_DOWNLOAD_CACHE=c:/users/containeradministrator/appdata/local/pip --build-arg UV_CACHE_DIR=c:/users/containeradministrator/appdata/local/uv',
                                                dockerRunArgs: '-v pipcache_hathizip:c:/users/containeradministrator/appdata/local/pip -v uvcache_hathizip:c:/users/containeradministrator/appdata/local/uv',
                                                retry: 2

                                            )
                                    },
                                    failFast: true
                                )
                            }
                            stage('Run Tox'){
                                parallel(windowsJobs + linuxJobs)
                            }
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
                }
                beforeAgent true
            }
            stages{
                stage('Source and Wheel Formats'){
                    agent {
                        docker{
                            image 'python'
                            label 'linux && docker'
                          }
                    }
                    options {
                        retry(2)
                    }
                    steps{
                        timeout(5){
                            withEnv(['PIP_NO_CACHE_DIR=off']) {
                                sh(label: 'Build Python Package',
                                   script: '''python -m venv venv --upgrade-deps
                                              venv/bin/pip install build
                                              venv/bin/python -m build .
                                              '''
                                    )
                            }
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
                                    [pattern: '*.egg-info/', type: 'INCLUDE'],
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
                            def windowsTests = [:]
                            if(params.INCLUDE_WINDOWS_X86_64 == true){
                                SUPPORTED_WINDOWS_VERSIONS.each{ pythonVersion ->
                                    windowsTests["Windows - Python ${pythonVersion}: sdist"] = {
                                        testPythonPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg CHOCOLATEY_SOURCE --build-arg chocolateyVersion --build-arg PIP_DOWNLOAD_CACHE=c:/users/containeradministrator/appdata/local/pip --build-arg UV_CACHE_DIR=c:/users/containeradministrator/appdata/local/uv',
                                                    args: '-v pipcache_hathizip:c:/users/containeradministrator/appdata/local/pip -v uvcache_hathizip:c:/users/containeradministrator/appdata/local/uv'
                                                ]
                                            ],
                                            retries: 3,
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                            },
                                            testCommand: {
                                                findFiles(glob: 'dist/*.tar.gz,dist/*.zip').each{
                                                    bat(label: 'Running Tox', script: "tox --workdir %TEMP%\\tox --installpkg ${it.path} -e py${pythonVersion.replace('.', '')} -v")
                                                }
                                            },
                                        )
                                    }
                                    windowsTests["Windows - Python ${pythonVersion}: wheel"] = {
                                        testPythonPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg CHOCOLATEY_SOURCE --build-arg chocolateyVersion --build-arg PIP_DOWNLOAD_CACHE=c:/users/containeradministrator/appdata/local/pip --build-arg UV_CACHE_DIR=c:/users/containeradministrator/appdata/local/uv',
                                                    args: '-v pipcache_hathizip:c:/users/containeradministrator/appdata/local/pip -v uvcache_hathizip:c:/users/containeradministrator/appdata/local/uv'
                                                ]
                                            ],
                                            retries: 3,
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                            },
                                            testCommand: {
                                                 findFiles(glob: 'dist/*.whl').each{
                                                     powershell(label: 'Running Tox', script: "tox --installpkg ${it.path} --workdir \$env:TEMP\\tox  -e py${pythonVersion.replace('.', '')}")
                                                 }
                                            },
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
                                        testPythonPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: "linux && docker && ${processorArchitecture}",
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip --build-arg UV_CACHE_DIR=/.cache/uv',
                                                    args: '-v pipcache_hathizip:/.cache/pip',
                                                ]
                                            ],
                                            retries: 3,
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                            },
                                            testCommand: {
                                                findFiles(glob: 'dist/*.tar.gz').each{
                                                    sh(
                                                        label: 'Running Tox',
                                                        script: "tox --installpkg ${it.path} --workdir /tmp/tox -e py${pythonVersion.replace('.', '')}"
                                                        )
                                                }
                                            },
                                        )
                                    }
                                    linuxTests["Linux ${processorArchitecture} - Python ${pythonVersion}-${processorArchitecture}: wheel"] = {
                                        testPythonPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: "linux && docker && ${processorArchitecture}",
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg PIP_DOWNLOAD_CACHE=/.cache/pip --build-arg UV_CACHE_DIR=/.cache/uv',
                                                    args: '-v pipcache_hathizip:/.cache/pip',
                                                ]
                                            ],
                                            retries: 3,
                                            testSetup: {
                                                sh( script: 'printenv', label: 'checking env variables')
                                                checkout scm
                                                unstash 'dist'
                                            },
                                            testCommand: {
                                                findFiles(glob: 'dist/*.tar.gz').each{
                                                    sh(
                                                        label: 'Running Tox',
                                                        script: "tox --installpkg ${it.path} --workdir /tmp/tox -e py${pythonVersion.replace('.', '')}"
                                                        )
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            def macTests = [:]
                            SUPPORTED_MAC_VERSIONS.each{ pythonVersion ->
                                def architectures = []
                                if(params.INCLUDE_MACOS_ARM == true){
                                    architectures.add('arm64')
                                }
                                if(params.INCLUDE_MACOS_X86_64 == true){
                                    architectures.add('x86_64')
                                }
                                architectures.each{ processorArchitecture ->
                                    if (nodesByLabel("python${pythonVersion} && mac && ${processorArchitecture} || emulated_${processorArchitecture}").size() > 0){
                                        macTests["Mac ${processorArchitecture} - Python ${pythonVersion}: sdist"] = {
                                            testPythonPkg(
                                                agent: [
                                                    label: "mac && python${pythonVersion} && ${processorArchitecture} || emulated_${processorArchitecture}",
                                                ],
                                                testSetup: {
                                                    checkout scm
                                                    unstash 'dist'
                                                },
                                                retries: 3,
                                                testCommand: {
                                                    withEnv(['UV_INDEX_STRATEGY=unsafe-best-match']) {
                                                        findFiles(glob: 'dist/*.tar.gz').each{
                                                            sh(label: 'Running Tox',
                                                               script: """arch -${processorArchitecture} python${pythonVersion} -m venv venv
                                                               ./venv/bin/python -m pip install --upgrade pip
                                                               ./venv/bin/pip install -r requirements/requirements_tox.txt
                                                               ./venv/bin/tox --installpkg ${it.path} -e py${pythonVersion.replace('.', '')}"""
                                                            )
                                                        }
                                                    }
                                                },
                                                post:[
                                                    cleanup: {
                                                        sh 'rm -r venv/'
                                                    }
                                                ]
                                            )
                                        }
                                        macTests["Mac ${processorArchitecture} - Python ${pythonVersion}: wheel"] = {
                                            testPythonPkg(
                                                agent: [
                                                    label: "mac && python${pythonVersion} && ${processorArchitecture} || emulated_${processorArchitecture}",
                                                ],
                                                testSetup: {
                                                    checkout scm
                                                    unstash 'dist'
                                                },
                                                retries: 3,
                                                testCommand: {
                                                    withEnv(['UV_INDEX_STRATEGY=unsafe-best-match']) {
                                                        findFiles(glob: 'dist/*.whl').each{
                                                            sh(label: 'Running Tox',
                                                               script: """arch -${processorArchitecture} python${pythonVersion} -m venv venv
                                                               ./venv/bin/python -m pip install --upgrade pip
                                                               ./venv/bin/pip install -r requirements/requirements_tox.txt
                                                               ./venv/bin/tox --installpkg ${it.path} -e py${pythonVersion.replace('.', '')}"""
                                                            )
                                                        }
                                                    }
                                                },
                                                post:[
                                                    cleanup: {
                                                        sh 'rm -r venv/'
                                                    }
                                                ]
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
                        pypiUpload(
                            credentialsId: 'jenkins-nexus',
                            repositoryUrl: SERVER_URL,
                            glob: 'dist/*'
                        )
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
