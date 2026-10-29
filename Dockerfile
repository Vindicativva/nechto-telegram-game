# Multi-stage build for Nechto Backend (Root context)
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY apps/backend/.mvn/ .mvn/
COPY apps/backend/mvnw apps/backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

COPY apps/backend/src ./src
RUN ./mvnw clean package -DskipTests

# Runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN addgroup --system nechto && adduser --system --ingroup nechto nechto
USER nechto:nechto

COPY --from=builder /build/target/*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
