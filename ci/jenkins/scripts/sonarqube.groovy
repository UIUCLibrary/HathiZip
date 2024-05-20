
def get_sonarqube_unresolved_issues(report_task_file){
    script{

        def props = readProperties  file: '.scannerwork/report-task.txt'
        def response = httpRequest url : props['serverUrl'] + '/api/issues/search?componentKeys=' + props['projectKey'] + '&resolved=no'
        def outstandingIssues = readJSON text: response.content
        return outstandingIssues
    }
}


def sonarcloudSubmit(args = [:]){
    def outputJson = args.outputJson ? args.outputJson: 'reports/sonar-report.json'
    def projectVersion = args.projectVersion
    def sonarCredentialsId = args.credentialsId
    def command

    withSonarQubeEnv(installationName:'sonarcloud', credentialsId: sonarCredentialsId) {
        if(args.sonarCommand){
            command = args.sonarCommand
        } else {
            if (env.CHANGE_ID){
                command = "sonar-scanner -Dsonar.projectVersion=${projectVersion} -Dsonar.buildString=\"${env.BUILD_TAG}\" -Dsonar.pullrequest.key=${env.CHANGE_ID} -Dsonar.pullrequest.base=${env.CHANGE_TARGET}"
            } else {
                command = "sonar-scanner -Dsonar.projectVersion=${projectVersion} -Dsonar.buildString=\"${env.BUILD_TAG}\" -Dsonar.branch.name=${env.BRANCH_NAME}"

            }
        }
        sh command
    }
    timeout(time: 1, unit: 'HOURS') {
        def sonarqube_result = waitForQualityGate(abortPipeline: false)
        if (sonarqube_result.status != 'OK') {
            unstable "SonarQube quality gate: ${sonarqube_result.status}"
        }
        def outstandingIssues = get_sonarqube_unresolved_issues('.scannerwork/report-task.txt')
        writeJSON file: outputJson, json: outstandingIssues
    }
}
return this
