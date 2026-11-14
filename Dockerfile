FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p build/classes && javac -d build/classes src/main/java/dev/handoffguard/*.java
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/classes ./build/classes
COPY web ./web
ENV PORT=8080 HOST=0.0.0.0
EXPOSE 8080
USER 10001
CMD ["java","-cp","build/classes","dev.handoffguard.Server"]
