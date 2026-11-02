# syntax=docker/dockerfile:1
FROM node:24.22.1-bookworm-slim AS web
WORKDIR /workspace
RUN npm install --global pnpm@11.25.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json ./apps/web/package.json
RUN pnpm install --frozen-lockfile
COPY assets/space-catalog.json ./assets/space-catalog.json
COPY apps/web ./apps/web
RUN pnpm build

FROM eclipse-temurin:21-jdk-jammy AS server
WORKDIR /workspace/server
COPY apps/server ./
COPY assets/space-catalog.json /workspace/space-catalog.json
COPY --from=web /workspace/apps/web/dist /workspace/web-dist
RUN ./gradlew --no-daemon bootJar -PwebDist=/workspace/web-dist -PproductionImage -PspaceCatalogue=/workspace/space-catalog.json

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --gid 10001 dothome && useradd --uid 10001 --gid dothome --no-create-home --home-dir /app dothome
WORKDIR /app
COPY --from=server --chown=10001:10001 /workspace/server/build/libs/dot-home-server-0.1.0.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=google,prod \
    DOT_HOME_DEPLOYMENT=beta \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
