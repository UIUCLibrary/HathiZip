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


pipeline {
    agent none
    triggers {
       parameterizedCron '@daily % PACKAGE_CX_FREEZE=true; DEPLOY_DEVPI=true; TEST_RUN_TOX=true'
    }
    libraries {
      lib('devpi')
      lib('PythonHelpers')
      lib('ds-utils')
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
        booleanParam(name: "TEST_RUN_TOX", defaultValue: false, description: "Run Tox Tests")
        booleanParam(name: "PACKAGE_CX_FREEZE", defaultValue: false, description: "Create a package with CX_Freeze")
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: false, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: "DEPLOY_DEVPI_PRODUCTION", defaultValue: false, description: "Deploy to https://devpi.library.illinois.edu/production/release")
//         TODO Set to false
        booleanParam(name: "DEPLOY_ADD_TAG", defaultValue: true, description: "Tag commit to current version")
        booleanParam(name: "DEPLOY_SCCM", defaultValue: false, description: "Deploy to SCCM")
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")

    }
    stages {
        stage("Getting Distribution Info"){
           agent {
                dockerfile {
                    filename 'ci/docker/python/linux/testing/Dockerfile'
                    label 'linux && docker'
                }
            }
            steps{
                sh "python setup.py dist_info"
            }
            post{
                success{
                    stash includes: "HathiZip.dist-info/**", name: 'DIST-INFO'
                    archiveArtifacts artifacts: "HathiZip.dist-info/**"
                }
            }
        }
        stage("Build"){
            agent {
                dockerfile {
                    filename 'ci/docker/python/linux/testing/Dockerfile'
                    label 'linux && docker'
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
                            unstash "DIST-INFO"
                            script{
                                def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
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

            parallel {
                stage("PyTest"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
                        }
                    }
                    steps{
                        sh(label: "Running pytest",
                            script: """python -m pytest --junitxml=reports/junit-${env.NODE_NAME}-pytest.xml --junit-prefix=${env.NODE_NAME}-pytest --cov-report html:reports/coverage/ --cov=hathizip"""
                        )
                    }
                    post {
                        always{
                            dir("reports"){
                                script{
                                    def report_files = findFiles glob: '**/*.pytest.xml'
                                    report_files.each { report_file ->
                                        echo "Found ${report_file}"
                                        junit "${report_file}"
                                    }
                                }
                            }
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/coverage', reportFiles: 'index.html', reportName: 'Coverage', reportTitles: ''])
                        }
                    }
                }
                stage("Doctest"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
                        }
                    }
                    steps{
                        sh "python -m sphinx -b doctest docs/source build/docs -d build/docs/doctrees -v"
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
                    agent {
                      dockerfile {
                        filename 'ci/docker/python-testing/Dockerfile'
                        label "linux && docker"
                      }
                    }
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
                        cleanup{
                            deleteDir()
                        }
                    }
                }
                stage("Run Flake8 Static Analysis") {
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
                        }
                    }
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
                stage("Run Tox"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
                        }
                    }
                    when{
                        equals expected: true, actual: params.TEST_RUN_TOX
                        beforeAgent true
                    }
                    steps {
                        script{
                            try{
                                sh (
                                    label: "Run Tox",
                                    script: "tox -e py --workdir .tox -v"
                                )
                            } catch (exc) {
                                sh (
                                    label: "Run Tox with new environments",
                                    script: "tox --recreate -e py  --workdir .tox -v"
                                )
                            }
                        }
                    }
                    post{
                        always{
                            archiveArtifacts allowEmptyArchive: true, artifacts: '.tox/py*/log/*.log,.tox/log/*.log,logs/tox_report.json'
                        }
                        cleanup{
                            cleanWs deleteDirs: true, patterns: [
                                [pattern: '.tox/', type: 'INCLUDE'],
                                [pattern: "HathiZip.dist-info/", type: 'INCLUDE'],
                                [pattern: 'logs/rox_report.json', type: 'INCLUDE']
                            ]
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
                        }
                    }
                    options{
                        timeout(5)
                    }
                    steps{
                        sh "python setup.py sdist --format zip -d dist bdist_wheel -d dist"

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
                    options{
                        timeout(15)
                    }
                    steps{
                        bat "python cx_setup.py bdist_msi --add-to-path=true -k --bdist-dir build/msi -d dist"


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
                    options{
                        timeout(5)
                    }
                    steps {
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
                                        unstash "DIST-INFO"
                                        script{
                                            def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
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
                    post{
                        success{
                            node('linux && docker') {
                               script{
                                    docker.build("hathizip:devpi.${env.BUILD_ID}",'-f ./ci/docker/deploy/devpi/deploy/Dockerfile --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) .').inside{
                                        unstash "DIST-INFO"
                                        def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
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
                                    docker.build("hathizip:devpi.${env.BUILD_ID}",'-f ./ci/docker/deploy/devpi/deploy/Dockerfile --build-arg USER_ID=$(id -u) --build-arg GROUP_ID=$(id -g) .').inside{
                                        unstash "DIST-INFO"
                                        def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
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
                stage("Deploy to DevPi Production") {
                    when {
                        allOf{
                            equals expected: true, actual: params.DEPLOY_DEVPI_PRODUCTION
                            branch "master"
                        }
                        beforeAgent true
                        beforeInput true
                    }
                    input{
                        message 'Release to DevPi Production?'
                    }
                    agent {
                        dockerfile {
                            filename 'ci/docker/python37/windows/build/msvc/Dockerfile'
                            label "windows && docker"
                        }
                    }
                    steps {
                        unstash "DIST-INFO"
                        script {
                            def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                            bat "devpi login ${env.DEVPI_USR} --password ${env.DEVPI_PSW}"
                            bat "devpi  use /DS_Jenkins/${env.BRANCH_NAME}"
                            bat "devpi push ${props.Name}==${props.Version} production/release"
                        }
                    }
                }
            }
        }
        stage("Deploy"){
            environment{
//             get_package_version
                PKG_VERSION = "d"
            }
            stages{
                stage("Tagging git Commit"){
                    agent {
                        dockerfile {
                            filename 'ci/docker/python/linux/testing/Dockerfile'
                            label 'linux && docker'
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
                    }
                    steps{
                        unstash "DIST-INFO"
                        script{
                            def props = readProperties interpolate: true, file: "HathiZip.dist-info/METADATA"
                            def commitTag = input message: 'git commit', parameters: [string(defaultValue: "v${props.Version}", description: 'Version to use a a git tag', name: 'Tag', trim: false)]
                            sh(label: "Tagging ${commitTag.Tag}",
                               script: "git tag -a ${commitTag.Tag}"
                            )

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
