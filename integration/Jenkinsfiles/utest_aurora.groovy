/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     atimic
 */

properties([
        [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', daysToKeepStr: '60', numToKeepStr: '60', artifactNumToKeepStr: '1']],
        disableConcurrentBuilds(),
        [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/nuxeo/nuxeo/'],
        [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
        [$class: 'ParametersDefinitionProperty', parameterDefinitions: [
                // branch hardcoded for now
                [$class: 'StringParameterDefinition', name: 'BRANCH', description: '', defaultValue: 'feature-NXP-22077-add-aurora'],
                // can't work in a pipeline multi-branch with build PR because BRANCH_NAME contains PR-XXXXX
                //[$class: 'StringParameterDefinition', name: 'BRANCH', description: '', defaultValue: BRANCH_NAME],
                [$class: 'StringParameterDefinition', name: 'PARENT_BRANCH', description: '', defaultValue: 'master'],
                [$class: 'StringParameterDefinition', name: 'DBPROFILE', description: '', defaultValue: 'pgsql'],
                [$class: 'StringParameterDefinition', name: 'DBVERSION', description: '', defaultValue: 'unknown'],
                [$class: 'BooleanParameterDefinition', name: 'CLEAN', description: '', defaultValue: true],
                [$class: 'StringParameterDefinition', name: 'NODELABEL', description: '', defaultValue: 'REFBENCH']
        ]]])

currentBuild.setDescription("Branch: $BRANCH -> $PARENT_BRANCH, DB: aurora-$DBPROFILE, VERSION: $DBVERSION")

node(env.NODELABEL) {
    tool name: 'ant-1.9', type: 'ant'
    tool name: 'java-8-openjdk', type: 'hudson.model.JDK'
    tool name: 'maven-3', type: 'hudson.tasks.Maven$MavenInstallation'

    timeout(time: 3, unit: 'HOURS') {
        timestamps {
            def sha = stage('clone') {
                if (CLEAN) {
                    deleteDir()
                }
                checkout(
                        [$class: 'GitSCM',
                         branches: [[name: '*/${BRANCH}']],
                         browser: [$class: 'GithubWeb', repoUrl: 'https://github.com/nuxeo/nuxeo'],
                         doGenerateSubmoduleConfigurations: false,
                         extensions: [
                                 [$class: 'CleanBeforeCheckout'],
                                 [$class: 'CloneOption', noTags: true, reference: '', shallow: false]
                         ],
                         submoduleCfg: [],
                         userRemoteConfigs: [[url: 'git@github.com:nuxeo/nuxeo.git']]
                        ])
                def originsha = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                sh "./clone.py $BRANCH -f $PARENT_BRANCH"
                return originsha
            }

            try {
                stage('tests') {
                    withBuildStatus("utest/aurora-$DBPROFILE-$DBVERSION", 'https://github.com/nuxeo/nuxeo', sha, "${BUILD_URL}") {
                        withEnv(["NX_DB_PORT=5432", "NX_DB_ADMINNAME=nuxeoAurora"]){
                            withCredentials([usernamePassword(credentialsId: 'AURORA_PGSQL', usernameVariable: 'NX_DB_ADMINUSER', passwordVariable: 'NX_DB_ADMINPASS')]) {
                                try {
                                    sh "mvn -B -f $WORKSPACE/pom.xml install -Pqa,addons,customdb,$DBPROFILE -Dmaven.test.failure.ignore=true -Dnuxeo.tests.random.mode=STRICT"
                                } finally {
                                    archive '**/target/failsafe-reports/*, **/target/*.png, **/target/**/*.log, **/target/**/log/*'
                                    junit '**/target/surefire-reports/*.xml, **/target/failsafe-reports/*.xml, **/target/failsafe-reports/**/*.xml'
                                }
                            }
                        }
                    }
                }
            } finally {
                warningsPublisher()
                claimPublisher()
            }
        }
    }
}
