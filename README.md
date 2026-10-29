# SnapBook - 대규모 선착순 티켓 예약 시스템

10,000명 이상 동시 접속을 목표로 하는 선착순 티켓 예약 시스템입니다.
Redis 대기열로 트래픽을 제어하고, Redis Lua Script로 재고를 원자적으로 차감한 뒤, Kafka를 통해 예약을 비동기로 DB에 저장합니다.

## 기술 스택

| 항목 | 기술 |
|------|------|
| Framework | Spring Boot 4.0.6 (Java 17) |
| Build | Gradle |
| Database | PostgreSQL 16 (Spring Data JPA) |
| Cache/Queue | Redis 7 (Lettuce) |
| Message Queue | Apache Kafka (Confluent 7.5.0, Zookeeper 모드) |
| Validation | Jakarta Bean Validation |
| Test | JUnit 5, Testcontainers 1.20.4 |
| Container | Docker Compose |

## 시스템 아키텍처

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐
│   Client    │────▶│  API Server  │────▶│    Redis     │
│  (10,000+)  │     │ (Spring Boot)│     │ (대기열/재고) │
└─────────────┘     └──────┬───────┘     └──────────────┘
                           │ produce
                    ┌──────▼──────┐
                    │    Kafka    │
                    │ (예약 이벤트) │
                    └──────┬──────┘
                           │ consume
                    ┌──────▼──────┐
                    │ PostgreSQL  │
                    │  (영속 저장)  │
                    └─────────────┘
```

### 처리 흐름

1. **이벤트 오픈**: 이벤트 상태를 `OPEN`으로 변경하고 Redis에 재고(`stock:event:{eventId}`)를 초기화
2. **대기열 진입**: Redis Sorted Set(`queue:waiting:{eventId}`)에 진입 시각(ms)을 score로 등록하고 순번 발급
3. **입장 허용**: 관리자가 `/queue/allow`를 호출하면 대기열 앞에서 `batch-size`명을 꺼내(`ZPOPMIN`) 입장 허용 Set(`queue:allowed:{eventId}`)으로 이동
4. **순번 확인**: 클라이언트는 순번 조회 API를 폴링하며 `isAllowed`가 `true`가 될 때까지 대기
5. **예약 요청**: `ReservationFacade`가 입장 허용 여부를 검증
6. **재고 차감**: Redis Lua Script로 재고를 원자적으로 확인·차감 (초과 판매 방지)
7. **입장권 소진**: 예약에 성공한 사용자를 입장 허용 Set에서 제거 (1회 입장 = 1회 예약)
8. **비동기 저장**: Kafka로 예약 메시지를 발행하고 즉시 `202 Accepted` 응답
9. **영속화**: Consumer가 메시지를 받아 `PENDING`으로 저장한 뒤 `CONFIRMED`로 변경

## 프로젝트 구조

```
src/main/java/com/interstellar/snapbook/
├── SnapBookApplication.java
├── config/
│   ├── RedisConfig.java              # RedisTemplate / StringRedisTemplate 설정
│   └── KafkaConfig.java              # 예약 토픽 생성 (파티션 3, 복제 1)
├── domain/
│   ├── event/
│   │   ├── Event.java                # 이벤트 엔티티 (events 테이블)
│   │   ├── EventStatus.java          # SCHEDULED, OPEN, CLOSED, CANCELLED
│   │   ├── EventRepository.java
│   │   ├── EventService.java         # 생성 / 오픈(재고 초기화) / 조회
│   │   ├── EventController.java
│   │   ├── EventCreateRequest.java
│   │   └── EventResponse.java
│   └── reservation/
│       ├── Reservation.java          # 예약 엔티티 (reservations 테이블, eventId·userId 인덱스)
│       ├── ReservationStatus.java    # PENDING, CONFIRMED, CANCELLED
│       ├── ReservationRepository.java
│       └── ReservationService.java
├── queue/
│   ├── WaitingQueueService.java      # Redis 대기열 / 입장 허용 관리
│   └── QueueController.java          # 대기열 API
├── stock/
│   └── StockService.java             # Redis Lua Script 재고 관리
├── kafka/
│   ├── ReservationMessage.java       # Kafka 메시지 DTO
│   ├── ReservationProducer.java      # 예약 이벤트 발행 (key = eventId)
│   └── ReservationConsumer.java      # 예약 이벤트 소비 및 DB 저장
├── reservation/
│   ├── ReservationFacade.java        # 예약 오케스트레이션 (대기열 검증 → 재고 차감 → Kafka 발행)
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

### 사전 요구사항

- JDK 17
- Docker / Docker Compose

### 1. 인프라 실행

```bash
docker-compose up -d
```

PostgreSQL, Redis, Zookeeper, Kafka 컨테이너가 실행됩니다. 테이블은 JPA `ddl-auto: update`로 자동 생성됩니다.

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

서버는 `http://localhost:8080`에서 실행됩니다.

### 3. 테스트 실행

```bash
./gradlew test
```

> 테스트는 Testcontainers를 사용하므로 Docker가 실행 중이어야 합니다. 아래 [알려진 제약 사항](#알려진-제약-사항)도 참고하세요.

## API 엔드포인트

### 이벤트 관리

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| POST | `/api/v1/events` | 이벤트 생성 (상태: `SCHEDULED`) | 201 |
| POST | `/api/v1/events/{eventId}/open` | 이벤트 오픈 및 Redis 재고 초기화 | 200 |
| GET | `/api/v1/events/{eventId}` | 이벤트 조회 | 200 |
| GET | `/api/v1/events` | `OPEN` 상태 이벤트 목록 | 200 |
| GET | `/api/v1/events/{eventId}/stock` | 잔여 티켓 조회 (Redis 기준) | 200 |

### 대기열

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| POST | `/api/v1/events/{eventId}/queue?userId={userId}` | 대기열 진입 | 200 |
| GET | `/api/v1/events/{eventId}/queue/position?userId={userId}` | 순번 / 입장 허용 여부 조회 | 200 |
| POST | `/api/v1/events/{eventId}/queue/allow` | 다음 배치 입장 허용 (관리자) | 200 |

순번 조회 응답의 `position` 값:

| 값 | 의미 |
|----|------|
| `1` 이상 | 대기 중 (1부터 시작하는 순번) |
| `0` | 입장 허용됨 (예약 가능) |
| `-1` | 대기열에 없음 |

### 예약

| Method | Endpoint | 설명 | 응답 |
|--------|----------|------|------|
| POST | `/api/v1/events/{eventId}/reservations` | 예약 요청 (비동기 처리) | 202 |
| GET | `/api/v1/reservations/{reservationId}` | 예약 조회 | 200 |
| GET | `/api/v1/users/{userId}/reservations` | 사용자 예약 목록 | 200 |

### 요청 검증 규칙

| 요청 | 필드 | 규칙 |
|------|------|------|
| 이벤트 생성 | `name` | 필수 (공백 불가) |
| 이벤트 생성 | `totalTickets` | 양수 |
| 이벤트 생성 | `eventDate` | 미래 시각 |
| 예약 요청 | `userId` | 필수 |
| 예약 요청 | `quantity` | 필수, 1 ~ 4 |

## 응답 포맷

모든 API는 `ApiResponse`로 감싸서 응답합니다. `null` 필드는 생략됩니다.

**성공**

```json
{
  "success": true,
  "data": { "position": 1, "isAllowed": false, "totalWaiting": 42 },
  "timestamp": "2026-10-29T12:00:00"
}
```

**실패**

```json
{
  "success": false,
  "error": { "code": "SOLD_OUT", "message": "Tickets are sold out for this event." },
  "timestamp": "2026-10-29T12:00:00"
}
```

## API 사용 예시

### 1. 이벤트 생성

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "BTS 콘서트",
    "totalTickets": 10000,
    "eventDate": "2027-12-25T19:00:00"
  }'
```

> `eventDate`는 반드시 미래 시각이어야 합니다.

### 2. 이벤트 오픈

```bash
curl -X POST http://localhost:8080/api/v1/events/1/open
```

### 3. 대기열 진입

```bash
curl -X POST "http://localhost:8080/api/v1/events/1/queue?userId=123"
```

### 4. 입장 허용 (관리자)

```bash
curl -X POST http://localhost:8080/api/v1/events/1/queue/allow
```

### 5. 순번 확인

```bash
curl "http://localhost:8080/api/v1/events/1/queue/position?userId=123"
```

### 6. 예약 요청

```bash
curl -X POST http://localhost:8080/api/v1/events/1/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 123,
    "quantity": 2
  }'
```

### 7. 예약 내역 확인

```bash
curl http://localhost:8080/api/v1/users/123/reservations
```

## 핵심 구현 상세

### Redis Lua Script - 원자적 재고 감소

재고 확인과 차감을 하나의 스크립트로 실행하여 동시 요청에서도 초과 판매가 발생하지 않습니다.

```lua
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1   -- 재고 키 없음 (이벤트 미오픈)
end
local quantity = tonumber(ARGV[1])
if stock >= quantity then
    redis.call('DECRBY', KEYS[1], quantity)
    return 1    -- 성공
else
    return 0    -- 재고 부족
end
```

### Redis 키 구조

| 키 | 타입 | 용도 |
|----|------|------|
| `stock:event:{eventId}` | String | 잔여 재고 |
| `queue:waiting:{eventId}` | Sorted Set | 대기열 (member: userId, score: 진입 시각 ms) |
| `queue:allowed:{eventId}` | Set | 입장 허용된 사용자 |

### 대기열 시스템

- **Waiting Queue**: Redis Sorted Set, 진입 시각 순으로 정렬. 같은 사용자가 재진입하면 score가 갱신되어 뒤로 밀립니다.
- **Allowed Queue**: Redis Set, `ZPOPMIN`으로 꺼낸 사용자를 배치 단위(`app.queue.batch-size`)로 추가
- 입장 허용은 현재 관리자 API(`/queue/allow`) 호출로만 수행됩니다.
- 예약에 성공하면 Allowed Queue에서 제거되어 다시 예약하려면 대기열에 재진입해야 합니다.

### Kafka 메시지 처리

| 항목 | 설정 |
|------|------|
| 토픽 | `reservation-events` (파티션 3, 복제 1) |
| 메시지 키 | `eventId` (같은 이벤트의 메시지는 같은 파티션으로) |
| Producer | `acks=all`, `retries=3`, `enable.idempotence=true`, JSON 직렬화 |
| Consumer 그룹 | `snapbook-reservation-group`, `auto-offset-reset=earliest` |

메시지 포맷:

```json
{
  "eventId": 1,
  "userId": 123,
  "quantity": 2,
  "requestedAt": "2026-10-29T12:00:00"
}
```

### 예외 처리

| 예외 | HTTP 상태 | 에러 코드 | 설명 |
|------|-----------|-----------|------|
| `SoldOutException` | 409 Conflict | `SOLD_OUT` | 티켓 매진 또는 이벤트 미오픈 |
| `NotInQueueException` | 403 Forbidden | `NOT_IN_QUEUE` | 대기열 미등록 |
| `QueueNotReadyException` | 425 Too Early | `QUEUE_NOT_READY` | 순번 미도달 |
| `EntityNotFoundException` | 404 Not Found | `NOT_FOUND` | 리소스 없음 |
| `MethodArgumentNotValidException` | 400 Bad Request | `VALIDATION_ERROR` | 요청 검증 실패 |
| `IllegalArgumentException` | 400 Bad Request | `BAD_REQUEST` | 잘못된 인자 |
| `IllegalStateException` | 409 Conflict | `CONFLICT` | 잘못된 상태 |
| 기타 `Exception` | 500 Internal Server Error | `INTERNAL_ERROR` | 예상치 못한 오류 |

## 설정

### application.yml 주요 설정

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/snapbook
    username: snapbook
    password: snapbook123
    hikari:
      maximum-pool-size: 20
  jpa:
    hibernate:
      ddl-auto: update
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 20
  kafka:
    bootstrap-servers: localhost:9092

app:
  queue:
    batch-size: 100          # 배치당 입장 허용 인원
    allow-interval-ms: 1000  # 자동 입장 허용 주기 (현재 코드에서 미사용)
  kafka:
    topic:
      reservation: reservation-events
```

테스트 설정(`src/test/resources/application.yml`)은 `batch-size: 10`, 토픽 `test-reservation-events`, `ddl-auto: create-drop`을 사용합니다.

## Docker Compose 구성

| 서비스 | 컨테이너 | 이미지 | 포트 |
|--------|----------|--------|------|
| PostgreSQL | snapbook-postgres | postgres:16 | 5432 |
| Redis | snapbook-redis | redis:7-alpine (AOF 활성화) | 6379 |
| Zookeeper | snapbook-zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 |
| Kafka | snapbook-kafka | confluentinc/cp-kafka:7.5.0 | 9092 (호스트), 29092 (컨테이너 내부) |

- PostgreSQL 계정: DB `snapbook` / 사용자 `snapbook` / 비밀번호 `snapbook123`
- 모든 서비스에 healthcheck가 설정되어 있으며, PostgreSQL과 Redis 데이터는 볼륨에 유지됩니다.

## 테스트

### 동시성 테스트

`StockServiceTest`는 Testcontainers로 Redis 컨테이너를 띄워 재고 로직을 검증합니다.

| 테스트 | 검증 내용 |
|--------|-----------|
| `testInitStock` | 재고 초기화 |
| `testDecreaseStock` | 재고 차감 |
| `testDecreaseStockFail` | 재고 부족 시 차감 실패 및 재고 유지 |
| `testConcurrentDecrease` | 재고 100개에 100건 동시 요청 → 전부 성공, 재고 0 |
| `testPreventOverselling` | 재고 50개에 100건 동시 요청 → 50건 성공 / 50건 실패 (초과 판매 없음) |
| `testRestoreStock` | 재고 복원 |

```bash
./gradlew test --tests StockServiceTest
```

## 알려진 제약 사항

- **테스트 실행 환경**: 테스트용 `application.yml`에 datasource URL이 없어 `@SpringBootTest`로 전체 컨텍스트를 띄우는 테스트(`SnapBookApplicationTests`, `StockServiceTest`)는 PostgreSQL 연결 설정 없이는 컨텍스트 로딩에 실패합니다. Testcontainers PostgreSQL/Kafka 의존성은 선언되어 있지만 아직 테스트에 연결되지 않았습니다.
- **자동 입장 허용 미구현**: `app.queue.allow-interval-ms` 설정은 있지만 스케줄러가 없어 입장 허용은 `/queue/allow` 수동 호출로만 이루어집니다.
- **DB 재고 미동기화**: 재고 차감은 Redis에서만 이루어지며, `events.available_tickets` 컬럼은 예약 후에도 갱신되지 않습니다. 잔여 재고는 `/stock` API(Redis)로 확인해야 합니다.
- **보상 트랜잭션 없음**: Kafka 발행 또는 Consumer 처리가 실패해도 차감된 Redis 재고는 복원되지 않습니다. (`StockService.restoreStock`은 구현되어 있으나 호출되지 않음)
- **이벤트 상태 검증 없음**: 대기열 진입과 예약 시 이벤트의 `OPEN` 여부를 확인하지 않으며, 오픈 API를 다시 호출하면 Redis 재고가 DB의 `availableTickets` 값으로 재설정됩니다.
- **인증/인가 없음**: `userId`를 요청 파라미터로 받고, 관리자 API도 별도 권한 검사가 없습니다.

## 라이선스

MIT License
