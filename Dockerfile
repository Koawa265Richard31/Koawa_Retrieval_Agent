FROM maven:3.9.16-eclipse-temurin-17-noble AS build

WORKDIR /workspace
COPY pom.xml ./
COPY bootstrap/pom.xml bootstrap/pom.xml
COPY framework/pom.xml framework/pom.xml
COPY infra-ai/pom.xml infra-ai/pom.xml
COPY mcp-server/pom.xml mcp-server/pom.xml
COPY resources/format resources/format
COPY bootstrap/src bootstrap/src
COPY framework/src framework/src
COPY infra-ai/src infra-ai/src
COPY mcp-server/src mcp-server/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl bootstrap,mcp-server -am -DskipTests package

FROM eclipse-temurin:17-jre-ubi9-minimal AS app

WORKDIR /app
COPY --from=build /workspace/bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar /app/app.jar
COPY deploy/application-docker.yaml /app/config/application-docker.yaml
USER 10001:10001
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:17-jre-ubi9-minimal AS mcp-server

WORKDIR /app
COPY --from=build /workspace/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 9099
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM node:24-alpine AS frontend-build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
ARG VITE_API_BASE_URL=/api/koawa-agent
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

FROM nginx:1.28-alpine AS frontend

COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/frontend/dist /usr/share/nginx/html
EXPOSE 80
