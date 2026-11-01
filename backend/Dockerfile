FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system studio && useradd --system --gid studio studio && mkdir /app/storage && chown studio:studio /app/storage
COPY --from=build /app/target/material-studio-0.1.0.jar app.jar
USER studio
EXPOSE 8080
ENTRYPOINT ["java","-Djava.awt.headless=true","-jar","/app/app.jar"]
