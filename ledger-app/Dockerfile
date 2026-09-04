# Build and run the application. Two stages so the runtime image carries a JRE and a jar,
# not a JDK and a Maven repository.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY config config
COPY ledger-core/pom.xml ledger-core/
COPY ledger-import/pom.xml ledger-import/
COPY ledger-app/pom.xml ledger-app/
RUN mvn -B -q dependency:go-offline -DskipTests || true
COPY ledger-core ledger-core
COPY ledger-import ledger-import
COPY ledger-app ledger-app
RUN mvn -B -q package -DskipTests -Dspotless.check.skip=true -Dspotbugs.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/ledger-app/target/ledger-app-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
