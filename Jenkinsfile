pipeline {
    agent any

    tools {
        maven 'M3'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 15, unit: 'MINUTES')
        buildDiscarder(
            logRotator(
                numToKeepStr: '10',
                artifactNumToKeepStr: '5'
            )
        )
    }

    environment {
        // Aplicación
        APP_NAME = 'usuarios-rest'
        APP_CONTEXT_PATH = 'usuariosBuild'
        WAR_FILE = 'usuariosBuild.war'

        // Docker
        IMAGE_NAME = 'imagen_usuarios'
        IMAGE_TAG = "${BUILD_NUMBER}"
        CONTAINER_NAME = 'contenedor_usuarios'

        // Contenedor anterior utilizado por el proyecto previo
        LEGACY_CONTAINER_NAME = 'contenedor_sucursal'

        // Red
        APP_PORT = '9090'
        CONTAINER_PORT = '8080'

        // Rutas funcionales
        OPENAPI_PATH = 'api-docs'
        USERS_PATH = 'user'

        // Base de datos esperada
        EXPECTED_DATABASE = 'municipalidad_la_florida'
    }

    stages {

        stage('Checkout Source Code') {
            steps {
                echo 'Descargando código fuente desde GitHub...'

                checkout scmGit(
                    branches: [[name: '*/main']],
                    extensions: [
                        [$class: 'CleanBeforeCheckout']
                    ],
                    userRemoteConfigs: [[
                        credentialsId: 'dev-github-java-jenkins-pat',
                        url: 'https://github.com/dannyfriz/java-jenkins'
                    ]]
                )

                sh '''
                    set -eu

                    echo "Commit utilizado:"
                    git log -1 --pretty=format:'%H - %an - %s'
                    echo ""
                '''
            }
        }

        stage('Validate Required Tools') {
            steps {
                sh '''
                    set -eu

                    echo "Validando herramientas requeridas por el pipeline..."

                    REQUIRED_COMMANDS="git java mvn docker curl mysql"

                    for COMMAND in $REQUIRED_COMMANDS; do
                        if ! command -v "$COMMAND" >/dev/null 2>&1; then
                            echo "ERROR: No se encontró la herramienta: $COMMAND"
                            exit 1
                        fi
                    done

                    echo "Versiones detectadas:"
                    git --version
                    java -version
                    mvn --version
                    docker --version
                    curl --version | head -n 1
                    mysql --version

                    echo "Herramientas validadas correctamente."
                '''
            }
        }

        stage('Validate RDS MySQL Connection') {
            steps {
                withCredentials([
                    string(
                        credentialsId: 'dev-rds-mysql-sucursal-jdbc-url',
                        variable: 'SPRING_DATASOURCE_URL'
                    ),
                    usernamePassword(
                        credentialsId: 'dev-rds-mysql-sucursal-credentials',
                        usernameVariable: 'SPRING_DATASOURCE_USERNAME',
                        passwordVariable: 'SPRING_DATASOURCE_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        set -eu

                        echo "Validando credenciales y conexión a Amazon RDS MySQL..."

                        case "$SPRING_DATASOURCE_URL" in
                            jdbc:mysql://*)
                                echo "Formato JDBC MySQL detectado correctamente."
                                ;;
                            *)
                                echo "ERROR: SPRING_DATASOURCE_URL no comienza con jdbc:mysql://"
                                exit 1
                                ;;
                        esac

                        JDBC_WITHOUT_PREFIX="${SPRING_DATASOURCE_URL#jdbc:mysql://}"
                        JDBC_WITHOUT_PARAMS="${JDBC_WITHOUT_PREFIX%%\\?*}"

                        MYSQL_HOST_PORT="${JDBC_WITHOUT_PARAMS%%/*}"
                        MYSQL_DATABASE="${JDBC_WITHOUT_PARAMS#*/}"
                        MYSQL_HOST="${MYSQL_HOST_PORT%%:*}"

                        if echo "$MYSQL_HOST_PORT" | grep -q ':'; then
                            MYSQL_PORT="${MYSQL_HOST_PORT##*:}"
                        else
                            MYSQL_PORT="3306"
                        fi

                        if [ -z "$MYSQL_HOST" ]; then
                            echo "ERROR: No fue posible obtener el host desde la URL JDBC."
                            exit 1
                        fi

                        if [ -z "$MYSQL_DATABASE" ] || \
                           [ "$MYSQL_DATABASE" = "$MYSQL_HOST_PORT" ]; then
                            echo "ERROR: No fue posible obtener la base de datos desde la URL JDBC."
                            exit 1
                        fi

                        echo "Host RDS detectado: $MYSQL_HOST"
                        echo "Puerto RDS detectado: $MYSQL_PORT"
                        echo "Base de datos detectada: $MYSQL_DATABASE"

                        if [ "$MYSQL_DATABASE" != "$EXPECTED_DATABASE" ]; then
                            echo "ERROR: La URL JDBC no apunta a la base esperada."
                            echo "Base esperada: $EXPECTED_DATABASE"
                            echo "Base detectada: $MYSQL_DATABASE"
                            exit 1
                        fi

                        echo "Probando autenticación contra Amazon RDS MySQL..."

                        MYSQL_PWD="$SPRING_DATASOURCE_PASSWORD" mysql \
                            --protocol=TCP \
                            --connect-timeout=10 \
                            -h "$MYSQL_HOST" \
                            -P "$MYSQL_PORT" \
                            -u "$SPRING_DATASOURCE_USERNAME" \
                            "$MYSQL_DATABASE" \
                            -e "
                                SELECT
                                    'Conexion RDS MySQL OK' AS resultado,
                                    DATABASE() AS base_datos;

                                SELECT
                                    COUNT(*) AS usuarios_registrados
                                FROM users;
                            "

                        echo "Conexión con Amazon RDS validada correctamente."
                    '''
                }
            }
        }

        stage('Build Usuarios WAR Artifact') {
            steps {
                sh '''
                    set -eu

                    echo "Compilando aplicación Usuarios REST..."

                    mvn clean package -DskipTests

                    echo "Validando artefacto WAR..."

                    if [ ! -f "target/$WAR_FILE" ]; then
                        echo "ERROR: No se generó target/$WAR_FILE"

                        echo "Contenido del directorio target:"
                        ls -lah target || true

                        exit 1
                    fi

                    ls -lh "target/$WAR_FILE"

                    echo "Validando que el archivo no esté vacío..."

                    if [ ! -s "target/$WAR_FILE" ]; then
                        echo "ERROR: El archivo WAR está vacío."
                        exit 1
                    fi

                    echo "Artefacto WAR generado correctamente."
                '''
            }
        }

        stage('Build Usuarios Docker Image') {
            steps {
                sh '''
                    set -eu

                    FULL_IMAGE_NAME="$IMAGE_NAME:$IMAGE_TAG"

                    echo "Construyendo imagen Docker:"
                    echo "$FULL_IMAGE_NAME"

                    docker build \
                        --pull \
                        -t "$FULL_IMAGE_NAME" \
                        -t "$IMAGE_NAME:latest" \
                        .

                    echo "Validando imagen creada..."

                    docker image inspect "$FULL_IMAGE_NAME" >/dev/null

                    docker images "$IMAGE_NAME"

                    echo "Imagen Docker construida correctamente."
                '''
            }
        }

        stage('Prepare Deployment Port') {
            steps {
                sh '''
                    set -eu

                    echo "Preparando el puerto $APP_PORT para el despliegue..."

                    echo "Eliminando contenedor actual del proyecto si existe..."
                    docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

                    echo "Eliminando contenedor anterior conocido si existe..."
                    docker rm -f "$LEGACY_CONTAINER_NAME" >/dev/null 2>&1 || true

                    echo "Buscando contenedores que publiquen el puerto $APP_PORT..."

                    PORT_CONTAINERS="$(
                        docker ps \
                            --filter "publish=$APP_PORT" \
                            --format '{{.Names}}' || true
                    )"

                    if [ -n "$PORT_CONTAINERS" ]; then
                        echo "ERROR: El puerto $APP_PORT sigue ocupado."
                        echo "Contenedor o contenedores detectados:"
                        echo "$PORT_CONTAINERS"

                        echo "Detalle:"
                        docker ps \
                            --filter "publish=$APP_PORT" \
                            --format \
                            'Nombre={{.Names}} | Imagen={{.Image}} | Puertos={{.Ports}}'

                        echo ""
                        echo "No se eliminaron automáticamente contenedores desconocidos."
                        echo "Revise si corresponde detenerlos o utilice otro APP_PORT."

                        exit 1
                    fi

                    echo "Validando procesos del host que podrían utilizar el puerto..."

                    if command -v ss >/dev/null 2>&1; then
                        if ss -ltn | grep -q ":$APP_PORT "; then
                            echo "ERROR: Un proceso del sistema está utilizando el puerto $APP_PORT."
                            ss -ltnp | grep ":$APP_PORT " || true
                            exit 1
                        fi
                    fi

                    echo "Puerto $APP_PORT disponible."
                '''
            }
        }

        stage('Deploy Usuarios Container') {
            steps {
                withCredentials([
                    string(
                        credentialsId: 'dev-rds-mysql-sucursal-jdbc-url',
                        variable: 'SPRING_DATASOURCE_URL'
                    ),
                    usernamePassword(
                        credentialsId: 'dev-rds-mysql-sucursal-credentials',
                        usernameVariable: 'SPRING_DATASOURCE_USERNAME',
                        passwordVariable: 'SPRING_DATASOURCE_PASSWORD'
                    )
                ]) {
                    sh '''
                        set +x
                        set -eu

                        FULL_IMAGE_NAME="$IMAGE_NAME:$IMAGE_TAG"

                        echo "Levantando contenedor de Usuarios REST..."

                        # Limpieza adicional por seguridad ante una ejecución anterior fallida.
                        docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

                        if ! docker run -d \
                            --name "$CONTAINER_NAME" \
                            --restart unless-stopped \
                            -p "$APP_PORT:$CONTAINER_PORT" \
                            -e SPRING_DATASOURCE_URL="$SPRING_DATASOURCE_URL" \
                            -e SPRING_DATASOURCE_USERNAME="$SPRING_DATASOURCE_USERNAME" \
                            -e SPRING_DATASOURCE_PASSWORD="$SPRING_DATASOURCE_PASSWORD" \
                            "$FULL_IMAGE_NAME"; then

                            echo "ERROR: No fue posible iniciar el contenedor."

                            docker rm -f "$CONTAINER_NAME" \
                                >/dev/null 2>&1 || true

                            exit 1
                        fi

                        echo "Contenedor creado correctamente."

                        docker ps \
                            --filter "name=^/${CONTAINER_NAME}$" \
                            --format \
                            'Nombre={{.Names}} | Imagen={{.Image}} | Estado={{.Status}} | Puertos={{.Ports}}'
                    '''
                }
            }
        }

        stage('Validate Container Status') {
            steps {
                sh '''
                    set -eu

                    echo "Validando estado del contenedor..."

                    CONTAINER_STATUS="$(
                        docker inspect \
                            --format '{{.State.Status}}' \
                            "$CONTAINER_NAME"
                    )"

                    echo "Estado detectado: $CONTAINER_STATUS"

                    if [ "$CONTAINER_STATUS" != "running" ]; then
                        echo "ERROR: El contenedor no se encuentra en ejecución."

                        echo "Información del contenedor:"
                        docker inspect "$CONTAINER_NAME" || true

                        echo "Logs disponibles:"
                        docker logs --tail=150 "$CONTAINER_NAME" || true

                        exit 1
                    fi

                    echo "Contenedor en ejecución."
                '''
            }
        }

        stage('Validate WAR Inside Container') {
            steps {
                sh '''
                    set -eu

                    WAR_PATH="/usr/local/tomcat/webapps/$WAR_FILE"

                    echo "Validando el archivo WAR dentro del contenedor..."
                    echo "Ruta esperada: $WAR_PATH"

                    docker exec "$CONTAINER_NAME" \
                        test -f "$WAR_PATH"

                    docker exec "$CONTAINER_NAME" \
                        ls -lh "$WAR_PATH"

                    echo "Validando directorio de aplicaciones de Tomcat..."

                    docker exec "$CONTAINER_NAME" \
                        ls -lah /usr/local/tomcat/webapps/

                    echo "El archivo WAR está disponible dentro de Tomcat."
                '''
            }
        }

        stage('Wait for Application Startup') {
            steps {
                sh '''
                    set -eu

                    OPENAPI_URL="http://localhost:$APP_PORT/$APP_CONTEXT_PATH/$OPENAPI_PATH"

                    echo "Esperando el despliegue de Tomcat y Spring Boot..."
                    echo "URL de validación: $OPENAPI_URL"

                    ATTEMPT=1
                    MAX_ATTEMPTS=18
                    WAIT_SECONDS=5

                    while [ "$ATTEMPT" -le "$MAX_ATTEMPTS" ]; do
                        CONTAINER_STATUS="$(
                            docker inspect \
                                --format '{{.State.Status}}' \
                                "$CONTAINER_NAME"
                        )"

                        if [ "$CONTAINER_STATUS" != "running" ]; then
                            echo "ERROR: El contenedor dejó de ejecutarse."
                            docker logs --tail=200 "$CONTAINER_NAME" || true
                            exit 1
                        fi

                        HTTP_STATUS="$(
                            curl \
                                --silent \
                                --output /dev/null \
                                --write-out '%{http_code}' \
                                "$OPENAPI_URL" || true
                        )"

                        if [ "$HTTP_STATUS" = "200" ]; then
                            echo "Aplicación disponible en el intento $ATTEMPT."
                            echo "Respuesta HTTP: $HTTP_STATUS"
                            exit 0
                        fi

                        echo "Intento $ATTEMPT/$MAX_ATTEMPTS: HTTP $HTTP_STATUS"
                        echo "Esperando $WAIT_SECONDS segundos..."

                        sleep "$WAIT_SECONDS"
                        ATTEMPT=$((ATTEMPT + 1))
                    done

                    echo "ERROR: La aplicación no respondió con HTTP 200."
                    echo "URL probada: $OPENAPI_URL"

                    echo "Últimos logs del contenedor:"
                    docker logs --tail=200 "$CONTAINER_NAME" || true

                    exit 1
                '''
            }
        }

        stage('Inspect Container and Application Logs') {
            steps {
                sh '''
                    set -eu

                    echo "Estado del contenedor:"

                    docker ps \
                        --filter "name=^/${CONTAINER_NAME}$" \
                        --format \
                        'Nombre={{.Names}} | Imagen={{.Image}} | Estado={{.Status}} | Puertos={{.Ports}}'

                    echo ""
                    echo "Últimos logs de Tomcat y Spring Boot:"

                    docker logs --tail=120 "$CONTAINER_NAME"
                '''
            }
        }

        stage('Verify OpenAPI Documentation') {
            steps {
                sh '''
                    set -eu

                    OPENAPI_URL="http://localhost:$APP_PORT/$APP_CONTEXT_PATH/$OPENAPI_PATH"

                    echo "Validando documento OpenAPI..."
                    echo "$OPENAPI_URL"

                    HTTP_STATUS="$(
                        curl \
                            --silent \
                            --output openapi.json \
                            --write-out '%{http_code}' \
                            "$OPENAPI_URL"
                    )"

                    if [ "$HTTP_STATUS" != "200" ]; then
                        echo "ERROR: OpenAPI respondió con HTTP $HTTP_STATUS."
                        exit 1
                    fi

                    if [ ! -s openapi.json ]; then
                        echo "ERROR: El documento OpenAPI está vacío."
                        exit 1
                    fi

                    echo "OpenAPI disponible con HTTP 200."
                    echo "Tamaño del documento:"
                    ls -lh openapi.json
                '''
            }
        }

        stage('Run Usuarios API Smoke Test') {
            steps {
                sh '''
                    set -eu

                    USERS_URL="http://localhost:$APP_PORT/$APP_CONTEXT_PATH/$USERS_PATH"

                    echo "Validando endpoint GET de usuarios..."
                    echo "$USERS_URL"

                    HTTP_STATUS="$(
                        curl \
                            --silent \
                            --output users-response.json \
                            --write-out '%{http_code}' \
                            "$USERS_URL"
                    )"

                    if [ "$HTTP_STATUS" != "200" ]; then
                        echo "ERROR: GET /user respondió con HTTP $HTTP_STATUS."

                        echo "Respuesta recibida:"
                        cat users-response.json || true

                        exit 1
                    fi

                    if [ ! -s users-response.json ]; then
                        echo "ERROR: GET /user devolvió una respuesta vacía."
                        exit 1
                    fi

                    echo "GET /user respondió correctamente con HTTP 200."

                    echo "Respuesta:"
                    cat users-response.json
                    echo ""
                '''
            }
        }

        stage('Publish Deployment Summary') {
            steps {
                sh '''
                    set -eu

                    echo "=============================================="
                    echo "RESUMEN DEL DESPLIEGUE"
                    echo "=============================================="
                    echo "Aplicación:       $APP_NAME"
                    echo "Imagen:           $IMAGE_NAME:$IMAGE_TAG"
                    echo "Contenedor:       $CONTAINER_NAME"
                    echo "Puerto host:      $APP_PORT"
                    echo "Puerto Tomcat:    $CONTAINER_PORT"
                    echo "Context path:     /$APP_CONTEXT_PATH"
                    echo ""
                    echo "OpenAPI:"
                    echo "http://localhost:$APP_PORT/$APP_CONTEXT_PATH/$OPENAPI_PATH"
                    echo ""
                    echo "Usuarios:"
                    echo "http://localhost:$APP_PORT/$APP_CONTEXT_PATH/$USERS_PATH"
                    echo ""
                    echo "Swagger UI:"
                    echo "http://localhost:$APP_PORT/$APP_CONTEXT_PATH/swagger-ui/index.html"
                    echo "=============================================="
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline de Usuarios REST finalizado correctamente.'

            archiveArtifacts(
                artifacts: 'target/usuariosBuild.war,openapi.json,users-response.json',
                fingerprint: true,
                onlyIfSuccessful: true
            )
        }

        failure {
            sh '''
                echo "El pipeline presentó un error."

                if docker ps -a \
                    --format '{{.Names}}' \
                    | grep -qx "$CONTAINER_NAME"; then

                    echo "Estado del contenedor:"

                    docker ps -a \
                        --filter "name=^/${CONTAINER_NAME}$" || true

                    echo "Últimos logs del contenedor:"

                    docker logs \
                        --tail=200 \
                        "$CONTAINER_NAME" || true
                fi

                echo "Contenedores que utilizan el puerto $APP_PORT:"

                docker ps \
                    --filter "publish=$APP_PORT" \
                    --format \
                    'Nombre={{.Names}} | Imagen={{.Image}} | Puertos={{.Ports}}' || true
            '''
        }

        always {
            sh '''
                echo "Estado final de Docker:"

                docker ps -a \
                    --filter "name=^/${CONTAINER_NAME}$" || true
            '''

            junit(
                testResults: 'target/surefire-reports/*.xml',
                allowEmptyResults: true
            )
        }

        cleanup {
            sh '''
                echo "Limpiando contenedores fallidos en estado Created o Exited..."

                if docker ps -a \
                    --filter "name=^/${CONTAINER_NAME}$" \
                    --filter "status=created" \
                    --format '{{.Names}}' \
                    | grep -qx "$CONTAINER_NAME"; then

                    docker rm -f "$CONTAINER_NAME" || true
                fi
            '''
        }
    }
}