pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    disableConcurrentBuilds()
  }

  environment {
    IMAGE_NAME = 'blazedemo-performance:jenkins'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build Image') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
        }
      }
      steps {
        sh 'docker build -f docker/Dockerfile -t ${IMAGE_NAME} .'
      }
    }

    stage('Load test') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
        }
      }
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            mkdir -p results/load
            docker run --rm \
              -v "$PWD/results:/workspace/results" \
              ${IMAGE_NAME} \
              -n -t scripts/load_test.jmx -l results/load/load.jtl -j results/load/jmeter.log -e -o results/load/dashboard
          '''
        }
      }
    }

    stage('Peak test') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
        }
      }
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            mkdir -p results/peak
            docker run --rm \
              -v "$PWD/results:/workspace/results" \
              ${IMAGE_NAME} \
              -n -t scripts/peak_test.jmx -l results/peak/peak.jtl -j results/peak/jmeter.log -e -o results/peak/dashboard
          '''
        }
      }
    }

    stage('Generate Allure Report') {
      when {
        anyOf {
          branch 'main'
          branch 'master'
        }
      }
      steps {
        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
          sh '''
            mkdir -p allure-results results/allure-report
            docker run --rm \
              -v "$PWD/results:/workspace/results" \
              -v "$PWD/allure-results:/workspace/allure-results" \
              ${IMAGE_NAME} \
              sh -c '
                set -eu
                generated=0
                export ACCEPTANCE_P90_MS="${ACCEPTANCE_P90_MS:-8000}"
                if [ -f /workspace/results/load/load.jtl ]; then
                  ACCEPTANCE_RPS="${ACCEPTANCE_RPS_LOAD:-22}" java -jar /opt/jtl-allure/jtl-allure.jar /workspace/results/load/load.jtl /workspace/allure-results "Load 30 RPS"
                  generated=1
                fi
                if [ -f /workspace/results/peak/peak.jtl ]; then
                  ACCEPTANCE_RPS="${ACCEPTANCE_RPS_PEAK:-50}" java -jar /opt/jtl-allure/jtl-allure.jar /workspace/results/peak/peak.jtl /workspace/allure-results "Peak 70 RPS"
                  generated=1
                fi
                if [ "$generated" -eq 1 ]; then
                  allure generate /workspace/allure-results --clean -o /workspace/results/allure-report
                else
                  mkdir -p /workspace/results/allure-report
                  printf "%s\n" "Nenhum JTL gerado para consolidar Allure nesta execucao." > /workspace/results/allure-report/index.html
                fi
              '
          '''
        }
      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: 'results/**/*,allure-results/**/*', allowEmptyArchive: true, fingerprint: true
    }
  }
}
