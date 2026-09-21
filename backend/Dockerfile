FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline

COPY src src

RUN ./mvnw clean package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "if [ -n \"$OPENAI_API_KEY\" ]; then echo 'OPENAI_API_KEY is available'; else echo 'OPENAI_API_KEY is MISSING'; fi; exec java -jar app.jar"]