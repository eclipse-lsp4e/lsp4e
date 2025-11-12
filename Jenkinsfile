// https://github.com/eclipse-cbi/jiro/wiki/CI-Best-Practices
// https://github.com/eclipse-cbi/jiro/wiki/FAQ#how-do-i-run-a-javamaven-build-on-the-cluster-based-infrastructure
pipeline {
	agent {
		// https://eclipse.dev/cbi/jiro-agent/
		// https://github.com/eclipse-cbi/jiro-agents/
		// https://github.com/eclipse-cbi/jiro-agents/tree/master/ubuntu
		label 'ubuntu-latest'
	}

	// https://www.jenkins.io/doc/book/pipeline/syntax/#triggers
	// https://www.jenkins.io/doc/pipeline/steps/params/pipelinetriggers/
	triggers {
		githubPush()
	}

	// https://www.jenkins.io/doc/book/pipeline/syntax/#options
	options {
		timeout(time: 20, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '10'))
		disableConcurrentBuilds(abortPrevious: true)
	}

	tools {
		// https://github.com/eclipse-cbi/jiro/wiki/Tools-(JDK,-Maven,-Ant)#apache-maven
		// https://eclipse.dev/cbi/jiro/Tools/#apache-maven
		maven 'apache-maven-latest'

		// https://github.com/eclipse-cbi/jiro/wiki/Tools-(JDK,-Maven,-Ant)#eclipse-temurin
		// https://eclipse.dev/cbi/jiro/Tools/#eclipse-temurin
		jdk 'temurin-jdk21-latest'
	}

	stages {

		stage('Build') {
			steps {
				// https://github.com/eclipse-cbi/jiro/wiki/FAQ#how-do-i-run-ui-tests-on-the-cluster-based-infrastructure
				wrap([$class: 'Xvnc', takeScreenshot: false, useXauthority: true]) {
					script {
						def maven_opts = (env.MAVEN_OPTS ?: '')

						if (isUnix()) {
							// https://www.baeldung.com/java-security-egd#bd-testing-the-effect-of-javasecurityegd
							maven_opts += ' -Djava.security.egd=file:/dev/./urandom'
						} else {
							// https://stackoverflow.com/questions/58991966/what-java-security-egd-option-is-for/59097932#59097932
							maven_opts += ' -Djava.security.egd=file:/dev/urandom'
						}

						// https://stackoverflow.com/questions/5120470/how-to-time-the-different-stages-of-maven-execution/49494561#49494561
						maven_opts += ' -Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS'

						maven_opts += ' -Xmx1024m -Djava.awt.headless=true -Djava.net.preferIPv4Stack=true -Dhttps.protocols=TLSv1.3,TLSv1.2'

						def maven_args = [
							'--no-transfer-progress',
							'--errors',
							'--update-snapshots',
							'--batch-mode',
							'--show-version',
							'-Declipse.p2.mirrors=false',
							'-Dsurefire.rerunFailingTestsCount=3',
						].join(' ')

						withEnv(["MAVEN_OPTS=${maven_opts}"]) {
							echo "MAVEN_OPTS: ${env.MAVEN_OPTS}"

							sh "./mvnw ${maven_args} ${env.BRANCH_NAME=='main' ? '-Psign': ''} clean verify"
						}
					}
				}
			}

			post {
				always {
					archiveArtifacts artifacts: 'repository/target/repository/**/*,repository/target/*.zip,*/target/work/data/.metadata/.log'
					junit '**/target/surefire-reports/TEST-*.xml'
				}
			}
		}

		stage('Deploy Snapshot') {
			when {
				branch 'main'
			}
			steps {
				// https://github.com/eclipse-cbi/jiro/wiki/FAQ#how-do-i-deploy-artifacts-to-downloadeclipseorg
				sshagent (['projects-storage.eclipse.org-bot-ssh']) {
					sh '''
						DOWNLOAD_AREA=/home/data/httpd/download.eclipse.org/lsp4e/snapshots/
						echo DOWNLOAD_AREA=$DOWNLOAD_AREA
						ssh genie.lsp4e@projects-storage.eclipse.org "\
							rm -rf ${DOWNLOAD_AREA}/* && \
							mkdir -p ${DOWNLOAD_AREA}"
						scp -r repository/target/repository/* genie.lsp4e@projects-storage.eclipse.org:${DOWNLOAD_AREA}
						scp repository/target/repository-*-SNAPSHOT.zip genie.lsp4e@projects-storage.eclipse.org:${DOWNLOAD_AREA}/repository.zip
					'''
				}
			}
		}
	}
}
