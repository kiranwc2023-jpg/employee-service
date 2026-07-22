# Employee Service

Enterprise-grade Spring Boot microservice demonstrating a complete GitOps CI/CD pipeline.

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.3 |
| Build Tool | Maven |
| Database | H2 (dev) / PostgreSQL (prod) |
| Containerization | Docker |
| Registry | GitHub Container Registry (GHCR) |
| CI/CD | GitHub Actions |
| GitOps | Argo CD |
| Orchestration | Kubernetes |

## API Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/hello` | Health smoke test |
| GET | `/health` | Application status |
| GET | `/api/v1/employees` | List all employees |
| GET | `/api/v1/employees/{id}` | Get employee by ID |
| GET | `/api/v1/employees/department/{dept}` | Filter by department |
| GET | `/api/v1/employees/count` | Employee count |
| POST | `/api/v1/employees` | Create employee |
| PUT | `/api/v1/employees/{id}` | Update employee |
| DELETE | `/api/v1/employees/{id}` | Delete employee |
| GET | `/actuator/health` | Kubernetes probe endpoint |

## Local Development

```bash
# Build and run
mvn clean package
java -jar target/employee-service-*.jar

# Run tests only
mvn test

# Run with Maven
mvn spring-boot:run

# Access H2 console
open http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:employeedb
```

## Branch Strategy (Git Flow)

```
main        → production-ready, triggers release
develop     → integration branch, triggers staging deploy
feature/*   → individual feature work
hotfix/*    → urgent production fixes
release/*   → release preparation
```

## CI/CD Pipeline

Every push to `main` or `develop` triggers:
1. Maven build and unit tests
2. Automatic semantic version bump
3. Docker image build
4. Push to GHCR
5. GitOps repository update
6. Argo CD deploys to Kubernetes

## Docker

```bash
docker build -t employee-service:latest .
docker run -p 8080:8080 employee-service:latest
```

## Environment Variables (Production)

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | DB password (from Secret) |
| `APP_VERSION` | Application version |
