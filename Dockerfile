FROM amazoncorretto:18
COPY target/dynamodb-streams-kcl-app-1.0-SNAPSHOT-jar-with-dependencies.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]