@Library("ds-utils")
import org.ds.*

pipeline {
    agent any
    environment {
        mypy_args = "--junit-xml=mypy.xml"
        pytest_args = "--junitxml=reports/junit-{env:OS:UNKNOWN_OS}-{envname}.xml --junit-prefix={env:OS:UNKNOWN_OS}  --basetemp={envtmpdir}"
    }
    parameters {
        string(name: "PROJECT_NAME", defaultValue: "HathiTrust Zip for Submit", description: "Name given to the project")
        choice(choices: 'Windowstest\nWindows', description: "Jenkins build node label requirement.", name: "windowsLabel")
        booleanParam(name: "UNIT_TESTS", defaultValue: true, description: "Run automated unit tests")
        booleanParam(name: "ADDITIONAL_TESTS", defaultValue: true, description: "Run additional tests")
        booleanParam(name: "PACKAGE", defaultValue: true, description: "Create a package")
        booleanParam(name: "DEPLOY_SCCM", defaultValue: false, description: "Create SCCM deployment package")
        booleanParam(name: "DEPLOY_DEVPI", defaultValue: false, description: "Deploy to devpi on http://devpy.library.illinois.edu/DS_Jenkins/${env.BRANCH_NAME}")
        booleanParam(name: "UPDATE_DOCS", defaultValue: false, description: "Update online documentation")
        string(name: 'URL_SUBFOLDER', defaultValue: "hathi_zip", description: 'The directory that the docs should be saved under')

    }
    stages {

        stage("Cloning Source") {
            agent any

            steps {
                deleteDir()
                checkout scm
                stash includes: '**', name: "Source", useDefaultExcludes: false
            }

        }

        stage("Unit tests") {
            when {
                expression { params.UNIT_TESTS == true }
            }
            steps {
                parallel(
                        "Windows": {
                            script {
                                def runner = new Tox(this)
                                runner.env = "pytest"
                                runner.windows = true
                                runner.stash = "Source"
                                runner.label = "${params.windowsLabel}"
                                runner.post = {
                                    junit 'reports/junit-*.xml'
                                }
                                runner.run()
                            }
                        },
                        "Linux": {
                            script {
                                def runner = new Tox(this)
                                runner.env = "pytest"
                                runner.windows = false
                                runner.stash = "Source"
                                runner.label = "Linux"
                                runner.post = {
                                    junit 'reports/junit-*.xml'
                                }
                                runner.run()
                            }
                        }
                )
            }
        }

        stage("Additional tests") {
            when {
                expression { params.ADDITIONAL_TESTS == true }
            }

            steps {
                parallel(
                        "Documentation": {
                            script {
                                def runner = new Tox(this)
                                runner.env = "docs"
                                runner.windows = false
                                runner.stash = "Source"
                                runner.label = "Linux"
                                runner.post = {
                                    dir('.tox/dist/html/') {
                                        stash includes: '**', name: "HTML Documentation", useDefaultExcludes: false
                                    }
                                }
                                runner.run()

                            }
                        },
                        "MyPy": {
                            script {
                                def runner = new Tox(this)
                                runner.env = "mypy"
                                runner.windows = false
                                runner.stash = "Source"
                                runner.label = "Linux"
                                runner.post = {
                                    junit 'mypy.xml'
                                }
                                runner.run()

                            }
                        }

                )
            }
        }

        stage("Packaging") {
            when {
                expression { params.PACKAGE == true }
            }

            steps {
                parallel(
                        "Windows Wheel": {
                            node(label: "${params.windowsLabel}") {
                                deleteDir()
                                unstash "Source"
                                bat """${tool 'Python3.6.3_Win64'} -m venv .env
                                        call .env/Scripts/activate.bat
                                        pip install --upgrade pip setuptools
                                        pip install -r requirements.txt
                                        python setup.py bdist_wheel --universal
                                    """
                                archiveArtifacts artifacts: "dist/**", fingerprint: true
                            }
                        },
                        "Windows CX_Freeze MSI": {
                            node(label: "${params.windowsLabel}") {
                                deleteDir()
                                unstash "Source"
                                bat """${tool 'Python3.6.3_Win64'} -m venv .env
                                       call .env/Scripts/activate.bat
                                       pip install -r requirements.txt
                                       python cx_setup.py bdist_msi --add-to-path=true -k --bdist-dir build/msi
                                       call .env/Scripts/deactivate.bat
                                    """
                                bat "build\\msi\\hathizip.exe --pytest"
                                dir("dist") {
                                    stash includes: "*.msi", name: "msi"
                                }

                            }
                            node(label: "${params.windowsLabel}") {
                                deleteDir()
                                git url: 'https://github.com/UIUCLibrary/ValidateMSI.git'
                                unstash "msi"
                                bat "call validate.bat -i"
                                archiveArtifacts artifacts: "*.msi", fingerprint: true
                            }
                        },
                        "Source Release": {
                            node(label: "Linux"){
                                createSourceRelease(env.PYTHON3, "Source")
                            }

                        }
                )
            }
        }

        stage("Deploy - Staging") {
            agent {
                label "Linux"
            }
            when {
                expression { params.DEPLOY_SCCM == true && params.PACKAGE == true }
            }

            steps {
                deployStash("msi", "${env.SCCM_STAGING_FOLDER}/${params.PROJECT_NAME}/")
                input("Deploy to production?")
            }
        }

        stage("Deploy - SCCM upload") {
            agent {
                label "Linux"
            }
            when {
                expression { params.DEPLOY_SCCM == true && params.PACKAGE == true }
            }

            steps {
                deployStash("msi", "${env.SCCM_UPLOAD_FOLDER}")
            }

            post {
                success {
                    script{
                        unstash "Source"
                        def  deployment_request = requestDeploy this, "deployment.yml"
                        echo deployment_request
                        writeFile file: "deployment_request.txt", text: deployment_request
                        archiveArtifacts artifacts: "deployment_request.txt"
                    }
                }
            }
        }
//        stage("Deploying to Devpi") {
//            agent {
//                node {
//                    label 'Windows&&DevPi'
//                }
//            }
//            when {
//                expression { params.DEPLOY_DEVPI == true }
//            }
//            steps {
//                deleteDir()
//                unstash "Source"
//                bat "devpi use http://devpy.library.illinois.edu"
//                withCredentials([usernamePassword(credentialsId: 'DS_devpi', usernameVariable: 'DEVPI_USERNAME', passwordVariable: 'DEVPI_PASSWORD')]) {
//                    bat "devpi login ${DEVPI_USERNAME} --password ${DEVPI_PASSWORD}"
//                    bat "devpi use /${DEVPI_USERNAME}/${env.BRANCH_NAME}"
//                    script {
//                        try{
//                            bat "devpi upload --with-docs"
//
//                        } catch (exc) {
//                            echo "Unable to upload to devpi with docs. Trying without"
//                            bat "devpi upload"
//                        }
//                    }
//                    bat "devpi test HathiZip"
//                }
//
//            }
//        }
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
}
