// ============================================================================


def getPypiConfig() {
    node(){
        configFileProvider([configFile(fileId: 'pypi_config', variable: 'CONFIG_FILE')]) {
            def config = readJSON( file: CONFIG_FILE)
            return config['deployment']['indexes']
        }
    }
}

def get_sonarqube_unresolved_issues(report_task_file){
    script{

        def props = readProperties  file: '.scannerwork/report-task.txt'
        def response = httpRequest url : props['serverUrl'] + '/api/issues/search?componentKeys=' + props['projectKey'] + '&resolved=no'
        def outstandingIssues = readJSON text: response.content
        return outstandingIssues
    }
}


// ****************************************************************************

def call() {
    library(
        identifier: 'JenkinsPythonHelperLibrary@2024.12.0',
        retriever: modernSCM(
            [
                $class: 'GitSCMSource',
                remote: 'https://github.com/UIUCLibrary/JenkinsPythonHelperLibrary.git'
            ]
        )
    )
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
            booleanParam(name: 'INCLUDE_LINUX-ARM64', defaultValue: false, description: 'Include ARM architecture for Linux')
            booleanParam(name: 'INCLUDE_LINUX-X86_64', defaultValue: true, description: 'Include x86_64 architecture for Linux')
            booleanParam(name: 'INCLUDE_MACOS-ARM64', defaultValue: false, description: 'Include ARM(m1) architecture for Mac')
            booleanParam(name: 'INCLUDE_MACOS-X86_64', defaultValue: false, description: 'Include x86_64 architecture for Mac')
            booleanParam(name: 'INCLUDE_WINDOWS-X86_64', defaultValue: false, description: 'Include x86_64 architecture for Windows')
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
                            docker{
                                image 'python'
                                label 'docker && linux'
                                args '--mount source=python-tmp-hathizip,target=/tmp'
                            }
                        }
                        environment{
                            PIP_CACHE_DIR='/tmp/pipcache'
                            UV_INDEX_STRATEGY='unsafe-best-match'
                            UV_TOOL_DIR='/tmp/uvtools'
                            UV_PYTHON_INSTALL_DIR='/tmp/uvpython'
                            UV_CACHE_DIR='/tmp/uvcache'
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
                            stage('Building Sphinx Documentation'){
                                steps {
                                    catchError(buildResult: 'UNSTABLE', message: 'Building documentation produced an error or a warning', stageResult: 'UNSTABLE') {
                                        sh(
                                            label: 'Building docs',
                                            script: '''python3 -m venv venv
                                                       trap "rm -rf venv" EXIT
                                                       venv/bin/pip install --disable-pip-version-check uv
                                                       mkdir -p logs
                                                       venv/bin/uvx --python 3.12 --from sphinx sphinx-build docs/source build/docs/html -d build/docs/.doctrees -v -w logs/build_sphinx.log -W --keep-going
                                                       '''
                                        )
                                    }
                                }
                                post{
                                    always {
                                        recordIssues(tools: [sphinxBuild(name: 'Sphinx Documentation Build', pattern: 'logs/build_sphinx.log')])
                                        archiveArtifacts artifacts: 'logs/build_sphinx.log'
                                        script{
                                            def props = readTOML( file: 'pyproject.toml')['project']
                                            zip archive: true, dir: 'build/docs/html', glob: '', zipFile: "dist/${props.name}-${props.version}.doc.zip"
                                        }
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
                                environment{
                                    PIP_CACHE_DIR='/tmp/pipcache'
                                    UV_INDEX_STRATEGY='unsafe-best-match'
                                    UV_TOOL_DIR='/tmp/uvtools'
                                    UV_PYTHON_INSTALL_DIR='/tmp/uvpython'
                                    UV_CACHE_DIR='/tmp/uvcache'
                                }
                                agent {
                                    docker{
                                        image 'python'
                                        label 'docker && linux && x86_64' // needed for pysonar-scanner which is x86_64 only as of 0.2.0.520
                                        args '--mount source=python-tmp-hathizip,target=/tmp'
                                    }
                                }
                                stages{
                                    stage('Run Test'){
                                        stages{
                                            stage('Setup Testing Environment'){
                                                steps{
                                                    discoverGitReferenceBuild()
                                                    mineRepository()
                                                    sh(
                                                        label: 'Create virtual environment',
                                                        script: '''python3 -m venv bootstrap_uv
                                                                   bootstrap_uv/bin/pip install --disable-pip-version-check uv
                                                                   bootstrap_uv/bin/uv venv --python 3.12  venv
                                                                   . ./venv/bin/activate
                                                                   bootstrap_uv/bin/uv pip install uv
                                                                   rm -rf bootstrap_uv
                                                                   uv pip install -r requirements-dev.txt
                                                                   '''
                                                               )
                                                    sh(
                                                        label: 'Install package in development mode',
                                                        script: '''. ./venv/bin/activate
                                                                   uv pip install -e .
                                                                '''
                                                        )
                                                }
                                            }
                                            stage('Run Tests'){
                                                parallel{
                                                    stage('PyTest'){
                                                        steps{
                                                            sh(label: 'Running pytest',
                                                                script: '''. ./venv/bin/activate
                                                                           coverage run --parallel-mode --source=hathizip -m pytest --junitxml=./reports/tests/pytest/pytest-junit.xml
                                                                           '''
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
                                                                    script: '''. ./venv/bin/activate
                                                                               pylint hathizip -r n --msg-template="{path}:{line}: [{msg_id}({symbol}), {obj}] {msg}" > reports/pylint.txt
                                                                            '''
                                                                )
                                                            }
                                                            sh(
                                                                script: '''. ./venv/bin/activate
                                                                           pylint hathizip -r n --msg-template="{path}:{module}:{line}: [{msg_id}({symbol}), {obj}] {msg}" | tee reports/pylint_issues.txt
                                                                           ''',
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
                                                            sh '''. ./venv/bin/activate
                                                                  coverage run --parallel-mode --source=hathizip -m sphinx -b doctest docs/source build/docs -d build/docs/doctrees -v
                                                               '''
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
                                                                        script: '''. ./venv/bin/activate
                                                                                   mypy -p hathizip --html-report reports/mypy/mypy_html
                                                                                '''
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
                                                                   script: '''. ./venv/bin/activate
                                                                              flake8 hathizip --tee --output-file=logs/flake8.log
                                                                           '''
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
                                                   script: '''. ./venv/bin/activate
                                                              coverage combine
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
                                        environment{
                                            VERSION="${readTOML( file: 'pyproject.toml')['project'].version}"
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
                                            beforeOptions true
                                        }
                                        steps{
                                            script{
                                                withSonarQubeEnv(installationName:'sonarcloud', credentialsId: params.SONARCLOUD_TOKEN) {
                                                    def sourceInstruction
                                                    if (env.CHANGE_ID){
                                                        sourceInstruction = '-Dsonar.pullrequest.key=$CHANGE_ID -Dsonar.pullrequest.base=$BRANCH_NAME'
                                                    } else{
                                                        sourceInstruction = '-Dsonar.branch.name=$BRANCH_NAME'
                                                    }
                                                    sh(
                                                        label: 'Running Sonar Scanner',
                                                        script: """. ./venv/bin/activate
                                                                    uvx pysonar-scanner -Dsonar.projectVersion=$VERSION -Dsonar.buildString=\"$BUILD_TAG\" ${sourceInstruction}
                                                                """
                                                    )
                                                }
                                                timeout(time: 1, unit: 'HOURS') {
                                                    def sonarqube_result = waitForQualityGate(abortPipeline: false)
                                                    if (sonarqube_result.status != 'OK') {
                                                        unstable "SonarQube quality gate: ${sonarqube_result.status}"
                                                    }
                                                    def outstandingIssues = get_sonarqube_unresolved_issues('.scannerwork/report-task.txt')
                                                    writeJSON file: 'reports/sonar-report.json', json: outstandingIssues
                                                }
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
                                                [pattern: 'venv/', type: 'INCLUDE'],
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
                        parallel{
                            stage('Linux'){
                                when{
                                    expression {return nodesByLabel('linux && docker && x86').size() > 0}
                                }
                                environment{
                                    PIP_CACHE_DIR='/tmp/pipcache'
                                    UV_INDEX_STRATEGY='unsafe-best-match'
                                    UV_TOOL_DIR='/tmp/uvtools'
                                    UV_PYTHON_INSTALL_DIR='/tmp/uvpython'
                                    UV_CACHE_DIR='/tmp/uvcache'
                                }
                                steps{
                                    script{
                                        def envs = []
                                        node('docker && linux'){
                                            docker.image('python').inside('--mount source=python-tmp-hathizip,target=/tmp'){
                                                try{
                                                    checkout scm
                                                    sh(script: 'python3 -m venv venv && venv/bin/pip install --disable-pip-version-check uv')
                                                    envs = sh(
                                                        label: 'Get tox environments',
                                                        script: './venv/bin/uvx --quiet --with tox-uv tox list -d --no-desc',
                                                        returnStdout: true,
                                                    ).trim().split('\n')
                                                } finally{
                                                    cleanWs(
                                                        patterns: [
                                                            [pattern: 'venv/', type: 'INCLUDE'],
                                                            [pattern: '.tox', type: 'INCLUDE'],
                                                            [pattern: '**/__pycache__/', type: 'INCLUDE'],
                                                        ]
                                                    )
                                                }
                                            }
                                        }
                                        parallel(
                                            envs.collectEntries{toxEnv ->
                                                def version = toxEnv.replaceAll(/py(\d)(\d+)/, '$1.$2')
                                                [
                                                    "Tox Environment: ${toxEnv}",
                                                    {
                                                        node('docker && linux'){
                                                            docker.image('python').inside('--mount source=python-tmp-galatea,target=/tmp'){
                                                                checkout scm
                                                                try{
                                                                    sh( label: 'Running Tox',
                                                                        script: """python3 -m venv venv
                                                                                   trap "rm -rf venv" EXIT
                                                                                   venv/bin/pip install --disable-pip-version-check uv
                                                                                   venv/bin/uv python install cpython-${version}
                                                                                   venv/bin/uvx -p ${version} --with tox-uv tox run -e ${toxEnv}
                                                                                """
                                                                        )
                                                                } catch(e) {
                                                                    sh(script: '''. ./venv/bin/activate
                                                                          uv python list
                                                                          '''
                                                                            )
                                                                    throw e
                                                                } finally{
                                                                    cleanWs(
                                                                        patterns: [
                                                                            [pattern: 'venv/', type: 'INCLUDE'],
                                                                            [pattern: '.tox', type: 'INCLUDE'],
                                                                            [pattern: '**/__pycache__/', type: 'INCLUDE'],
                                                                        ]
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                ]
                                            }
                                        )
                                    }
                                }
                            }
                            stage('Windows'){
                                when{
                                    expression {return nodesByLabel('windows && docker && x86').size() > 0}
                                }
                                environment{
                                    UV_INDEX_STRATEGY='unsafe-best-match'
                                    PIP_CACHE_DIR='C:\\Users\\ContainerUser\\Documents\\pipcache'
                                    UV_TOOL_DIR='C:\\Users\\ContainerUser\\Documents\\uvtools'
                                    UV_PYTHON_INSTALL_DIR='C:\\Users\\ContainerUser\\Documents\\uvpython'
                                    UV_CACHE_DIR='C:\\Users\\ContainerUser\\Documents\\uvcache'
                                }
                                steps{
                                    script{
                                        def envs = []
                                        node('docker && windows'){
                                            docker.image('python').inside("--mount type=volume,source=uv_python_install_dir,target=${env.UV_PYTHON_INSTALL_DIR}"){
                                                try{
                                                    checkout scm
                                                    bat(script: 'python -m venv venv && venv\\Scripts\\pip install --disable-pip-version-check uv')
                                                    envs = bat(
                                                        label: 'Get tox environments',
                                                        script: '@.\\venv\\Scripts\\uvx --quiet --with tox-uv tox list -d --no-desc',
                                                        returnStdout: true,
                                                    ).trim().split('\r\n')
                                                } finally{
                                                    cleanWs(
                                                        patterns: [
                                                            [pattern: 'venv/', type: 'INCLUDE'],
                                                            [pattern: '.tox', type: 'INCLUDE'],
                                                            [pattern: '**/__pycache__/', type: 'INCLUDE'],
                                                        ]
                                                    )
                                                }
                                            }
                                        }
                                        parallel(
                                            envs.collectEntries{toxEnv ->
                                                def version = toxEnv.replaceAll(/py(\d)(\d+)/, '$1.$2')
                                                [
                                                    "Tox Environment: ${toxEnv}",
                                                    {
                                                        node('docker && windows'){
                                                            docker.image('python').inside("--mount type=volume,source=uv_python_install_dir,target=${env.UV_PYTHON_INSTALL_DIR}"){
                                                                checkout scm
                                                                try{
                                                                    bat(label: 'Install uv',
                                                                        script: 'python -m venv venv && venv\\Scripts\\pip install --disable-pip-version-check uv'
                                                                    )
                                                                    retry(3){
                                                                        bat(label: 'Running Tox',
                                                                            script: """python -m venv venv && venv\\Scripts\\pip install --disable-pip-version-check uv
                                                                                    venv\\Scripts\\uv python install cpython-${version}
                                                                                    venv\\Scripts\\uvx -p ${version} --with tox-uv tox run -e ${toxEnv}
                                                                                    rmdir /S /Q .tox
                                                                                    rmdir /S /Q venv
                                                                                """
                                                                        )
                                                                    }
                                                                } finally{
                                                                    cleanWs(
                                                                        patterns: [
                                                                            [pattern: 'venv/', type: 'INCLUDE'],
                                                                            [pattern: '.tox', type: 'INCLUDE'],
                                                                            [pattern: '**/__pycache__/', type: 'INCLUDE'],
                                                                        ]
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                ]
                                            }
                                        )
                                    }
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
                                args '--mount source=python-tmp-hathizip,target=/tmp'
                              }
                        }
                        environment{
                            PIP_CACHE_DIR='/tmp/pipcache'
                            UV_INDEX_STRATEGY='unsafe-best-match'
                            UV_CACHE_DIR='/tmp/uvcache'
                        }
                        options {
                            retry(2)
                        }
                        steps{
                            sh(
                                label: 'Package',
                                script: '''python3 -m venv venv && venv/bin/pip install --disable-pip-version-check uv
                                           trap "rm -rf venv" EXIT
                                           venv/bin/uv build
                                        '''
                            )
                        }
                        post{
                            success{
                                archiveArtifacts artifacts: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', fingerprint: true
                                stash includes: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', name: 'PYTHON_PACKAGES'
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
                    stage('Testing Packages'){
                        when{
                            equals expected: true, actual: params.TEST_PACKAGES
                        }
                        environment{
                            UV_INDEX_STRATEGY='unsafe-best-match'
                        }
                        steps{
                            customMatrix(
                                axes: [
                                    [
                                        name: 'PYTHON_VERSION',
                                        values: ['3.9', '3.10', '3.11', '3.12','3.13']
                                    ],
                                    [
                                        name: 'OS',
                                        values: ['linux','macos','windows']
                                    ],
                                    [
                                        name: 'ARCHITECTURE',
                                        values: ['x86_64', 'arm64']
                                    ],
                                    [
                                        name: 'PACKAGE_TYPE',
                                        values: ['wheel', 'sdist'],
                                    ]
                                ],
                                excludes: [
                                    [
                                        [
                                            name: 'OS',
                                            values: 'windows'
                                        ],
                                        [
                                            name: 'ARCHITECTURE',
                                            values: 'arm64',
                                        ]
                                    ]
                                ],
                                when: {entry -> "INCLUDE_${entry.OS}-${entry.ARCHITECTURE}".toUpperCase() && params["INCLUDE_${entry.OS}-${entry.ARCHITECTURE}".toUpperCase()]},
                                stages: [
                                    { entry ->
                                        stage('Test Package') {
                                            node("${entry.OS} && ${entry.ARCHITECTURE} ${['linux', 'windows'].contains(entry.OS) ? '&& docker': ''}"){
                                                try{
                                                    checkout scm
                                                    unstash 'PYTHON_PACKAGES'
                                                    if(['linux', 'windows'].contains(entry.OS) && params.containsKey("INCLUDE_${entry.OS}-${entry.ARCHITECTURE}".toUpperCase()) && params["INCLUDE_${entry.OS}-${entry.ARCHITECTURE}".toUpperCase()]){
                                                        docker.image('python').inside(isUnix() ? '': "--mount type=volume,source=uv_python_install_dir,target=C:\\Users\\ContainerUser\\Documents\\uvpython"){
                                                             if(isUnix()){
                                                                withEnv([
                                                                    'PIP_CACHE_DIR=/tmp/pipcache',
                                                                    'UV_TOOL_DIR=/tmp/uvtools',
                                                                    'UV_PYTHON_INSTALL_DIR=/tmp/uvpython',
                                                                    'UV_CACHE_DIR=/tmp/uvcache',
                                                                ]){
                                                                     sh(
                                                                        label: 'Testing with tox',
                                                                        script: """python3 -m venv venv
                                                                                   ./venv/bin/pip install --disable-pip-version-check uv
                                                                                   ./venv/bin/uv python install cpython-${entry.PYTHON_VERSION}
                                                                                   ./venv/bin/uvx --with tox-uv tox --installpkg ${findFiles(glob: entry.PACKAGE_TYPE == 'wheel' ? 'dist/*.whl' : 'dist/*.tar.gz')[0].path} -e py${entry.PYTHON_VERSION.replace('.', '')}
                                                                                """
                                                                    )
                                                                }
                                                             } else {
                                                                withEnv([
                                                                    'PIP_CACHE_DIR=C:\\Users\\ContainerUser\\Documents\\pipcache',
                                                                    'UV_TOOL_DIR=C:\\Users\\ContainerUser\\Documents\\uvtools',
                                                                    'UV_PYTHON_INSTALL_DIR=C:\\Users\\ContainerUser\\Documents\\uvpython',
                                                                    'UV_CACHE_DIR=C:\\Users\\ContainerUser\\Documents\\uvcache',
                                                                ]){
                                                                    bat(
                                                                        label: 'Testing with tox',
                                                                        script: """python -m venv venv
                                                                                   .\\venv\\Scripts\\pip install --disable-pip-version-check uv
                                                                                   .\\venv\\Scripts\\uv python install cpython-${entry.PYTHON_VERSION}
                                                                                   .\\venv\\Scripts\\uvx --with tox-uv tox --installpkg ${findFiles(glob: entry.PACKAGE_TYPE == 'wheel' ? 'dist/*.whl' : 'dist/*.tar.gz')[0].path} -e py${entry.PYTHON_VERSION.replace('.', '')}
                                                                                """
                                                                    )
                                                                }
                                                             }
                                                        }
                                                    } else {
                                                        if(isUnix()){
                                                            sh(
                                                                label: 'Testing with tox',
                                                                script: """python3 -m venv venv
                                                                           ./venv/bin/pip install --disable-pip-version-check uv
                                                                           ./venv/bin/uvx --with tox-uv tox --installpkg ${findFiles(glob: entry.PACKAGE_TYPE == 'wheel' ? 'dist/*.whl' : 'dist/*.tar.gz')[0].path} -e py${entry.PYTHON_VERSION.replace('.', '')}
                                                                        """
                                                            )
                                                        } else {
                                                            bat(
                                                                label: 'Testing with tox',
                                                                script: """python -m venv venv
                                                                           .\\venv\\Scripts\\pip install --disable-pip-version-check uv
                                                                           .\\venv\\Scripts\\uv python install cpython-${entry.PYTHON_VERSION}
                                                                           .\\venv\\Scripts\\uvx --with tox-uv tox --installpkg ${findFiles(glob: entry.PACKAGE_TYPE == 'wheel' ? 'dist/*.whl' : 'dist/*.tar.gz')[0].path} -e py${entry.PYTHON_VERSION.replace('.', '')}
                                                                        """
                                                            )
                                                        }
                                                    }
                                                } finally{
                                                    if(isUnix()){
                                                        sh "${tool(name: 'Default', type: 'git')} clean -dfx"
                                                    } else {
                                                        bat "${tool(name: 'Default', type: 'git')} clean -dfx"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                ]
                            )
                        }
                    }
                }
            }
            stage('Deploy'){
                parallel{
                    stage('Deploy to pypi') {
                        environment{
                            PIP_CACHE_DIR='/tmp/pipcache'
                            UV_INDEX_STRATEGY='unsafe-best-match'
                            UV_TOOL_DIR='/tmp/uvtools'
                            UV_PYTHON_INSTALL_DIR='/tmp/uvpython'
                            UV_CACHE_DIR='/tmp/uvcache'
                        }
                        agent {
                            docker{
                                image 'python'
                                label 'docker && linux'
                                args '--mount source=python-tmp-hathizip,target=/tmp'
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
                            unstash 'PYTHON_PACKAGES'
                            withEnv(
                                [
                                    "TWINE_REPOSITORY_URL=${SERVER_URL}",
                                ]
                            ){
                                withCredentials(
                                    [
                                        usernamePassword(
                                            credentialsId: 'jenkins-nexus',
                                            passwordVariable: 'TWINE_PASSWORD',
                                            usernameVariable: 'TWINE_USERNAME'
                                        )
                                    ]){
                                        sh(
                                            label: 'Uploading to pypi',
                                            script: '''python3 -m venv venv
                                                       trap "rm -rf venv" EXIT
                                                       ./venv/bin/pip install --disable-pip-version-check uv
                                                       UV_INDEX_STRATEGY=unsafe-best-match ./venv/bin/uvx twine --installpkg upload --disable-progress-bar --non-interactive dist/*
                                                    '''
                                        )
                                }
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
                        environment{
                            PIP_CACHE_DIR='/tmp/pipcache'
                            UV_INDEX_STRATEGY='unsafe-best-match'
                            UV_TOOL_DIR='/tmp/uvtools'
                            UV_PYTHON_INSTALL_DIR='/tmp/uvpython'
                            UV_CACHE_DIR='/tmp/uvcache'
                        }
                        agent {
                            docker{
                                image 'python'
                                label 'docker && linux'
                                args '--mount source=python-tmp-hathizip,target=/tmp'
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
}