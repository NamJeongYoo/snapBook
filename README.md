# SnapBook - 대규모 선착순 티켓 예약 시스템

10,000명 이상 동시 접속을 처리할 수 있는 고성능 티켓 예약 시스템입니다.

## 기술 스택

| 항목 | 기술 |
|------|------|
| Framework | Spring Boot 4.0.6 (Java 17) |
| Database | PostgreSQL 16 |
| Cache/Queue | Redis 7 |
| Message Queue | Apache Kafka |
| Container | Docker Compose |

## 시스템 아키텍처

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Client    │────▶│  API Server │────▶│   Redis     │
│  (10,000+)  │     │ (Spring Boot)│     │ (대기열/재고)│
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────▼──────┐
                    │    Kafka    │
                    │  (예약 처리) │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ PostgreSQL  │
                    │  (영속 저장) │
                    └─────────────┘
```

### 처리 흐름

1. **대기열 진입**: Redis Sorted Set으로 순번 발급
2. **순번 확인**: 현재 순번 폴링
3. **예약 요청**: 순번 도달 시 예약 API 호출
4. **재고 감소**: Redis Lua Script로 원자적 처리
5. **비동기 저장**: Kafka → PostgreSQL 영속화

## 프로젝트 구조

```
src/main/java/com/interstellar/snapbook/
├── SnapBookApplication.java
├── config/
│   ├── RedisConfig.java              # Redis 설정
│   └── KafkaConfig.java              # Kafka 토픽 설정
├── domain/
│   ├── event/
│   │   ├── Event.java                # 이벤트 엔티티
│   │   ├── EventStatus.java          # SCHEDULED, OPEN, CLOSED, CANCELLED
│   │   ├── EventRepository.java
│   │   ├── EventService.java
│   │   ├── EventController.java
│   │   ├── EventCreateRequest.java
│   │   └── EventResponse.java
│   └── reservation/
│       ├── Reservation.java          # 예약 엔티티
│       ├── ReservationStatus.java    # PENDING, CONFIRMED, CANCELLED
│       ├── ReservationRepository.java
│       └── ReservationService.java
├── queue/
│   ├── WaitingQueueService.java      # Redis 대기열 관리
│   └── QueueController.java          # 대기열 API
├── stock/
│   └── StockService.java             # Redis Lua Script 재고 관리
├── kafka/
│   ├── ReservationMessage.java       # Kafka 메시지 DTO
│   ├── ReservationProducer.java      # 예약 이벤트 발행
│   └── ReservationConsumer.java      # 예약 이벤트 소비 및 DB 저장
├── reservation/
│   ├── ReservationFacade.java        # 예약 오케스트레이션
│   ├── ReservationController.java
│   ├── ReservationRequest.java
│   └── ReservationResponse.java
└── common/
    ├── dto/
    │   └── ApiResponse.java          # 통일된 API 응답 포맷
    └── exception/
        ├── GlobalExceptionHandler.java
        ├── SoldOutException.java
        ├── NotInQueueException.java
        └── QueueNotReadyException.java
```

## 빠른 시작

### 1. 인프라 실행

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 테스트 실행

```bash
./gradlew test
```

## API 엔드포인트

### 이벤트 관리

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/events` | 이벤트 생성 |
| POST | `/api/v1/events/{id}/open` | 이벤트 오픈 (티켓 판매 시작) |
| GET | `/api/v1/events/{id}` | 이벤트 조회 |
| GET | `/api/v1/events` | 오픈된 이벤트 목록 |
| GET | `/api/v1/events/{id}/stock` | 잔여 티켓 조회 |

### 대기열

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/events/{eventId}/queue?userId={userId}` | 대기열 진입 |
| GET | `/api/v1/events/{eventId}/queue/position?userId={userId}` | 순번 조회 |
| POST | `/api/v1/events/{eventId}/queue/allow` | 다음 배치 입장 허용 (관리자) |

### 예약

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/v1/events/{eventId}/reservations` | 예약 요청 |
| GET | `/api/v1/reservations/{id}` | 예약 조회 |
| GET | `/api/v1/users/{userId}/reservations` | 사용자 예약 목록 |

## API 사용 예시

### 1. 이벤트 생성

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "BTS 콘서트",
    "totalTickets": 10000,
    "eventDate": "2024-12-25T19:00:00"
  }'
```

### 2. 이벤트 오픈

```bash
curl -X POST http://localhost:8080/api/v1/events/1/open
```

### 3. 대기열 진입

```bash
curl -X POST "http://localhost:8080/api/v1/events/1/queue?userId=123"
```

### 4. 순번 확인

```bash
curl "http://localhost:8080/api/v1/events/1/queue/position?userId=123"
```

### 5. 예약 요청

```bash
curl -X POST http://localhost:8080/api/v1/events/1/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 123,
    "quantity": 2
  }'
```

## 핵심 구현 상세

### Redis Lua Script - 원자적 재고 감소

```lua
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1
end
local quantity = tonumber(ARGV[1])
if stock >= quantity then
    redis.call('DECRBY', KEYS[1], quantity)
    return 1
else
    return 0
end
```

### 대기열 시스템

- **Waiting Queue**: Redis Sorted Set (timestamp 기반 순서)
- **Allowed Queue**: Redis Set (입장 허용된 사용자)
- 배치 단위로 사용자 입장 허용

### 예외 처리

| 예외 | HTTP 상태 | 설명 |
|------|-----------|------|
| `SoldOutException` | 409 Conflict | 티켓 매진 |
| `NotInQueueException` | 403 Forbidden | 대기열 미등록 |
| `QueueNotReadyException` | 425 Too Early | 순번 미도달 |
| `EntityNotFoundException` | 404 Not Found | 리소스 없음 |

## 설정

### application.yml 주요 설정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/snapbook
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092

app:
  queue:
    batch-size: 100          # 배치당 입장 허용 인원
  kafka:
    topic:
      reservation: reservation-events
```

## Docker Compose 구성

| 서비스 | 이미지 | 포트 |
|--------|--------|------|
| PostgreSQL | postgres:16 | 5432 |
| Redis | redis:7-alpine | 6379 |
| Zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 |
| Kafka | confluentinc/cp-kafka:7.5.0 | 9092 |

## 테스트

### 동시성 테스트

`StockServiceTest`에서 100개의 동시 요청으로 재고 감소 테스트:
- 초과 판매 방지 검증
- 원자적 연산 검증

```bash
./gradlew test --tests StockServiceTest
```

## 라이선스

MIT License
