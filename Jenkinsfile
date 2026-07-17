pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'M3'
    }

    environment {
        APP_NAME = 'usuariosBuild'
        IMAGE_NAME = 'usuarios-api'
        CONTAINER_NAME = 'usuarios-api'
        HOST_PORT = '9090'
        CONTAINER_PORT = '8080'
        WAR_NAME = 'usuariosBuild.war'
        PATH = "/usr/bin:/usr/local/bin:${env.PATH}"
    }

    stages {
        stage('1. Validar herramientas') {
            steps {
                sh '''
                    echo "JAVA_HOME configurado por Jenkins:"
                    echo $JAVA_HOME
                    which java
                    java -version

                    echo "Validando Maven M3:"
                    which mvn
                    mvn -version

                    echo "Validando Docker:"
                    which docker
                    docker --version
                    docker ps
                '''
            }
        }

        stage('2. Checkout') {
            steps {
                echo 'Descargando código fuente desde GitHub...'
                checkout scm
            }
        }

        stage('3. Build Maven') {
            steps {
                echo 'Compilando proyecto Spring Boot con Maven...'
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('4. Validar WAR') {
            steps {
                sh '''
                    echo "Validando archivo WAR generado..."
                    ls -lh target/
                    test -f target/${WAR_NAME}
                '''
            }
        }

        stage('5. Build Docker Image') {
            steps {
                echo 'Construyendo imagen Docker...'
                sh '''
                    docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} .
                    docker images | grep ${IMAGE_NAME}
                '''
            }
        }

        stage('6. Deploy Docker Container') {
            steps {
                echo 'Desplegando contenedor Docker en puerto 9090...'

                withCredentials([
                    string(
                        credentialsId: 'rds-url',
                        variable: 'DB_URL'
                    ),
                    usernamePassword(
                        credentialsId: 'rds-mysql',
                        usernameVariable: 'DB_USER',
                        passwordVariable: 'DB_PASS'
                    )
                ]) {
                    sh '''
                        docker stop ${CONTAINER_NAME} || true
                        docker rm ${CONTAINER_NAME} || true

                        docker run -d \
                          --name ${CONTAINER_NAME} \
                          -p ${HOST_PORT}:${CONTAINER_PORT} \
                          -e DB_URL="${DB_URL}" \
                          -e DB_USER="${DB_USER}" \
                          -e DB_PASS="${DB_PASS}" \
                          --restart unless-stopped \
                          ${IMAGE_NAME}:${BUILD_NUMBER}

                        docker ps
                    '''
                }
            }
        }

        stage('7. Validar Swagger') {
            steps {
                echo 'Validando disponibilidad de Swagger...'
                sh '''
                    sleep 20
                    curl -I http://localhost:${HOST_PORT}/${APP_NAME}/swagger-ui/index.html || true
                    docker logs --tail 100 ${CONTAINER_NAME}
                '''
            }
        }
    }

    post {
        always {
            echo 'Pipeline finalizado. Revisar consola y logs.'
            archiveArtifacts artifacts: 'target/*.war', fingerprint: true
        }

        success {
            echo 'Pipeline ejecutado correctamente.'
        }

        failure {
            echo 'Pipeline con error. Revisar etapa fallida.'
        }
    }
}
