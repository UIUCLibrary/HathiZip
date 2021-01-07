def getNodeLabel(agent){
    def label
    if (agent.containsKey("dockerfile")){
        return agent.dockerfile.label
    }
    return label
}
def getToxEnv(args){
    try{
        def pythonVersion = args.pythonVersion.replace(".", "")
        return "py${pythonVersion}"
    } catch(e){
        return "py"
    }
}

def getAgent(args){
    def nodeLabel = getNodeLabel(args.agent)
    return { inner ->
        node(nodeLabel){
            ws{
                checkout scm
                def dockerImage
//                 def dockerImageName = "dummy"
                def dockerImageName = "dummy_${getToxEnv(args)}"
                echo "Getting docker build args"
                def dockerBuildArg = "-f ${args.agent.dockerfile.filename} ${args.agent.dockerfile.additionalBuildArgs} ."
        //         TODO: change the docker image name
                echo "Running docker with the following args ${dockerBuildArg}"
                lock("docker build-${env.NODE_NAME}"){
                    dockerImage = docker.build(dockerImageName, dockerBuildArg)
                }
                dockerImage.inside{
                    inner()
                }
            }
        }
    }
}

def testPkg(args = [:]){
    def agentRunner = getAgent(args)
    agentRunner {
        checkout scm
        unstash "${args.stash}"
        findFiles(glob: args.glob).each{
            def toxCommand = "tox --installpkg ${it.path} -e ${getToxEnv(args)}"
            if(isUnix()){
                sh(label: "Testing tox version", script: "tox --version")
                toxCommand = toxCommand + " --workdir /tmp/tox"
                sh(label: "Running Tox", script: toxCommand)
            } else{
                bat(label: "Testing tox version", script: "tox --version")
                toxCommand = toxCommand + " --workdir %TEMP%\\tox"
                bat(label: "Running Tox", script: toxCommand)
            }
        }
    }
}

return [
    testPkg: this.&testPkg
]