CONFIGURATIONS = [
    '3.6': [
        test_docker_image: "python:3.6-windowsservercore",
        tox_env: "py36"
        ],
    "3.7": [
        test_docker_image: "python:3.7",
        tox_env: "py37"
        ],
    "3.8": [
        test_docker_image: "python:3.8",
        tox_env: "py38"
        ],
    "3.9": [
        test_docker_image: "python:3.9",
        tox_env: "py39"
        ]
]

def DEFAULT_AGENT = [
    filename: 'ci/docker/python/linux/testing/Dockerfile',
    label: 'linux && docker',
    additionalBuildArgs: '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST'
]
def DOCKER_PLATFORM_BUILD_ARGS = [
    linux: '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)',
    windows: ''
]

def tox

def getDevPiStagingIndex(){

    if (env.TAG_NAME?.trim()){
        return 'tag_staging'
    } else{
        return "${env.BRANCH_NAME}_staging"
    }
}


node(){
    checkout scm
    tox = load("ci/jenkins/scripts/tox.groovy")
    devpi = load('ci/jenkins/scripts/devpi.groovy')
}
SONARQUBE_CREDENTIAL_ID = "sonartoken-hathizip"
DEVPI_STAGING_INDEX = "DS_Jenkins/${getDevPiStagingIndex()}"

defaultParameterValues = [
    USE_SONARQUBE: false
]

def startup(){
    def SONARQUBE_CREDENTIAL_ID = SONARQUBE_CREDENTIAL_ID
    parallel(
        [
            failFast: true,
            "Checking sonarqube Settings": {
                node(){
                    try{
                        withCredentials([string(credentialsId: SONARQUBE_CREDENTIAL_ID, variable: 'dddd')]) {
                            echo 'Found credentials for sonarqube'
                        }
                        defaultParameterValues.USE_SONARQUBE = true
                    } catch(e){
                        echo "Setting defaultValue for USE_SONARQUBE to false. Reason: ${e}"
                        defaultParameterValues.USE_SONARQUBE = false
                    }
                }
            },
            "Getting Distribution Info": {
                node('linux && docker') {
                    timeout(2){
                        ws{
                            checkout scm
                            try{
                                docker.image('python:3.8').inside {
                                    sh(
                                       label: "Running setup.py with dist_info",
                                       script: """python --version
                                                  python setup.py dist_info
                                               """
                                    )
                                    stash includes: "HathiZip.dist-info/**", name: 'DIST-INFO'
                                    archiveArtifacts artifacts: "HathiZip.dist-info/**"
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
    libraries {
      lib('PythonHelpers')
      lib('ds-utils')
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
//         TODO: set defaultValue to true
        booleanParam(name: 'RUN_CHECKS', defaultValue: false, description: "Run checks on code")
        booleanParam(name: 'USE_SONARQUBE', defaultValue: defaultParameterValues.USE_SONARQUBE, description: 'Send data test data to SonarQube')
        booleanParam(name: "TEST_RUN_TOX", defaultValue: false, description: "Run Tox Tests")
        booleanParam(name: 'TEST_PACKAGES', defaultValue: false, description: "Test packages")
        booleanParam(name: "TEST_PACKAGES_ON_MAC", defaultValue: false, description: "Test Python packages on Mac")
        booleanParam(name: "PACKAGE_CX_FREEZE", defaultValue: false, description: "Create a package with CX_Freeze")
//         TODO: set defaultValue to false
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: true, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: "DEPLOY_DEVPI_PRODUCTION", defaultValue: false, description: "Deploy to https://devpi.library.illinois.edu/production/release")
        booleanParam(name: "DEPLOY_ADD_TAG", defaultValue: false, description: "Tag commit to current version")
        booleanParam(name: "DEPLOY_SCCM", defaultValue: false, description: "Deploy to SCCM")
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")
    }
    stages {
        stage("Build"){
            agent {
                dockerfile {
                    filename DEFAULT_AGENT.filename
                    label DEFAULT_AGENT.label
                    additionalBuildArgs DEFAULT_AGENT.additionalBuildArgs
                }
            }
            stages{
                stage("Python Package"){
                    steps {
                        sh(script: """mkdir -p logs
                                      python setup.py build -b build | tee logs/build.log
                                      """
                        )
                    }
                    post{
                        always{
                            archiveArtifacts artifacts: "logs/build.log"
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/", type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE']
                                ]
                            )
                        }

                    }
                }
                stage("Building Sphinx Documentation"){
                    steps {
                        sh(
                            label: "Building docs on ${env.NODE_NAME}",
                            script: """mkdir -p logs
                                       python -m sphinx docs/source build/docs/html -d build/docs/.doctrees -v -w logs/build_sphinx.log
                                       """
                        )
                    }
                    post{
                        always {
                            recordIssues(tools: [sphinxBuild(name: 'Sphinx Documentation Build', pattern: 'logs/build_sphinx.log')])
                            archiveArtifacts artifacts: 'logs/build_sphinx.log'
                        }
                        success{
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'build/docs/html', reportFiles: 'index.html', reportName: 'Documentation', reportTitles: ''])
                            script{
                                def DOC_ZIP_FILENAME = "${props.Name}-${props.Version}.doc.zip"
                                zip archive: true, dir: "build/docs/html", glob: '', zipFile: "dist/${DOC_ZIP_FILENAME}"
                                stash includes: "dist/${DOC_ZIP_FILENAME},build/docs/html/**", name: 'DOCS_ARCHIVE'
                            }
                        }
                        failure{
                            echo "Failed to build Python package"
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/", type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE'],
                                    [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                ]
                            )
                        }
                    }
                }
            }
        }
        stage("Checks") {
            when{
                equals expected: true, actual: params.RUN_CHECKS
            }
            stages{
                stage("Code Quality"){
                    stages{
                        stage("Run test"){
                            agent {
                                dockerfile {
                                    filename DEFAULT_AGENT.filename
                                    label DEFAULT_AGENT.label
                                    additionalBuildArgs DEFAULT_AGENT.additionalBuildArgs
                                }
                            }
                            stages{
                                stage("Set up Tests"){
                                    steps{
                                        sh '''mkdir -p logs
                                              python setup.py build
                                              mkdir -p reports
                                              '''
                                    }
                                }
                                stage("Run Tests"){
                                    parallel{
                                        stage("PyTest"){
                                            steps{
                                                sh(label: "Running pytest",
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
                                        stage("Run Pylint Static Analysis") {
                                            steps{
                                                catchError(buildResult: 'SUCCESS', message: 'Pylint found issues', stageResult: 'UNSTABLE') {
                                                    sh(label: "Running pylint",
                                                        script: '''pylint hathizip -r n --msg-template="{path}:{line}: [{msg_id}({symbol}), {obj}] {msg}" > reports/pylint.txt
                                                                   '''

                                                    )
                                                }
                                                sh(
                                                    script: 'pylint hathizip -r n --msg-template="{path}:{module}:{line}: [{msg_id}({symbol}), {obj}] {msg}" | tee reports/pylint_issues.txt',
                                                    label: "Running pylint for sonarqube",
                                                    returnStatus: true
                                                )
                                            }
                                            post{
                                                always{
                                                    recordIssues(tools: [pyLint(pattern: 'reports/pylint.txt')])
                                                    stash includes: "reports/pylint_issues.txt,reports/pylint.txt", name: 'PYLINT_REPORT'
                                                }
                                            }
                                        }
                                        stage("Doctest"){
                                            steps{
                                                unstash "DOCS_ARCHIVE"
                                                sh 'coverage run --parallel-mode --source=hathizip -m sphinx -b doctest docs/source build/docs -d build/docs/doctrees -v'
                                            }
                                            post{
                                                failure{
                                                    sh "ls -R build/docs/"
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
                                        stage("MyPy"){
                                            steps{
                                                sh "mkdir -p reports/mypy && mkdir -p logs"
                                                catchError(buildResult: 'SUCCESS', message: 'mypy found some warnings', stageResult: 'UNSTABLE') {
                                                    sh(
                                                        script: "mypy -p hathizip --html-report ${WORKSPACE}/reports/mypy/mypy_html | tee logs/mypy.log"
                                                    )
                                                }
                                            }
                                            post{
                                                always {
                                                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/mypy/mypy_html', reportFiles: 'index.html', reportName: 'MyPy', reportTitles: ''])
                                                    recordIssues(tools: [myPy(name: 'MyPy', pattern: 'logs/mypy.log')])
                                                }

                                            }
                                        }
                                        stage("Run Flake8 Static Analysis") {
                                            steps{
                                                catchError(buildResult: 'SUCCESS', message: 'flake8 found some warnings', stageResult: 'UNSTABLE') {
                                                    sh(label: "Running flake8",
                                                       script: """flake8 hathizip --tee --output-file=logs/flake8.log
                                                                  """
                                                    )
                                                }
                                            }
                                            post {
                                                always {
                                                    stash includes: 'logs/flake8.log', name: 'FLAKE8_REPORT'
                                                    recordIssues(tools: [flake8(name: 'Flake8', pattern: 'logs/flake8.log')])
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
                                equals expected: true, actual: params.USE_SONARQUBE
                                beforeAgent true
                                beforeOptions true
                            }
                            steps{
                                script{
                                    def sonarqube
                                    node(){
                                        checkout scm
                                        sonarqube = load('ci/jenkins/scripts/sonarqube.groovy')
                                    }
                                    def stashes = [
                                        'COVERAGE_REPORT_DATA',
                                        'PYTEST_UNIT_TEST_RESULTS',
                                        'PYLINT_REPORT',
                                        'FLAKE8_REPORT'
                                    ]
                                    def sonarqubeConfig = [
                                        installationName: 'sonarcloud',
                                        credentialsId: SONARQUBE_CREDENTIAL_ID,
                                    ]
                                    def agent = [
                                            dockerfile: [
                                                filename: 'ci/docker/python/linux/testing/Dockerfile',
                                                label: 'linux && docker',
                                                additionalBuildArgs: '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL',
                                                args: '--mount source=sonar-cache-hathi_zip,target=/home/user/.sonar/cache',
                                            ]
                                        ]
                                    if (env.CHANGE_ID){
                                        sonarqube.submitToSonarcloud(
                                            agent: agent,
                                            reportStashes: stashes,
                                            artifactStash: 'sonarqube artifacts',
                                            sonarqube: sonarqubeConfig,
                                            pullRequest: [
                                                source: env.CHANGE_ID,
                                                destination: env.BRANCH_NAME,
                                            ],
                                            package: [
                                                version: props.Version,
                                                name: props.Name
                                            ],
                                        )
                                    } else {
                                        sonarqube.submitToSonarcloud(
                                            agent: agent,
                                            reportStashes: stashes,
                                            artifactStash: 'sonarqube artifacts',
                                            sonarqube: sonarqubeConfig,
                                            package: [
                                                version: props.Version,
                                                name: props.Name
                                            ]
                                        )
                                    }
                                }
                            }
                            post {
                                always{
                                    node(''){
                                        unstash 'sonarqube artifacts'
                                        recordIssues(tools: [sonarQube(pattern: 'reports/sonar-report.json')])
                                    }
                                }
                            }
                        }
                    }
                }
                stage("Run Tox"){
                    when{
                        equals expected: true, actual: params.TEST_RUN_TOX
                    }
                    steps {
                        script{
                            def windowsJobs
                            def linuxJobs
                            stage("Scanning Tox Environments"){
                                parallel(
                                    "Linux":{
                                        linuxJobs = tox.getToxTestsParallel(
                                                envNamePrefix: "Tox Linux",
                                                label: "linux && docker",
                                                dockerfile: "ci/docker/python/linux/tox/Dockerfile",
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                            )
                                    },
                                    "Windows":{
                                        windowsJobs = tox.getToxTestsParallel(
                                                envNamePrefix: "Tox Windows",
                                                label: 'windows && docker',
                                                dockerfile: "ci/docker/python/windows/tox/Dockerfile",
                                                dockerArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
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

        stage("Packaging") {
            stages{
                stage("Create"){
                    parallel {
                        stage("Source and Wheel formats"){
                            agent {
                                dockerfile {
                                    filename DEFAULT_AGENT.filename
                                    label DEFAULT_AGENT.label
                                    additionalBuildArgs DEFAULT_AGENT.additionalBuildArgs
                                }
                            }
                            steps{
                                timeout(5){
                                    sh "python setup.py sdist -d dist bdist_wheel -d dist"
                                }

                            }
                            post{
                                success{
                                    archiveArtifacts artifacts: "dist/*.whl,dist/*.tar.gz,dist/*.zip", fingerprint: true
                                    stash includes: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', name: "dist"
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
                        stage("Windows CX_Freeze MSI"){
                            agent {
                                dockerfile {
                                    filename 'ci/docker/python/windows/build/msvc/Dockerfile'
                                    label 'windows && docker'
                                }
                            }
                            when{
                                equals expected: true, actual: params.PACKAGE_CX_FREEZE
                                beforeAgent true
                            }
                            steps{
                                timeout(15){
                                    bat "python cx_setup.py bdist_msi --add-to-path=true -k --bdist-dir build/msi -d dist"
                                }
                            }
                            post{
                                success{
                                    stash includes: "dist/*.msi", name: "msi"
                                    archiveArtifacts artifacts: "dist/*.msi", fingerprint: true
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
                    }
                }
                stage("Testing"){
                    when{
                        equals expected: true, actual: params.TEST_PACKAGES
                        beforeAgent true
                    }
                    steps{
                        script{
                            def packages
                            node(){
                                checkout scm
                                packages = load "ci/jenkins/scripts/packaging.groovy"

                            }
                            def tests = [
                                    'Windows - Python 3.6: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                    
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: '3.6'
                                        )
                                    },
                                    'Windows - Python 3.7: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: '3.7'
                                        )
                                    },
                                    'Windows - Python 3.8: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: '3.8'
                                        )
                                    },
                                    'Windows - Python 3.9: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: '3.9'
                                        )
                                    },
                                    'Linux - Python 3.6: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz',
                                            stash: 'dist',
                                            pythonVersion: '3.6'
                                        )
                                    },
                                    'Linux - Python 3.7: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz',
                                            stash: 'dist',
                                            pythonVersion: '3.7'
                                        )
                                    },
                                    'Linux - Python 3.8: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz',
                                            stash: 'dist',
                                            pythonVersion: '3.8'
                                        )
                                    },
                                    'Linux - Python 3.9: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.tar.gz',
                                            stash: 'dist',
                                            pythonVersion: '3.9'
                                        )
                                    },
                                    'Windows - Python 3.6: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.6'
                                        )
                                    },
                                    'Windows - Python 3.7: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.7'
                                        )
                                    },
                                    'Windows - Python 3.8: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.8'
                                        )
                                    },
                                    'Windows - Python 3.9: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'windows && docker',
                                                    filename: 'ci/docker/python/windows/tox/Dockerfile',
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['windows']}"
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.9'
                                        )
                                    },
                                    'Linux - Python 3.6: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.6'
                                        )
                                    },
                                    'Linux - Python 3.7: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.7'
                                        )
                                    },
                                    'Linux - Python 3.8: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.8'
                                        )
                                    },
                                    'Linux - Python 3.9: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                dockerfile: [
                                                    label: 'linux && docker',
                                                    filename: 'ci/docker/python/linux/tox/Dockerfile',
                                                    additionalBuildArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                                ]
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.9'
                                        )
                                    },
                                ]
                            def macTests = [
                                'Mac - Python 3.8: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                label: 'mac && python3.8',
                                            ],
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: '3.8',
                                            toxExec: "venv/bin/tox",
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                                sh(
                                                    label:'Install Tox',
                                                    script: '''python3 -m venv venv
                                                               venv/bin/pip install pip --upgrade
                                                               venv/bin/pip install tox
                                                               '''
                                                )
                                            },
                                            testTeardown: {
                                                sh "rm -r venv/"
                                            }

                                        )
                                    },
                                    'Mac - Python 3.8: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                label: 'mac && python3.8',
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.8',
                                            toxExec: "venv/bin/tox",
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                                sh(
                                                    label:'Install Tox',
                                                    script: '''python3 -m venv venv
                                                               venv/bin/pip install pip --upgrade
                                                               venv/bin/pip install tox
                                                               '''
                                                )
                                            },
                                            testTeardown: {
                                                sh "rm -r venv/"
                                            }

                                        )
                                    },
                                    'Mac - Python 3.9: sdist': {
                                        packages.testPkg(
                                            agent: [
                                                label: 'mac && python3.9',
                                            ],
                                            glob: 'dist/*.tar.gz,dist/*.zip',
                                            stash: 'dist',
                                            pythonVersion: '3.9',
                                            toxExec: "venv/bin/tox",
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                                sh(
                                                    label:'Install Tox',
                                                    script: '''python3 -m venv venv
                                                               venv/bin/pip install pip --upgrade
                                                               venv/bin/pip install tox
                                                               '''
                                                )
                                            },
                                            testTeardown: {
                                                sh "rm -r venv/"
                                            }

                                        )
                                    },
                                    'Mac - Python 3.9: wheel': {
                                        packages.testPkg(
                                            agent: [
                                                label: 'mac && python3.9',
                                            ],
                                            glob: 'dist/*.whl',
                                            stash: 'dist',
                                            pythonVersion: '3.9',
                                            toxExec: "venv/bin/tox",
                                            testSetup: {
                                                checkout scm
                                                unstash 'dist'
                                                sh(
                                                    label:'Install Tox',
                                                    script: '''python3 -m venv venv
                                                               venv/bin/pip install pip --upgrade
                                                               venv/bin/pip install tox
                                                               '''
                                                )
                                            },
                                            testTeardown: {
                                                sh "rm -r venv/"
                                            }

                                        )
                                    }
                                ]
                            if(params.TEST_PACKAGES_ON_MAC == true){
                                tests = tests + macTests
                            }
                            parallel(tests)
                        }
                    }
                }
            }
        }
        stage("Deploying to Devpi") {
            when {
                allOf{
                    anyOf{
                        equals expected: true, actual: params.DEPLOY_DEVPI
                    }
                    anyOf {
                        equals expected: "master", actual: env.BRANCH_NAME
                        equals expected: "dev", actual: env.BRANCH_NAME
                    }
                }
                beforeAgent true
                beforeOptions true
            }
            options{
                lock("HathiZip-devpi")
            }

            agent none
            stages{
                stage("Uploading to DevPi Staging"){
                    agent {
                        dockerfile {
                            additionalBuildArgs "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['linux']}"
                            filename 'ci/docker/python/linux/tox/Dockerfile'
                            label 'linux && docker'
                        }
                    }
                    steps {
                        timeout(5){
                            unstash "DOCS_ARCHIVE"
                            unstash "dist"
                            script{
                                devpi.upload(
                                        server: 'https://devpi.library.illinois.edu',
                                        credentialsId: 'DS_devpi',
                                        index: getDevPiStagingIndex(),
                                        clientDir: './devpi'
                                    )
                            }
                        }
                    }
                    post{
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/", type: 'INCLUDE'],
                                    [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                    [pattern: 'build/', type: 'INCLUDE']
                                ]
                            )
                        }
                    }
                }
//                 TODO: test on mac
                stage('Test DevPi mac packages') {
                    steps{

                        parallel(
                            "Test Python 3.8: wheel Mac": {
                                script{
                                    devpi.testDevpiPackage2(
                                        agent: [
                                            label: 'mac && python3.8'
                                        ],
                                        devpi: [
                                            index: getDevPiStagingIndex(),
                                            server: 'https://devpi.library.illinois.edu',
                                            credentialsId: 'DS_devpi',
                                            devpiExec: 'venv/bin/devpi'

                                        ],
                                        package:[
                                            name: props.Name,
                                            version: props.Version,
                                            selector: "whl"
                                        ],
                                        test:[
                                            setup: {
                                                sh(
                                                    label:"Installing Devpi client",
                                                    script: '''python3 -m venv venv
                                                                venv/bin/python -m pip install pip --upgrade
                                                                venv/bin/python -m pip install devpi_client
                                                                '''
                                                )
                                            },
                                            toxEnv: 'py38',
                                            teardown: {
                                                sh( label: 'Remove Devpi client', script: 'rm -r venv')
                                            }
                                        ]
                                    )
                                }
                            },
                            "Test Python 3.8: sdist Mac": {
                                script{
                                    devpi.testDevpiPackage2(
                                        agent: [
                                            label: 'mac && python3.8'
                                        ],
                                        devpi: [
                                            index: getDevPiStagingIndex(),
                                            server: 'https://devpi.library.illinois.edu',
                                            credentialsId: 'DS_devpi',
                                            devpiExec: 'venv/bin/devpi'

                                        ],
                                        package:[
                                            name: props.Name,
                                            version: props.Version,
                                            selector: "tar.gz"
                                        ],
                                        test:[
                                            setup: {
                                                sh(
                                                    label:"Installing Devpi client",
                                                    script: '''python3 -m venv venv
                                                                venv/bin/python -m pip install pip --upgrade
                                                                venv/bin/python -m pip install devpi_client
                                                                '''
                                                )
                                            },
                                            toxEnv: 'py38',
                                            teardown: {
                                                sh( label: 'Remove Devpi client', script: 'rm -r venv')
                                            }
                                        ]
                                    )
                                }
                            },
                            "Test Python 3.9: wheel Mac": {
                                script{
                                    devpi.testDevpiPackage2(
                                        agent: [
                                            label: 'mac && python3.9'
                                        ],
                                        devpi: [
                                            index: getDevPiStagingIndex(),
                                            server: 'https://devpi.library.illinois.edu',
                                            credentialsId: 'DS_devpi',
                                            devpiExec: 'venv/bin/devpi'

                                        ],
                                        package:[
                                            name: props.Name,
                                            version: props.Version,
                                            selector: "whl"
                                        ],
                                        test:[
                                            setup: {
                                                sh(
                                                    label:"Installing Devpi client",
                                                    script: '''python3 -m venv venv
                                                                venv/bin/python -m pip install pip --upgrade
                                                                venv/bin/python -m pip install devpi_client
                                                                '''
                                                )
                                            },
                                            toxEnv: 'py39',
                                            teardown: {
                                                sh( label: 'Remove Devpi client', script: 'rm -r venv')
                                            }
                                        ]
                                    )
                                }
                            },
                            "Test Python 3.9: sdist Mac": {
                                script{
                                    devpi.testDevpiPackage2(
                                        agent: [
                                            label: 'mac && python3.9'
                                        ],
                                        devpi: [
                                            index: getDevPiStagingIndex(),
                                            server: 'https://devpi.library.illinois.edu',
                                            credentialsId: 'DS_devpi',
                                            devpiExec: 'venv/bin/devpi'

                                        ],
                                        package:[
                                            name: props.Name,
                                            version: props.Version,
                                            selector: "tar.gz"
                                        ],
                                        test:[
                                            setup: {
                                                sh(
                                                    label:"Installing Devpi client",
                                                    script: '''python3 -m venv venv
                                                                venv/bin/python -m pip install pip --upgrade
                                                                venv/bin/python -m pip install devpi_client
                                                                '''
                                                )
                                            },
                                            toxEnv: 'py39',
                                            teardown: {
                                                sh( label: 'Remove Devpi client', script: 'rm -r venv')
                                            }
                                        ]
                                    )
                                }
                            },
                        )
                    }

                }
                stage('Test DevPi packages') {
                    matrix {
                        axes {
                            axis {
                                name 'PLATFORM'
                                values(
                                    'windows',
                                    'linux'
                                    )
                            }
                            axis {
                                name 'PYTHON_VERSION'
                                values(
                                    '3.7',
                                    '3.8',
                                    '3.9'
                                    )
                            }
                        }
                        stages{
                            stage('Testing DevPi Package wheel'){
                                steps{
                                    script{
                                        devpi.testDevpiPackage2(
                                            agent: [
                                                dockerfile: [
                                                    filename: "ci/docker/python/${PLATFORM}/tox/Dockerfile",
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS[PLATFORM]}",
                                                    label: "${PLATFORM} && docker"

                                                ]
                                            ],
                                            devpi: [
                                                index: getDevPiStagingIndex(),
                                                server: 'https://devpi.library.illinois.edu',
                                                credentialsId: 'DS_devpi',

                                            ],
                                            package:[
                                                name: props.Name,
                                                version: props.Version,
                                                selector: "whl"
                                            ],
                                            test:[
                                                toxEnv: devpi.getToxEnvName(pythonVersion: PYTHON_VERSION)
                                            ]
                                        )
                                    }
                                }
                            }
                            stage('Testing DevPi sdist Package'){
                                steps{
                                    script{
                                        devpi.testDevpiPackage2(
                                            agent: [
                                                dockerfile: [
                                                    filename: "ci/docker/python/${PLATFORM}/tox/Dockerfile",
                                                    additionalBuildArgs: "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS[PLATFORM]}",
                                                    label: "${PLATFORM} && docker"

                                                ]
                                            ],
                                            devpi: [
                                                index: getDevPiStagingIndex(),
                                                server: 'https://devpi.library.illinois.edu',
                                                credentialsId: 'DS_devpi',

                                            ],
                                            package:[
                                                name: props.Name,
                                                version: props.Version,
                                                selector: "tar.gz"
                                            ],
                                            test:[
                                                toxEnv: devpi.getToxEnvName(pythonVersion: PYTHON_VERSION)
                                            ]
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                stage("Deploy to DevPi Production") {
                    when {
                        allOf{
                            equals expected: true, actual: params.DEPLOY_DEVPI_PRODUCTION
                            branch "master"
                        }
                        beforeAgent true
                        beforeInput true
                    }
                    options{
                        timeout(time: 1, unit: 'DAYS')
                    }
                    agent {
                        dockerfile {
                            additionalBuildArgs "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['linux']}"
                            filename 'ci/docker/python/linux/tox/Dockerfile'
                            label 'linux && docker'
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
                                server: 'https://devpi.library.illinois.edu',
                                indexSource: DEVPI_STAGING_INDEX,
                                indexDestination: 'production/release',
                                credentialsId: 'DS_devpi'
                            )
                        }
                    }
                }
            }
            post{
                success{
                    node('linux && docker') {
                       script{
                            docker.build("hathizip:devpi","-f ci/docker/python/linux/tox/Dockerfile --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['linux']} .").inside{
                                devpi.pushPackageToIndex(
                                        pkgName: props.Name,
                                        pkgVersion: props.Version,
                                        server: 'https://devpi.library.illinois.edu',
                                        indexSource: DEVPI_STAGING_INDEX,
                                        indexDestination: "DS_Jenkins/${env.BRANCH_NAME}",
                                        credentialsId: 'DS_devpi'
                                    )
                            }
                       }
                    }
                }
                cleanup{
                    node('linux && docker') {
                       script{
                            docker.build("hathizip:devpi","-f ci/docker/python/linux/tox/Dockerfile --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL ${DOCKER_PLATFORM_BUILD_ARGS['linux']} .").inside{
                                devpi.removePackage(
                                    pkgName: props.Name,
                                    pkgVersion: props.Version,
                                    index: DEVPI_STAGING_INDEX,
                                    server: 'https://devpi.library.illinois.edu',
                                    credentialsId: 'DS_devpi',

                                )
                            }
                       }
                    }
                }
            }
        }
        stage("Deploy"){
            parallel{
                stage("Deploy to SCCM") {
                    when{
                        allOf{
                            equals expected: true, actual: params.DEPLOY_SCCM
                            branch "master"
                        }
                        beforeAgent true
                        beforeInput true
                    }
                    agent{
                        label "linux"
                    }
                    input {
                        message 'Deploy to production?'
                        parameters {
                            string defaultValue: '', description: '', name: 'SCCM_UPLOAD_FOLDER', trim: true
                            string defaultValue: '', description: '', name: 'SCCM_STAGING_FOLDER', trim: true
                        }
                    }

                    options{
                        skipDefaultCheckout true
                    }

                    steps {
                        unstash "msi"
                        deployStash("msi", "${env.SCCM_STAGING_FOLDER}/${params.PROJECT_NAME}/")
                        deployStash("msi", "${env.SCCM_UPLOAD_FOLDER}")

                    }
                    post {
                        success {
                            script{
                                def deployment_request = requestDeploy this, "deployment.yml"
                                echo deployment_request
                                writeFile file: "deployment_request.txt", text: deployment_request
                                archiveArtifacts artifacts: "deployment_request.txt"
                            }
                        }
                    }
                }
                stage("Update online documentation") {
                    agent {
                        label "Linux"
                    }
                    when {
                        equals expected: true, actual: params.UPDATE_DOCS
                        beforeAgent true
                        beforeInput true
                    }
                    input {
                        message 'Update online documentation'
                        parameters {
                            string defaultValue: 'hathi_zip', description: 'The directory that the docs should be saved under', name: 'URL_SUBFOLDER', trim: true
                        }
                    }
                    steps {
                        updateOnlineDocs url_subdomain: "${URL_SUBFOLDER}", stash_name: "HTML Documentation"

                    }
                }
            }
        }
    }
}
