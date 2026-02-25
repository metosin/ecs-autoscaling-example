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
- Docker
- Redis (or run via Docker)

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

### 3. Push Docker Images

```bash
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=ap-northeast-1

aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

# Build and push web
docker build --build-arg BUILD_TARGET=uber-web -t ecs-web .
docker tag ecs-web:latest $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/autoscaling-example/web:latest
docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/autoscaling-example/web:latest

# Build and push worker
docker build --build-arg BUILD_TARGET=uber-worker -t ecs-worker .
docker tag ecs-worker:latest $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/autoscaling-example/worker:latest
docker push $AWS_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/autoscaling-example/worker:latest
```

### 4. Deploy Everything

```bash
terraform apply
```

### 5. Test

```bash
ALB_DNS=$(terraform output -raw alb_dns_name)

# Health check
curl http://$ALB_DNS/health

# Submit a job
curl -X POST http://$ALB_DNS/jobs \
  -H "Content-Type: application/json" \
  -d '{"type": "example", "payload": {"data": "hello"}}'
```

## Load Test (Trigger Autoscaling)

```bash
ALB_DNS=$(terraform output -raw alb_dns_name)

# Submit 100 jobs to trigger scale-out
for i in $(seq 1 100); do
  curl -s -X POST http://$ALB_DNS/jobs \
    -H "Content-Type: application/json" \
    -d "{\"type\": \"load-test\", \"payload\": {\"i\": $i}}" &
done
wait

# Watch queue depth
watch -n 5 "curl -s http://$ALB_DNS/queue-length"
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
