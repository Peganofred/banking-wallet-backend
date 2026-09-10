# Digital Wallet Backend

Spring Boot + PostgreSQL + Redis + JWT + Docker

## Tech Stack
- Java 17
- Spring Boot 3.2.5
- PostgreSQL 16 (Docker)
- Redis 7 (Docker)
- JWT (jjwt)
- Lombok
- Swagger/OpenAPI

## How to Run

### 1. Start Docker services (PostgreSQL + Redis)
```bash
docker compose up -d
```

### 2. Build & start the app
```bash
mvnw spring-boot:run
```
or
```bash
mvnw package
java -jar target/digital-wallet-1.0.0.jar
```

App runs on: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`
API docs: `http://localhost:8080/api-docs`

### 3. Stop everything
```bash
docker compose down
```

---

## API Endpoints (Phase 1)

| Method | URL                    | Auth   | Description                     |
|--------|------------------------|--------|---------------------------------|
| POST   | `/api/v1/auth/register`| No     | Create account + get JWT        |
| POST   | `/api/v1/auth/login`   | No     | Login + get JWT                 |
| GET    | `/api/v1/auth/me`      | JWT    | Current user profile            |
| POST   | `/api/v1/wallets`      | JWT    | Create wallet for current user  |
| GET    | `/api/v1/wallets/my`   | JWT    | List your wallets               |
| GET    | `/api/v1/wallets/{id}` | JWT    | Get one wallet (owner/ADMIN)    |

---

## Postman Testing Guide

### Step 1: Register a user
- **POST** `http://localhost:8080/api/v1/auth/register`
- Body (raw JSON):
```json
{
  "email": "alice@test.com",
  "password": "secret123",
  "name": "Alice"
}
```
- **Success (201):** response contains `token`. Copy it.
```
{
  "token": "eyJhbGciOi...",
  "userId": 1,
  "email": "alice@test.com",
  "name": "Alice",
  "role": "USER"
}
```

- **Duplicate email (409):**
```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "Email already registered: alice@test.com",
  "path": "/api/v1/auth/register",
  "fieldErrors": null
}
```

- **Validation error (400)**: try `email: "abc"`, `password: "1"` -> shows all field errors.

### Step 2: Login
- **POST** `http://localhost:8080/api/v1/auth/login`
- Body:
```json
{
  "email": "alice@test.com",
  "password": "secret123"
}
```
- **Success (200):** new token.
- **Wrong password (401):** `Invalid email or password`
- **Missing email (400):** validation errors

### Step 3: Use the token
Every protected call needs header:
```
Authorization: Bearer <your_token>
```
(In Postman, also set up a variable `{{token}}` via Tests tab:
`pm.environment.set("token", pm.response.json().token);`)

### Step 4: /auth/me
- **GET** `http://localhost:8080/api/v1/auth/me` with token -> your profile
- Without token -> **401** `Authentication required - provide a valid JWT token`

### Step 5: Create wallet
- **POST** `http://localhost:8080/api/v1/wallets` with token
- Optional body: `{ "initialBalance": 500 }`
- Create wallet for Alice and Bob. Alice's wallet = id 1, Bob's = id 2.

### Step 6: List / Get wallets
- **GET** `http://localhost:8080/api/v1/wallets/my` -> your wallet(s)
- **GET** `http://localhost:8080/api/v1/wallets/1` -> wallet by id
  - Alice can see id 1.
  - Bob trying id 1 -> **403** `You are not allowed to access this wallet`
  - any id 999 -> **404** `Wallet not found with id: 999`

---

## Phase 2 - Deposit / Withdraw + Idempotency

### New endpoints

| Method | URL                        | Auth | Idempotency-Key Header | Description      |
|--------|----------------------------|------|------------------------|------------------|
| POST   | `/api/v1/wallets/{id}/deposit`  | JWT  | **required**           | Add money        |
| POST   | `/api/v1/wallets/{id}/withdraw` | JWT  | **required**           | Take out money   |
| POST   | `/api/v1/transfers`              | JWT  | **required**           | Send to another user |

Body:
```json
{
  "amount": 250,
  "description": "pay rent"
}
```

### Idempotency (why it matters)

Imagine the client sends a deposit but the connection drops mid-way.
The client retries. Without protection the money would be added TWICE.

Solution -> each deposit/withdraw/transfer REQUIRES an `Idempotency-Key` header.
Re-sending the same key+wallet returns the SAME result instead of executing again.

Two layers of defence:
1. **Redis** (`IdempotencyService`): atomic `SET key NX EX` lock - fast duplicate detector.
2. **Database**: unique constraint on `(wallet_id, idempotency_key)` - final authority
   even if Redis misses.

### Concurrency (atomic updates)

Banking-standard approach: **single-statement atomic SQL updates** instead of read-then-write:

- **Debit** (withdraw / transfer sender):
  ```sql
  UPDATE wallets SET balance = balance - :amount
  WHERE id = :id AND balance >= :amount
  ```
  The `balance >= :amount` guard makes the check + deduction atomic in one
  statement — no read window for a concurrent writer to interfere.

- **Credit** (deposit / transfer receiver):
  ```sql
  UPDATE wallets SET balance = balance + :amount WHERE id = :id
  ```

- **Transfer** (two wallets in one tx): debit + credit + 2 audit rows in a
  single `@Transactional`. Deadlock-free ordering: the LOWER wallet id is
  always touched first, so concurrent transfers (A→B and B→A) never lock
  rows in opposite order.

Redis idempotency (`IdempotencyService`) + DB unique constraint on
`(wallet_id, idempotency_key)` are retained as duplicate guards.

### Phase 2 Postman tests

1. **Deposit** (Alice, wallet 1):
   - POST `/api/v1/wallets/1/deposit`
   - Headers: `Authorization: Bearer <alice>` + `Idempotency-Key: dep-001`
   - Body: `{"amount": 1000, "description": "initial load"}`
   - Expected 200: `{"id":1,"type":"DEPOSIT","status":"SUCCESS","amount":1000,"balanceAfter":1000.00}`

2. **Same key again** (idempotency):
   - Send the EXACT same request again (same key `dep-001`).
   - Expected: SAME transaction id returned, `balanceAfter` still 1000.
   - Check wallet via GET `/wallets/1` -> balance still 1000 (NOT 2000).

3. **Withdraw**:
   - POST `/api/v1/wallets/1/withdraw`, key `wd-001`, body `{"amount":250,"description":"pay rent"}`
   - Expected 200: `balanceAfter: 750.00`

4. **Insufficient balance**:
   - POST withdraw with `{"amount": 5000}`, new key `wd-big`
   - Expected **400**: `"Insufficient balance: have 750.00, need 5000"`

5. **Missing Idempotency-Key**:
   - POST deposit WITHOUT the header
   - Expected **400**: `"Missing or malformed request: Required request header 'Idempotency-Key'..."`

6. **Negative amount**:
   - POST deposit with `{"amount": -50}`
   - Expected **400** + fieldErrors `"Amount must be greater than zero"`

7. **Ownership on write**:
   - Bob's token, POST deposit on `/wallets/1` (Alice's wallet)
   - Expected **403**: `"You are not allowed to access this wallet"`

8. **Concurrency (fun test)** - send 5 deposits of 200 in parallel:
   - Postman: open 5 tabs, same deposit URL, DIFFERENT keys (`par-1`..`par-5`), same wallet.
   - Fire all quickly (or use Postman Runner / a script).
   - Expected: all SUCCESS, final balance = start + 1000 exactly.
   - No lost updates because of the row lock.

---

## Global Exception Handling (how it works)

Every error response has the SAME shape:
```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "human readable message",
  "path": "/api/v1/...",
  "fieldErrors": null
}
```

Custom exceptions -> HTTP status mapping:

| Exception                    | HTTP          | When                                       |
|------------------------------|---------------|---------------------------------------------|
| ResourceNotFoundException    | 404 Not Found | wallet/user not found                       |
| InvalidRequestException      | 400 Bad Req   | bad amount, negative balance, etc.          |
| DuplicateResourceException   | 409 Conflict  | duplicate email / already has wallet        |
| InsufficientBalanceException | 400 Bad Req   | (Phase 2/3) not enough balance              |
| AccessDeniedException        | 403 Forbidden | (our check) not wallet owner                |
| BadCredentialsException      | 401 Unauthorized | wrong login credentials                 |
| Missing/invalid JWT          | 401 Unauthorized | no/bad token (custom entry point)       |
| Spring AccessDenied          | 403 Forbidden | security-level permission                   |
| MethodArgumentNotValid       | 400 Bad Req   | @Valid failures (fieldErrors populated)     |
| Any other Exception          | 500           | unexpected (no internals leaked)            |

---

## Phase 3 - Money Transfer (User-to-User)

### New endpoint

| Method | URL                    | Auth | Idempotency-Key Header | Description                     |
|--------|------------------------|------|------------------------|---------------------------------|
| POST   | `/api/v1/transfers`    | JWT  | **required**           | Transfer money to another user  |

Body:
```json
{
  "toWalletId": 2,
  "amount": 100,
  "description": "pay dinner"
}
```

Response (200):
```json
{
  "id": 5,
  "walletId": 1,
  "type": "TRANSFER_OUT",
  "status": "SUCCESS",
  "amount": 100.00,
  "balanceAfter": 900.00,
  "idempotencyKey": "tx-001",
  "description": "pay dinner",
  "createdAt": "2026-09-10T12:30:00.123"
}
```

Two transaction rows are created atomically (TRANSFER_OUT + TRANSFER_IN)
in a single `@Transactional` — money is always conserved.

### Validations

| Scenario                    | HTTP  | Message                                      |
|-----------------------------|-------|----------------------------------------------|
| Insufficient balance        | 400   | `Insufficient balance: have X, need Y`       |
| Transfer to self            | 400   | `Cannot transfer to the same wallet`         |
| Recipient wallet not found  | 404   | `Wallet not found with id: N`               |
| Negative amount             | 400   | `Amount must be greater than zero`           |
| Missing Idempotency-Key     | 400   | `Required request header 'Idempotency-Key'`  |
| Same key replay             | 200   | Returns original transaction, no balance change |

### How transfers stay race-free

1. **Atomic conditional UPDATE** — debit uses:
   ```sql
   UPDATE wallets SET balance = balance - :amount
   WHERE id = :id AND balance >= :amount
   ```
   Check + deduction happen in one SQL statement — no read window for
   a concurrent writer to interfere.

2. **Deadlock-free ordering** — the lower wallet ID is always touched
   first, so concurrent A→B and B→A transfers always lock rows in the
   same ascending order. No deadlock possible.

3. **Idempotency** — Redis + DB unique constraint `(wallet_id, idempotency_key)`
   prevent double-execution on retries.

### Phase 3 Postman tests

1. **Basic transfer** (Alice w1=1000 → Bob w2=0):
   - POST `/api/v1/transfers` (Alice's token, key `tx-001`)
   - Body: `{"toWalletId": 2, "amount": 250, "description": "lunch"}`
   - Expected 200: `balanceAfter: 750.00`, type `TRANSFER_OUT`
   - GET `/wallets/2` (Bob) -> balance 250.00

2. **Idempotency replay**:
   - Re-send same request (same key `tx-001`)
   - Expected: SAME transaction returned, balances unchanged

3. **Insufficient balance**:
   - Transfer `{"amount": 5000}` (w1 only has 750)
   - Expected **400**: `"Insufficient balance: have 750.00, need 5000"`

4. **Self-transfer**:
   - Alice sends to her own wallet 1
   - Expected **400**: `"Cannot transfer to the same wallet"`

5. **Wrong owner**:
   - Bob's token, send from wallet 1 (Alice's)
   - Expected **403**

6. **Concurrent bidirectional** (advanced):
   - Deposit 1000 to both wallets
   - Fire 5 transfers A→B and 5 transfers B→A simultaneously
   - All should return 200
   - Balances should still sum to original total (conservation check)

All handled centrally in:
- `exception/GlobalExceptionHandler.java` (@RestControllerAdvice)
- `security/RestAuthErrorHandlers.java` (401/403 for security filters)

---

## Phase 1 Structure

```
src/main/java/com/wallet/
├── DigitalWalletApplication.java
├── config/          SecurityConfig, JwtProperties, AppProperties
├── exception/       ApiError, ApiException, GlobalExceptionHandler, custom exceptions
├── security/        JwtService, JwtAuthFilter, CustomUserDetailsService, RestAuthErrorHandlers
├── user/            User, Role, AuthDtos, UserDto, AuthService, AuthController, UserRepository
├── wallet/          Wallet, WalletDtos, WalletService, WalletController, WalletRepository
├── transaction/     Transaction, TransactionType, TransactionRepository
└── transfer/        TransferRequest, TransferService, TransferController
```