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
        ]
]
def tox

node(){
    checkout scm
    tox = load("ci/jenkins/scripts/tox.groovy")
}

node('linux && docker') {
    timeout(2){
        ws{
            checkout scm
            try{
                docker.image('python:3.8').inside {
                    stage("Getting Distribution Info"){
                        sh(
                           label: "Running setup.py with dist_info",
                           script: """python --version
                                      python setup.py dist_info
                                   """
                        )
                        stash includes: "HathiZip.dist-info/**", name: 'DIST-INFO'
                        archiveArtifacts artifacts: "HathiZip.dist-info/**"
                    }
                }
            } finally{
                deleteDir()
            }
        }
    }
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

def props = get_props()

pipeline {
    agent none
    libraries {
      lib('devpi')
      lib('PythonHelpers')
      lib('ds-utils')
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
        booleanParam(name: 'USE_SONARQUBE', defaultValue: true, description: 'Send data test data to SonarQube')
        booleanParam(name: "TEST_RUN_TOX", defaultValue: false, description: "Run Tox Tests")
        booleanParam(name: "PACKAGE_CX_FREEZE", defaultValue: false, description: "Create a package with CX_Freeze")
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: false, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: "DEPLOY_DEVPI_PRODUCTION", defaultValue: false, description: "Deploy to https://devpi.library.illinois.edu/production/release")
        booleanParam(name: "DEPLOY_ADD_TAG", defaultValue: false, description: "Tag commit to current version")
        booleanParam(name: "DEPLOY_SCCM", defaultValue: false, description: "Deploy to SCCM")
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")

    }
    stages {
        stage("Build"){
            agent {
                dockerfile {
                    filename 'ci/docker/python/linux/testing/Dockerfile'
                    label 'linux && docker'
                    additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) --build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_TRUSTED_HOST'
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
        stage("Tests") {
            stages{
                stage("Code Quality"){
                    stages{
                        stage("Run test"){
                            agent {
                                dockerfile {
                                    filename 'ci/docker/python/linux/testing/Dockerfile'
                                    label 'linux && docker'
                                    additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)'
                                }
                            }
                            stages{
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
                                        stage("Doctest"){
                                            steps{
                                                sh '''mkdir -p build/docs/
                                                      python -m sphinx -b doctest docs/source build/docs -d build/docs/doctrees -v
                                                      '''
                                            }
                                            post{
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
                                                       script: """mkdir -p logs
                                                                  flake8 hathizip --tee --output-file=logs/flake8.log
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
                                lock('speedwagon-sonarscanner')
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
        //                                 'PYLINT_REPORT',
                                        'FLAKE8_REPORT'
                                    ]
                                    def sonarqubeConfig = [
                                        installationName: 'sonarcloud',
                                        credentialsId: 'sonartoken-hathizip',
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
//                                         linuxJobs = tox.getToxTestsParallel("Tox Linux", "linux && docker", "ci/docker/python/linux/tox/Dockerfile", "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL")
                                        linuxJobs = tox.getToxTestsParallel(
                                                envNamePrefix: "Tox Linux",
                                                label: "linux && docker",
                                                dockerfile: "ci/docker/python/linux/tox/Dockerfile",
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL'
                                            )
                                    },
                                    "Windows":{
//                                         windowsJobs = tox.getToxTestsParallel("Tox Windows", "windows && docker", "ci/docker/python/windows/tox/Dockerfile", "--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg CHOCOLATEY_SOURCE")
                                        windowsJobs = tox.getToxTestsParallel(
                                                envNamePrefix: "Tox Windows",
                                                label: "windows && docker",
                                                dockerfile: "ci/docker/python/windows/tox/Dockerfile",
                                                dockerArgs: '--build-arg PIP_EXTRA_INDEX_URL --build-arg PIP_INDEX_URL --build-arg CHOCOLATEY_SOURCE'
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
            parallel {
                stage("Source and Wheel formats"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
                            additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)'
                        }
                    }
                    steps{
                        timeout(5){
                            sh "python setup.py sdist --format zip -d dist bdist_wheel -d dist"
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
                            label "windows && docker"
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
                timestamps()
                lock("HathiZip-devpi")
            }

            agent none
            environment{
                DEVPI = credentials("DS_devpi")
            }
            stages{
                stage("Uploading to DevPi Staging"){

                    agent {
                        dockerfile {
                            filename 'ci/docker/deploy/devpi/deploy/Dockerfile'
                            label 'linux&&docker'
                            additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)'
                          }
                    }
                    steps {
                        timeout(5){
                            unstash "DOCS_ARCHIVE"
                            unstash "dist"
                            sh(
                                    label: "Connecting to DevPi Server",
                                    script: 'devpi use https://devpi.library.illinois.edu --clientdir ${WORKSPACE}/devpi && devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ${WORKSPACE}/devpi'
                                )
                            sh(
                                label: "Uploading to DevPi Staging",
                                script: """devpi use /${env.DEVPI_USR}/${env.BRANCH_NAME}_staging --clientdir ${WORKSPACE}/devpi
                                           devpi upload --from-dir dist --clientdir ${WORKSPACE}/devpi"""
                            )
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
                stage("Test DevPi packages") {
                    matrix {
                        axes {
                            axis {
                                name 'FORMAT'
                                values 'zip', "whl"
                            }
                            axis {
                                name 'PYTHON_VERSION'
                                values '3.8', "3.7", '3.6'
                            }
                        }
                        agent {
                          dockerfile {
                            additionalBuildArgs "--build-arg PYTHON_DOCKER_IMAGE_BASE=${CONFIGURATIONS[PYTHON_VERSION].test_docker_image}"
                            filename 'ci/docker/deploy/devpi/test/windows/Dockerfile'
                            label 'windows && docker'
                          }
                        }
                        stages{
                            stage("Testing DevPi Package"){
                                steps{
                                    timeout(10){
                                        script{
                                            bat "devpi use https://devpi.library.illinois.edu --clientdir certs\\ && devpi login %DEVPI_USR% --password %DEVPI_PSW% --clientdir certs\\ && devpi use ${env.BRANCH_NAME}_staging --clientdir certs\\"
                                            bat "devpi test --index ${env.BRANCH_NAME}_staging ${props.Name}==${props.Version} -s ${FORMAT} --clientdir certs\\ -e ${CONFIGURATIONS[PYTHON_VERSION].tox_env} -v"
                                        }
                                    }
                                }
                                post{
                                    cleanup{
                                        cleanWs(
                                            deleteDirs: true,
                                            patterns: [
                                                [pattern: "dist/", type: 'INCLUDE'],
                                                [pattern: "certs/", type: 'INCLUDE'],
                                                [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                                [pattern: 'build/', type: 'INCLUDE']
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
                            filename 'ci/docker/deploy/devpi/deploy/Dockerfile'
                            label 'linux&&docker'
                            additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)'
                          }
                    }
                    input {
                        message 'Release to DevPi Production?'
                    }
                    steps {
                        script{
                            sh(label: "Pushing to production index",
                               script: """devpi use https://devpi.library.illinois.edu --clientdir ./devpi
                                          devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ./devpi
                                          devpi push --index DS_Jenkins/${env.BRANCH_NAME}_staging ${props.Name}==${props.Version} production/release --clientdir ./devpi
                                       """
                            )
                        }
                    }
                }

            }
            post{
                success{
                    node('linux && docker') {
                       script{
                            docker.build("hathizip:devpi",'-f ./ci/docker/deploy/devpi/deploy/Dockerfile --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) .').inside{
                                sh(
                                    label: "Connecting to DevPi Server",
                                    script: 'devpi use https://devpi.library.illinois.edu --clientdir ${WORKSPACE}/devpi && devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ${WORKSPACE}/devpi'
                                )
                                sh "devpi use DS_Jenkins/${env.BRANCH_NAME}_staging --clientdir ${WORKSPACE}/devpi"
                                sh "devpi push ${props.Name}==${props.Version} DS_Jenkins/${env.BRANCH_NAME} --clientdir ${WORKSPACE}/devpi"
                            }
                       }
                    }
                }
                cleanup{
                    node('linux && docker') {
                       script{
                            docker.build("hathizip:devpi",'-f ./ci/docker/deploy/devpi/deploy/Dockerfile --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) .').inside{
                                sh(
                                    label: "Connecting to DevPi Server",
                                    script: 'devpi use https://devpi.library.illinois.edu --clientdir ${WORKSPACE}/devpi && devpi login $DEVPI_USR --password $DEVPI_PSW --clientdir ${WORKSPACE}/devpi'
                                )
                                sh "devpi use /DS_Jenkins/${env.BRANCH_NAME}_staging --clientdir ${WORKSPACE}/devpi"
                                sh "devpi remove -y ${props.Name}==${props.Version} --clientdir ${WORKSPACE}/devpi"
                            }
                       }
                    }
                }
            }
        }
        stage("Deploy"){
            stages{
                stage("Tagging git Commit"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
                            additionalBuildArgs '--build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g)'
                        }
                    }
                    when{
                        allOf{
                            equals expected: true, actual: params.DEPLOY_ADD_TAG
                        }
                        beforeAgent true
                        beforeInput true
                    }
                    options{
                        timeout(time: 1, unit: 'DAYS')
                        retry(3)
                    }
                    input {
                          message 'Add a version tag to git commit?'
                          parameters {
                                credentials credentialType: 'com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl', defaultValue: 'github.com', description: '', name: 'gitCreds', required: true
                          }
                    }
                    steps{
                        script{
                            def commitTag = input message: 'git commit', parameters: [string(defaultValue: "v${props.Version}", description: 'Version to use a a git tag', name: 'Tag', trim: false)]
                            withCredentials([usernamePassword(credentialsId: gitCreds, passwordVariable: 'password', usernameVariable: 'username')]) {
                                sh(label: "Tagging ${commitTag}",
                                   script: """git config --local credential.helper "!f() { echo username=\\$username; echo password=\\$password; }; f"
                                              git tag -a ${commitTag} -m 'Tagged by Jenkins'
                                              git push origin --tags
                                   """
                                )
                            }
                        }
                    }
                    post{
                        cleanup{
                            deleteDir()
                        }
                    }
                }
            }
        }
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
