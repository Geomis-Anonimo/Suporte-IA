# syntax=docker/dockerfile:1
# Build: Maven + JDK dentro da imagem (nao precisa de Java na maquina). Projeto multi-modulo.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY shared/pom.xml shared/
COPY lembretes/pom.xml lembretes/
COPY tarefas/pom.xml tarefas/
COPY bootstrap/pom.xml bootstrap/
COPY shared/src shared/src
COPY lembretes/src lembretes/src
COPY tarefas/src tarefas/src
COPY bootstrap/src bootstrap/src
# cache do ~/.m2 entre builds (BuildKit)
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

# Runtime: so o JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /build/bootstrap/target/api.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    SPRING_DATASOURCE_URL=jdbc:sqlite:/data/api.db
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
