FROM tomcat:10.1.23-jre21
MAINTAINER Duoc <usuarios>
EXPOSE 8080
COPY target/usuariosBuild.war  /usr/local/tomcat/webapps/usuariosBuild.war 
