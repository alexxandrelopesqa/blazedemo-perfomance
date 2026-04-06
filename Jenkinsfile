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

    stage('Run Load Test') {
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

    stage('Run Peak Test') {
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
                if [ -f /workspace/results/load/load.jtl ]; then
                  python3 /workspace/scripts/jtl_to_allure.py /workspace/results/load/load.jtl /workspace/allure-results "Load Test 250 RPS"
                  generated=1
                fi
                if [ -f /workspace/results/peak/peak.jtl ]; then
                  python3 /workspace/scripts/jtl_to_allure.py /workspace/results/peak/peak.jtl /workspace/allure-results "Peak Test 350 RPS"
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
