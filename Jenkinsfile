pipeline {
	options {
		timeout(time: 15, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '10'))
		disableConcurrentBuilds(abortPrevious: true)
	}

	agent {
		label 'centos-latest'
	}

	tools {
		jdk 'temurin-jdk21-latest'
	}

	stages {

		stage('Build') {
			steps {
				wrap([$class: 'Xvnc', useXauthority: true]) {
					sh """
						./mvnw clean verify \
							org.eclipse.dash:license-tool-plugin:license-check \
							-B ${env.BRANCH_NAME=='main' ? '-Psign': ''} \
							-Dmaven.test.failure.ignore=true \
							-Ddash.fail=false \
							-Dsurefire.rerunFailingTestsCount=3
					"""
				}
			}
			post {
				always {
					archiveArtifacts artifacts: 'repository/target/repository/**/*,repository/target/*.zip,*/target/work/data/.metadata/.log'
					junit '*/target/surefire-reports/TEST-*.xml'
				}
			}
		}

		stage('Deploy Snapshot') {
			when {
				branch 'main'
			}
			steps {
				sshagent (['projects-storage.eclipse.org-bot-ssh']) {
					sh '''
						DOWNLOAD_AREA=/home/data/httpd/download.eclipse.org/lsp4e/snapshots/
						echo DOWNLOAD_AREA=$DOWNLOAD_AREA
						ssh genie.lsp4e@projects-storage.eclipse.org "\
							rm -rf ${DOWNLOAD_AREA}/* && \
							mkdir -p ${DOWNLOAD_AREA}"
						scp -r repository/target/repository/* genie.lsp4e@projects-storage.eclipse.org:${DOWNLOAD_AREA}
					'''
				}
			}
		}

	}
}
