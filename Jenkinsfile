#!groovy
@Library("ds-utils")
import org.ds.*

@Library(["devpi", "PythonHelpers"]) _

def remove_from_devpi(devpiExecutable, pkgName, pkgVersion, devpiIndex, devpiUsername, devpiPassword){
    script {
                try {
                    bat "${devpiExecutable} login ${devpiUsername} --password ${devpiPassword}"
                    bat "${devpiExecutable} use ${devpiIndex}"
                    bat "${devpiExecutable} remove -y ${pkgName}==${pkgVersion}"
                } catch (Exception ex) {
                    echo "Failed to remove ${pkgName}==${pkgVersion} from ${devpiIndex}"
            }

    }
}


pipeline {
    agent {
        label "Windows && Python3"
    }
    options {
        disableConcurrentBuilds()  //each branch has 1 job running at a time
        timeout(60)  // Timeout after 60 minutes. This shouldn't take this long but it hangs for some reason
        checkoutToSubdirectory("source")
    }
//    environment {
////        DEVPI = credentials("DS_devpi")
////        mypy_args = "--junit-xml=mypy.xml"
////        pytest_args = "--junitxml=reports/junit-{env:OS:UNKNOWN_OS}-{envname}.xml --junit-prefix={env:OS:UNKNOWN_OS}  --basetemp={envtmpdir}"
//    }
    triggers {
        cron('@daily')
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
        booleanParam(name: "FRESH_WORKSPACE", defaultValue: false, description: "Purge workspace before staring and checking out source")
        booleanParam(name: "TEST_RUN_TOX", defaultValue: true, description: "Run Tox Tests")
        booleanParam(name: "PACKAGE_CX_FREEZE", defaultValue: false, description: "Create a package with CX_Freeze")
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: false, description: "Deploy to devpi on https://devpi.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: "DEPLOY_DEVPI_PRODUCTION", defaultValue: false, description: "Deploy to https://devpi.library.illinois.edu/production/release")
        booleanParam(name: "DEPLOY_SCCM", defaultValue: false, description: "Deploy to SCCM")
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")
        string(name: 'URL_SUBFOLDER', defaultValue: "hathi_zip", description: 'The directory that the docs should be saved under')
    }
    stages {
        stage("Configure") {
            stages{
                stage("Purge all existing data in workspace"){
                    when{
                        anyOf{
                            equals expected: true, actual: params.FRESH_WORKSPACE
                            triggeredBy "TimerTriggerCause"
                        }
                    }
                    steps {
                        deleteDir()
                        dir("source"){
                            checkout scm
                        }
                    }
                }
                stage("Stashing important files for later"){
                    environment{
                        PATH = "${tool 'CPython-3.7'};$PATH"
                    }
                    steps{
                        dir("source"){

                            bat "python setup.py dist_info"
                        }
                    }
                    post{
                        success{
                            dir("source"){
                                stash includes: "HathiZip.dist-info/**", name: 'DIST-INFO'
                                stash includes: 'deployment.yml', name: "Deployment"
                            }
                        }
                    }
                }
                stage("Creating virtualenv for building"){
                    environment{
                        PATH = "${tool 'CPython-3.7'};$PATH"
                    }
                    steps{
                        bat "python -m venv venv"
                        script {
                            try {
                                bat "call venv\\Scripts\\python.exe -m pip install -U pip>=18.0"
                            }
                            catch (exc) {
                                bat "python -m venv venv"
                                bat "call venv\\Scripts\\python.exe -m pip install -U pip>=18.0 --no-cache-dir"
                            }
                        }
                        bat 'venv\\Scripts\\pip.exe install "tox>=3.8.2,<3.10" mypy lxml pytest pytest-cov flake8 sphinx wheel --upgrade-strategy only-if-needed'
                    }
                    post{
                        success{
                            bat "(if not exist logs mkdir logs) && venv\\Scripts\\pip.exe list > logs\\pippackages_venv_${NODE_NAME}.log"
                            archiveArtifacts artifacts: "logs/pippackages_venv_${NODE_NAME}.log"
                        }


                    }
                }
            }
            post{
                failure {
                    deleteDir()
                }

            }
        }

        stage("Build"){
            stages{
                stage("Python Package"){
                    steps {
                        dir("source"){
                            powershell "& ${WORKSPACE}\\venv\\Scripts\\python.exe setup.py build -b ${WORKSPACE}\\build   | tee ${WORKSPACE}\\logs\\build.log"
                        }
                    }
                    post{
                        always{
                            recordIssues(tools: [
                                        pyLint(name: 'Setuptools Build: PyLint', pattern: 'logs/build.log'),
                                    ]
                                )
                            archiveArtifacts artifacts: "logs/build.log"
                        }

                    }
                }
                stage("Building Sphinx Documentation"){
                    environment{
                        DOC_ZIP_FILENAME = "hathizip-docs.zip"
                    }
                    steps {
                        echo "Building docs on ${env.NODE_NAME}"
                        bat "${WORKSPACE}\\venv\\Scripts\\sphinx-build.exe source/docs/source build/docs/html -d build/docs/.doctrees -v -w ${WORKSPACE}\\logs\\build_sphinx.log"
                    }
                    post{
                        always {
                            recordIssues(tools: [sphinxBuild(name: 'Sphinx Documentation Build', pattern: 'logs/build_sphinx.log')])
                            archiveArtifacts artifacts: 'logs/build_sphinx.log'
                        }
                        success{
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'build/docs/html', reportFiles: 'index.html', reportName: 'Documentation', reportTitles: ''])
                            zip archive: true, dir: "build/docs/html", glob: '', zipFile: "dist/${env.DOC_ZIP_FILENAME}"
                            stash includes: "dist/${env.DOC_ZIP_FILENAME},build/docs/html/**", name: 'DOCS_ARCHIVE'
                        }
                        failure{
                            echo "Failed to build Python package"
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                    [pattern: "dist/${env.DOC_ZIP_FILENAME}", type: 'INCLUDE'],
                                    [pattern: 'build/docs/html/**"', type: 'INCLUDE']
                                    ]
                            )
                        }
                    }
                }
            }
        }
        stage("Tests") {
            environment {
                PATH = "${WORKSPACE}\\venv\\Scripts;$PATH"
            }
            parallel {
                stage("PyTest"){
                    steps{
                        dir("source"){
                            bat "python -m pytest --junitxml=${WORKSPACE}/reports/junit-${env.NODE_NAME}-pytest.xml --junit-prefix=${env.NODE_NAME}-pytest --cov-report html:${WORKSPACE}/reports/coverage/ --cov=hathizip" //  --basetemp={envtmpdir}"
                        }

                    }
                    post {
                        always{
                            dir("reports"){
                                script{
                                    def report_files = findFiles glob: '**/*.pytest.xml'
                                    report_files.each { report_file ->
                                        echo "Found ${report_file}"
                                        junit "${report_file}"
                                        bat "del ${report_file}"
                                    }
                                }
                            }
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/coverage', reportFiles: 'index.html', reportName: 'Coverage', reportTitles: ''])
                        }
                    }
                }
                stage("Documentation"){
                    steps{
                        dir("source"){
                            bat "sphinx-build.exe -b doctest docs\\source ${WORKSPACE}\\build\\docs -d ${WORKSPACE}\\build\\docs\\doctrees -v"
                        }
                    }

                }
                stage("MyPy"){
                    steps{
                        bat "if not exist reports\\mypy\\mypy_html mkdir reports\\mypy\\mypy_html"
                        dir("source") {
                            bat returnStatus: true, script: "mypy.exe -p hathizip --junit-xml=${WORKSPACE}/reports/mypy/junit-${env.NODE_NAME}-mypy.xml --html-report ${WORKSPACE}/reports/mypy/mypy_html > ${WORKSPACE}\\logs\\mypy.log"
//                            bat "mypy.exe -p hathizip --junit-xml=${WORKSPACE}/junit-${env.NODE_NAME}-mypy.xml --html-report ${WORKSPACE}/reports/mypy_html"
                        }
                    }
                    post{
                        always {
                            junit "reports/mypy/junit-${env.NODE_NAME}-mypy.xml"
                            publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'reports/mypy/mypy_html', reportFiles: 'index.html', reportName: 'MyPy', reportTitles: ''])
                            recordIssues(tools: [myPy(name: 'MyPy', pattern: 'logs/mypy.log')])
                        }
                        cleanup{
                            cleanWs(
                                deleteDirs: true,
                                patterns: [
                                        [pattern: 'reports/mypy', type: 'INCLUDE']
                                    ]
                                )

                        }
                    }
                }
                stage("Run Flake8 Static Analysis") {
                    steps{
                        script{
                            bat "pip install flake8"
                            try{
                                dir("source"){
                                    bat "flake8 pyhathiprep --tee --output-file=${WORKSPACE}\\logs\\flake8.log"
                                }
                            } catch (exc) {
                                echo "flake8 found some warnings"
                            }
                        }
                    }
                    post {
                        always {

                            recordIssues(tools: [flake8(name: 'Flake8', pattern: 'logs/flake8.log')])
                        }
                        cleanup{
                            cleanWs(patterns: [[pattern: 'logs/flake8.log', type: 'INCLUDE']])
                        }
                    }
                }
                stage("Run Tox"){
                    environment{
                        PATH = "${tool 'CPython-3.6'};${tool 'CPython-3.7'};$PATH"
                    }
                    when{
                        equals expected: true, actual: params.TEST_RUN_TOX
                    }
                    steps {
                        dir("source"){
                            script{
                                try{
                                    bat (
                                        label: "Run Tox",
                                        script: "tox --parallel=auto --parallel-live --workdir ${WORKSPACE}\\.tox -v --result-json=${WORKSPACE}\\logs\\tox_report.json"
                                    )
                                } catch (exc) {
                                    bat (
                                        label: "Run Tox with new environments",
                                        script: "tox --recreate --parallel=auto --parallel-live --workdir ${WORKSPACE}\\.tox -v --result-json=${WORKSPACE}\\logs\\tox_report.json"
                                    )
                                }
                            }
                        }
//                        dir("source"){
//                            script{
//                                try{
//                                    bat "tox --parallel=auto --parallel-live --workdir ${WORKSPACE}\\.tox -vv"
//                                } catch (exc) {
//                                    bat "tox --parallel=auto --parallel-live --workdir ${WORKSPACE}\\.tox --recreate -vv"
//                                }
//                            }
//                        }
                    }
                    post{
                        always{
                            archiveArtifacts allowEmptyArchive: true, artifacts: '.tox/py*/log/*.log,.tox/log/*.log,logs/tox_report.json'
                        }
                        cleanup{
                            cleanWs deleteDirs: true, patterns: [
                                [pattern: '.tox/py*/log/*.log', type: 'INCLUDE'],
                                [pattern: '.tox/log/*.log', type: 'INCLUDE'],
                                [pattern: 'logs/rox_report.json', type: 'INCLUDE']
                            ]
                        }
                        failure {
                            dir("${WORKSPACE}\\.tox"){
                                deleteDir()
                            }
                        }
                    }
                }
            }
        }
        stage("Packaging") {
            parallel {
                stage("Source and Wheel formats"){
                    steps{
                        dir("source"){
                            bat "${WORKSPACE}\\venv\\scripts\\python.exe setup.py sdist --format zip -d ${WORKSPACE}\\dist bdist_wheel -d ${WORKSPACE}\\dist"
                        }

                    }
                    post{
                        success{
                            archiveArtifacts artifacts: "dist/*.whl,dist/*.tar.gz,dist/*.zip", fingerprint: true
                            stash includes: 'dist/*.whl,dist/*.tar.gz,dist/*.zip', name: "dist"
                        }
                    }
                }
                stage("Windows CX_Freeze MSI"){
                    when{
                        equals expected: true, actual: params.PACKAGE_CX_FREEZE
                    }
                    steps{
                        bat "venv\\Scripts\\pip.exe install -r source\\requirements.txt -r source\\requirements-freeze.txt -r source\\requirements-dev.txt -q"
                        dir("source"){
                            bat "${WORKSPACE}\\venv\\Scripts\\python.exe cx_setup.py bdist_msi --add-to-path=true -k --bdist-dir ${WORKSPACE}/build/msi -d ${WORKSPACE}/dist"
                        }


                    }
                    post{
                        success{
                            stash includes: "dist/*.msi", name: "msi"
                            archiveArtifacts artifacts: "dist/*.msi", fingerprint: true
                            }
                        cleanup{
                            bat "del dist\\*.msi"
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
                        triggeredBy "TimerTriggerCause"
                    }
                    anyOf {
                        equals expected: "master", actual: env.BRANCH_NAME
                        equals expected: "dev", actual: env.BRANCH_NAME
                    }
                }
            }
            options{
                timestamps()
            }
            environment{
                PATH = "${WORKSPACE}\\venv\\Scripts;${tool 'CPython-3.6'};${tool 'CPython-3.6'}\\Scripts;${PATH}"
                DEVPI = credentials("DS_devpi")
            }

            stages{
                stage("Install DevPi Client"){
                    steps{
                        bat "pip install devpi-client"
                    }
                }
                stage("Uploading to DevPi Staging"){
                    steps {
                        unstash "DOCS_ARCHIVE"
                        unstash "dist"
                        bat "devpi use https://devpi.library.illinois.edu && devpi login ${env.DEVPI_USR} --password ${env.DEVPI_PSW} && devpi use /${env.DEVPI_USR}/${env.BRANCH_NAME}_staging && devpi upload --from-dir dist"

                    }
                }
                stage("Test DevPi packages") {


                    parallel {
                        stage("Testing Submitted Source Distribution") {
                            environment {
                                PATH = "${tool 'CPython-3.7'};${tool 'CPython-3.6'};$PATH"
                            }
                            agent {
                                node {
                                    label "Windows && Python3"
                                }
                            }
                            options {
                                skipDefaultCheckout(true)

                            }
                            stages{
                                stage("Creating venv to test sdist"){
                                    steps {
                                        lock("system_python_${NODE_NAME}"){
                                            bat "python -m venv venv"
                                        }
                                        bat "venv\\Scripts\\python.exe -m pip install pip --upgrade && venv\\Scripts\\pip.exe install setuptools --upgrade && venv\\Scripts\\pip.exe install \"tox<3.7\" detox devpi-client"
                                    }

                                }
                                stage("Testing DevPi zip Package"){
                                    options{
                                        timeout(10)
                                    }
                                    environment {
                                        PATH = "${WORKSPACE}\\venv\\Scripts;$PATH"
                                    }
                                    steps {
                                        unstash "DIST-INFO"
                                        script{
                                            def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                                            devpiTest(
                                                devpiExecutable: "${powershell(script: '(Get-Command devpi).path', returnStdout: true).trim()}",
                                                url: "https://devpi.library.illinois.edu",
                                                index: "${env.BRANCH_NAME}_staging",
                                                pkgName: "${props.Name}",
                                                pkgVersion: "${props.Version}",
                                                pkgRegex: "zip",
                                                detox: false
                                            )
                                        }
                                        echo "Finished testing Source Distribution: .zip"
                                    }

                                }
                            }
                            post {
                                cleanup{
                                    cleanWs(
                                        deleteDirs: true,
                                        disableDeferredWipeout: true,
                                        patterns: [
                                            [pattern: '*tmp', type: 'INCLUDE'],
                                            [pattern: 'certs', type: 'INCLUDE']
                                            ]
                                    )
                                }
                            }

                        }
                        stage("Built Distribution: .whl") {
                            agent {
                                node {
                                    label "Windows && Python3"
                                }
                            }
                            environment {
                                PATH = "${tool 'CPython-3.6'};${tool 'CPython-3.6'}\\Scripts;${tool 'CPython-3.7'};$PATH"
                            }
                            options {
                                skipDefaultCheckout(true)
                            }
                            stages{
                                stage("Creating venv to Test Whl"){
                                    steps {
                                        lock("system_python_${NODE_NAME}"){
                                            bat "if not exist venv\\36 mkdir venv\\36"
                                            bat "\"${tool 'CPython-3.6'}\\python.exe\" -m venv venv\\36"
                                            bat "if not exist venv\\37 mkdir venv\\37"
                                            bat "\"${tool 'CPython-3.7'}\\python.exe\" -m venv venv\\37"
                                        }
                                        bat "venv\\36\\Scripts\\python.exe -m pip install pip --upgrade && venv\\36\\Scripts\\pip.exe install setuptools --upgrade && venv\\36\\Scripts\\pip.exe install \"tox<3.7\" devpi-client"
                                    }

                                }
                                stage("Testing DevPi .whl Package"){
                                    options{
                                        timeout(10)
                                    }
                                    environment {
                                        PATH = "${WORKSPACE}\\venv\\36\\Scripts;${WORKSPACE}\\venv\\37\\Scripts;$PATH"
                                    }
                                    steps {
                                        echo "Testing Whl package in devpi"
                                        unstash "DIST-INFO"
                                        script{
                                            def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                                            devpiTest(
                                                    devpiExecutable: "${powershell(script: '(Get-Command devpi).path', returnStdout: true).trim()}",
                                                    url: "https://devpi.library.illinois.edu",
                                                    index: "${env.BRANCH_NAME}_staging",
                                                    pkgName: "${props.Name}",
                                                    pkgVersion: "${props.Version}",
                                                    pkgRegex: "whl",
                                                    detox: false
                                                )
                                            }

                                        echo "Finished testing Built Distribution: .whl"
                                    }
                                }

                            }
                            post {
                                cleanup{
                                    cleanWs(
                                        deleteDirs: true,
                                        disableDeferredWipeout: true,
                                        patterns: [
                                            [pattern: '*tmp', type: 'INCLUDE'],
                                            [pattern: 'certs', type: 'INCLUDE']
                                            ]
                                    )
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
                    }
                    steps {
                        unstash "DIST-INFO"
                        script {
                            def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                            input "Release ${props.Name} ${props.Version} to DevPi Production?"
                            bat "venv\\Scripts\\devpi.exe login ${env.DEVPI_USR} --password ${env.DEVPI_PSW}"
                            bat "venv\\Scripts\\devpi.exe use /DS_Jenkins/${env.BRANCH_NAME}_staging"
                            bat "venv\\Scripts\\devpi.exe push ${props.Name}==${props.Version} production/release"
                        }
                    }
                }
            }
            post {
                success {
                    unstash "DIST-INFO"
                    script {
                        def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                        echo "it Worked. Pushing file to ${env.BRANCH_NAME} index"
                        bat "venv\\Scripts\\devpi.exe login ${env.DEVPI_USR} --password ${env.DEVPI_PSW}"
                        bat "venv\\Scripts\\devpi.exe use /${env.DEVPI_USR}/${env.BRANCH_NAME}_staging"
                        bat "venv\\Scripts\\devpi.exe push ${props.Name}==${props.Version} ${env.DEVPI_USR}/${env.BRANCH_NAME}"
                    }
                }
                cleanup{
                    unstash "DIST-INFO"
                    script {
                        def props = readProperties interpolate: true, file: 'HathiZip.dist-info/METADATA'
                        remove_from_devpi("venv\\Scripts\\devpi.exe", "${props.Name}", "${props.Version}", "/${env.DEVPI_USR}/${env.BRANCH_NAME}_staging", "${env.DEVPI_USR}", "${env.DEVPI_PSW}")
                    }
                }
                failure {
                    echo "At least one package format on DevPi failed."
                }
            }
        }

        stage("Deploy to SCCM") {
            when{
                allOf{
                    equals expected: true, actual: params.DEPLOY_SCCM
                    branch "master"
                }
            }

            steps {
                node("Linux"){
                    unstash "msi"
                    deployStash("msi", "${env.SCCM_STAGING_FOLDER}/${params.PROJECT_NAME}/")
                    input("Deploy to production?")
                    deployStash("msi", "${env.SCCM_UPLOAD_FOLDER}")
                }

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
                expression { params.UPDATE_DOCS == true }
            }

            steps {
                updateOnlineDocs url_subdomain: params.URL_SUBFOLDER, stash_name: "HTML Documentation"

            }
        }
    }
    post{
        cleanup{

//            script {
//                if(fileExists('source/setup.py')){
//                    dir("source"){
//                        try{
//                            retry(3) {
//                                bat "${WORKSPACE}\\venv\\Scripts\\python.exe setup.py clean --all"
//                            }
//                        } catch (Exception ex) {
//                            echo "Unable to successfully run clean. Purging source directory."
//                            deleteDir()
//                        }
//                    }
//                }
//            }
            cleanWs deleteDirs: true, patterns: [
                    [pattern: 'source', type: 'INCLUDE'],
                    [pattern: 'certs', type: 'INCLUDE'],
                    [pattern: 'dist*', type: 'INCLUDE'],
                    [pattern: 'logs*', type: 'INCLUDE'],
                    [pattern: 'reports*', type: 'INCLUDE'],
                    [pattern: '*tmp', type: 'INCLUDE']
                    ]
        }
    }
}
