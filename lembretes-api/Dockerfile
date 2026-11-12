# Build: Maven + JDK dentro da imagem (nao precisa de Java na maquina)
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Runtime: so o JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /app/target/lembretes-api-*.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    SPRING_DATASOURCE_URL=jdbc:sqlite:/data/lembretes.db \
    LEMBRETES_CONTEXTO_RAIZ=/contexto
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
