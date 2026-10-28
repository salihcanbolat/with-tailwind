# The server as one image, for any machine that runs Docker (docker-compose.yml, backend/README.md).
# Built from the repository root: docker build -f backend/Dockerfile .
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon --quiet :backend:installDist

FROM eclipse-temurin:21-jre-noble
COPY --from=build /src/backend/build/install/backend /app
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/backend"]
