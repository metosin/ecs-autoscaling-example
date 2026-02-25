# ECS Fargate Autoscaling Example

Demonstrates ECS Fargate autoscaling based on Redis queue depth. Two Clojure services (web + worker) communicate via Redis. Workers autoscale from 1-20 based on queue length.

## Architecture

```
Internet → ALB → [Web Service (Fargate)] → Redis (ElastiCache) → [Worker Service (Fargate, 1-20)]
                                                ↑                        |
                                                └── results ─────────────┘

Worker publishes RedisQueueLength → CloudWatch → Target Tracking Autoscaling Policy
```

**Autoscaling formula**: `BacklogPerWorker = RedisQueueLength / RunningTaskCount`, target = 5 items/worker.

## Local Development

### Prerequisites

- Java 21+
- [Clojure CLI](https://clojure.org/guides/install_clojure)
- [Babashka](https://github.com/babashka/babashka#installation)
- Docker
- Redis (or run via Docker)

All tools except Docker and Redis can be installed with [mise](https://mise.jdx.dev/getting-started.html) — see `mise.toml` in the repo root.

### Start Redis

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

### Run Web Server

```bash
clj -M:web
# Listening on http://localhost:8080
```

### Run Worker

```bash
clj -M:worker
```

### Test

```bash
# Submit a job
curl -X POST http://localhost:8080/jobs \
  -H "Content-Type: application/json" \
  -d '{"type": "example", "payload": {"data": "hello"}}'

# Check result (use job-id from response)
curl http://localhost:8080/jobs/<job-id>

# Check queue depth
curl http://localhost:8080/queue-length

# Health check
curl http://localhost:8080/health
```

## Docker Build

```bash
# Build web image
docker build --build-arg BUILD_TARGET=uber-web -t ecs-web .

# Build worker image
docker build --build-arg BUILD_TARGET=uber-worker -t ecs-worker .

# Run against local Redis
docker run -d --name redis -p 6379:6379 redis:7
docker run --rm -p 8080:8080 -e REDIS_HOST=host.docker.internal ecs-web
docker run --rm -e REDIS_HOST=host.docker.internal ecs-worker
```

## Deploy to AWS

### 1. Initialize Terraform

```bash
cd terraform
terraform init
```

### 2. Create Infrastructure (ECR first)

```bash
terraform apply -target=aws_ecr_repository.web -target=aws_ecr_repository.worker
```

### 3. Build and Push Docker Images

Images are tagged with the current git tag and built for both x86 and ARM architectures (Fargate is configured to use Graviton/ARM64).

```bash
bb build
```

### 4. Deploy Everything

```bash
bb deploy
```

### 5. Test

```bash
# Submit a job (defaults to type "example")
bb add-job

# Submit with custom type and payload
bb add-job load-test '{"data": "hello"}'

# Check queue depth
bb queue-length
```

## Load Test (Trigger Autoscaling)

```bash
# Submit 100 jobs to trigger scale-out
for i in $(seq 1 100); do
  bb add-job load-test "{\"i\": $i}" &
done
wait

# Watch queue depth
watch -n 5 bb queue-length
```

Tail logs:

```bash
bb logs-web
bb logs-worker
```

Monitor in the AWS Console:
- **ECS** → Cluster → worker service → Tasks tab (watch task count increase)
- **CloudWatch** → Metrics → ECSAutoscaling → RedisQueueLength
- **CloudWatch** → Metrics → ECS/ContainerInsights → RunningTaskCount

## Cleanup

```bash
cd terraform
terraform destroy
```

## Project Structure

```
├── bb.edn              # Babashka tasks (build, deploy, logs, etc.)
├── deps.edn            # Clojure dependencies
├── build.clj           # Uberjar build targets
├── Dockerfile          # Multi-stage build (web/worker)
├── src/app/
│   ├── config.clj      # Environment-based configuration
│   ├── redis.clj       # Carmine connection pool
│   ├── queue.clj       # Redis queue operations
│   ├── web.clj         # Ring HTTP server
│   └── worker.clj      # Job processor + CloudWatch metric publisher
└── terraform/
    ├── main.tf          # Provider config
    ├── variables.tf     # Configurable values
    ├── vpc.tf           # VPC, subnets, NAT
    ├── security_groups.tf
    ├── ecr.tf           # Container registries
    ├── iam.tf           # Task roles
    ├── elasticache.tf   # Redis
    ├── cloudwatch.tf    # Log groups
    ├── alb.tf           # Load balancer
    ├── ecs.tf           # Cluster, task defs, services
    ├── autoscaling.tf   # Target tracking policy
    └── outputs.tf
```
