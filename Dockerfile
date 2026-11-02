FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system odyssey && useradd --system --gid odyssey odyssey
COPY --from=build --chown=odyssey:odyssey /build/target/retail-shop-0.0.1-SNAPSHOT.jar /app/app.jar
USER odyssey
ENV SPRING_PROFILES_ACTIVE=mysql,production
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
