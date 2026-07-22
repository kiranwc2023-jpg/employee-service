# ═══════════════════════════════════════════════════════════════════════
# Dockerfile — Instructions to build the Docker image for employee-service
#
# WHY A DOCKERFILE?
#   Without Docker: "works on my machine" — different JDK versions, OS libraries,
#   environment configurations cause inconsistencies between dev, staging, prod.
#   With Docker: "runs identically everywhere" — the image packages the app,
#   its runtime (JVM), and all OS dependencies into a single portable artifact.
#
# BUILD LAYERS:
#   Docker builds images in LAYERS. Each instruction (FROM, COPY, RUN) creates
#   a new layer. Layers are CACHED — if a layer hasn't changed, Docker reuses
#   the cached layer instead of rebuilding it.
#
#   LAYER ORDER MATTERS:
#   Put frequently-changing layers LAST, rarely-changing layers FIRST.
#   This maximizes cache hits and speeds up builds.
# ═══════════════════════════════════════════════════════════════════════

# ───────────────────────────────────────────────────────────────────────
# STAGE 1: BUILD STAGE
# Uses a Maven + Java image to compile and package the application.
# This stage is DISCARDED in the final image — only the JAR is kept.
# This is called a MULTI-STAGE BUILD.
#
# WHY MULTI-STAGE?
#   The Maven build image is ~700MB (includes Maven, JDK, build tools).
#   We don't need Maven in production — only the JAR.
#   Multi-stage build keeps the final image small (~200MB instead of 700MB).
#   Smaller images = faster pulls = reduced attack surface = lower storage cost.
# ───────────────────────────────────────────────────────────────────────

# FROM <image>:<tag> AS <name>
#   FROM: the base image to start from. Like "inheriting" an OS + software stack.
#   eclipse-temurin:21-jdk-alpine: Official Eclipse Temurin (OpenJDK) image
#     21        = Java 21 (matches our pom.xml java.version)
#     jdk       = Java Development Kit (needed to compile; production uses jre)
#     alpine    = based on Alpine Linux (~5MB base OS; minimal attack surface)
#   AS builder: names this stage "builder" so stage 2 can reference it.
#
#   WHY maven:3.9-eclipse-temurin-21-alpine?
#   The eclipse-temurin:21-jdk-alpine image includes only the JDK — not Maven.
#   The official maven image bundles both Maven 3.9 and Java 21 on Alpine.
#   This ensures `mvn` is on PATH inside the Docker build context.
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

# WORKDIR /app
#   Sets the working directory INSIDE the container for all subsequent commands.
#   Creates /app if it doesn't exist.
#   All relative paths in COPY and RUN commands are relative to /app.
#   Without WORKDIR, files land in the root directory (/) — messy and risky.
WORKDIR /app

# COPY <source-on-host> <destination-in-container>
#   Copies files from your local machine (or CI runner) INTO the Docker image.
#
# Why copy pom.xml SEPARATELY before the source code?
#   LAYER CACHING OPTIMIZATION:
#   The Maven dependencies (downloading from Central) are the SLOWEST part of the build.
#   If we copy pom.xml first and run `mvn dependency:go-offline`, Docker caches
#   the dependency layer. On the next build, if pom.xml hasn't changed,
#   Docker reuses the cached layer — no re-downloading of dependencies.
#   Only when pom.xml changes (new dependency added) does Maven re-download.
#
#   Source code changes frequently. pom.xml changes rarely.
#   Copy the rarely-changing thing first → maximize cache hits.
COPY pom.xml .

# RUN executes a shell command INSIDE the container during image BUILD time.
# (Not at runtime — at build time. The result is baked into the layer.)
#
# mvn dependency:go-offline -q
#   dependency:go-offline = download ALL dependencies declared in pom.xml into
#                           the Maven local repository (~/.m2) INSIDE the image.
#   -q                    = quiet mode (suppress verbose download output)
#
# After this command, the Maven dependencies are CACHED in this layer.
# Subsequent builds that don't change pom.xml skip this entire step.
RUN mvn dependency:go-offline -q || true

# Now copy the full source code.
# This layer is separate from the pom.xml layer.
# Source code changes constantly — this layer is NEVER cached between builds.
# But it doesn't matter: by the time we reach this layer,
# Maven dependencies are already cached in the previous layer.
COPY src ./src

# mvn clean package -DskipTests
#   clean:       Delete /app/target — ensure no stale artifacts from previous builds.
#   package:     Run compile → test-compile → test → package → produce JAR in /app/target/
#   -DskipTests: Skip running tests during build stage.
#
#   WHY skip tests here?
#   Tests run separately in the CI pipeline BEFORE docker build.
#   Running tests again inside Docker would:
#     1. Slow down Docker builds significantly
#     2. Duplicate test execution (waste CI minutes)
#     3. Require test databases inside the Docker build context
#
#   The pipeline structure:
#     1. mvn test        → tests run on the runner (fast feedback)
#     2. docker build    → builds the artifact (skip tests, already passed)
RUN mvn clean package -DskipTests

# ───────────────────────────────────────────────────────────────────────
# STAGE 2: RUNTIME STAGE (the actual production image)
# Only the JAR from stage 1 is copied here.
# Maven, JDK tools, source code are NOT included in the final image.
# ───────────────────────────────────────────────────────────────────────

# eclipse-temurin:21-jre-alpine
#   21  = Java 21 (same version as build stage — MUST match)
#   jre = Java Runtime Environment (NOT jdk — we don't need the compiler in production)
#       JRE is ~100MB smaller than JDK
#   alpine = minimal OS
#
# JRE vs JDK in production:
#   JDK includes: java compiler (javac), debugger (jdb), profiler, documentation tools
#   JRE includes: java (JVM), standard library classes only
#   Production only needs to RUN compiled bytecode → JRE is sufficient.
#   Using JRE reduces attack surface (no compiler that could be exploited).
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Security best practice: do NOT run the application as root.
# By default, Docker containers run as root (UID 0).
# If an attacker exploits the application, they'd have root inside the container.
# Creating a dedicated user limits the blast radius.
#
# addgroup -S appgroup: create a system group named "appgroup" (-S = system/service group)
# adduser -S appuser -G appgroup: create a system user "appuser" in group "appgroup"
#   -S = system user (no password, no home directory shell)
# This is the minimal-privilege principle.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# COPY --from=builder <source-in-builder-stage> <destination-in-this-stage>
#   --from=builder: reference the "builder" stage (not the local filesystem)
#   /app/target/employee-service-*.jar: the fat JAR produced by mvn package
#   app.jar: the destination filename in the runtime image
#
# We use a WILDCARD (*.jar) because the filename includes the version:
#   employee-service-1.0.0.jar, employee-service-1.2.3.jar
# We don't want to hardcode the version in the Dockerfile — the version
# changes with every release and would require Dockerfile changes too.
COPY --from=builder /app/target/employee-service-*.jar app.jar

# Change ownership of the JAR to our non-root user.
# Without this, the file is owned by root and appuser cannot read it.
RUN chown appuser:appgroup app.jar

# Switch the active user to appuser.
# All subsequent commands (including the container's main process) run as this user.
# Without USER: container runs as root (security risk).
USER appuser

# EXPOSE 8080
#   Documents that the container listens on port 8080.
#   EXPOSE is DOCUMENTATION ONLY — it does not actually publish the port.
#   Port publishing is done at `docker run -p 8080:8080` or in Kubernetes Service YAML.
#
#   Without EXPOSE: the container still works, but no tooling knows what port to use.
#   With EXPOSE: docker inspect, docker-compose, Kubernetes can auto-detect the port.
EXPOSE 8080

# Management/actuator port (separate from API port for security)
EXPOSE 8081

# ENTRYPOINT defines the command that runs when the container starts.
# This is the container's main process (PID 1).
#
# ENTRYPOINT ["java", "-jar", "app.jar"] — EXEC FORM (preferred)
#   vs
# CMD ["java", "-jar", "app.jar"] — shell form: /bin/sh -c "java -jar app.jar"
#
# Why EXEC FORM?
#   Exec form runs java directly as PID 1 (not wrapped in /bin/sh).
#   PID 1 receives OS signals like SIGTERM (graceful shutdown).
#   If wrapped in /bin/sh: SIGTERM goes to sh, not java → graceful shutdown fails.
#   Spring Boot's graceful shutdown (server.shutdown=graceful) needs SIGTERM to reach java.
#
# JVM FLAGS explained:
#   -XX:+UseContainerSupport       : Make JVM aware it's running in a container.
#                                    Without this (Java < 10), JVM reads host RAM
#                                    and allocates 25% of host memory as heap,
#                                    causing OOMKilled in Kubernetes (heap exceeds container limit).
#                                    WITH this: JVM reads the container's cgroup limits.
#
#   -XX:MaxRAMPercentage=75.0      : Use 75% of the container's RAM as heap.
#                                    If Kubernetes limits the container to 512Mi:
#                                    JVM heap = 384Mi (75% of 512Mi).
#                                    Remaining 25% = OS overhead + JVM native memory.
#
#   -Djava.security.egd=file:/dev/./urandom  : Use /dev/urandom instead of /dev/random
#                                              for entropy. On Linux, /dev/random can
#                                              BLOCK if entropy pool is empty.
#                                              This causes slow startup in containers
#                                              (no keyboard/mouse events → low entropy).
#                                              /dev/urandom never blocks.
#
#   -Dspring.profiles.active=${SPRING_PROFILE:-default}
#                                  : Activate a Spring profile from environment variable.
#                                    Default is "default" if SPRING_PROFILE is not set.
#                                    In Kubernetes: set SPRING_PROFILE=prod via ConfigMap.
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "app.jar"]
