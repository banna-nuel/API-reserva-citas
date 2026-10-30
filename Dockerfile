# syntax=docker/dockerfile:1

# ---------- compilación ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Primero solo lo que define las dependencias: esta capa se reutiliza mientras pom.xml no cambie
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# mvnw llega con fin de línea LF (.gitattributes); el chmod cubre un checkout sin el bit de ejecución
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src ./src
# Las pruebas corren en el CI (necesitan Docker para Testcontainers); aquí solo se empaqueta
RUN ./mvnw -B -q package -DskipTests -Djacoco.skip=true

# ---------- ejecución ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=America/Bogota
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
