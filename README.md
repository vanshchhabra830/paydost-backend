# PayDost - Digital Payment Backend

PayDost is a robust, digital payment backend API built with Java 21 and Spring Boot. It supports user registration, JWT-based authentication, wallet management, and secure atomic money transfers between users.

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot 3** (Web, Data JPA, Security, Validation)
- **MySQL** (Relational Database for Users, Wallets, Transactions)
- **Redis** (In-memory datastore for Rate Limiting and Idempotency Caching)
- **JJWT** (JSON Web Tokens for stateless authentication)
- **Docker & Docker Compose** (Containerized deployment)
- **Springdoc OpenAPI (Swagger)** (API Documentation)
- **JUnit 5 & Mockito** (Testing)

## 🏗️ Architecture Summary

The application follows a standard **Layered Architecture**:
1. **Controllers**: Handle HTTP requests, input validation, and HTTP responses. (e.g., `TransactionController`)
2. **Services**: Contain the core business logic, handle transactions, and orchestration. (e.g., `TransactionService`)
3. **Repositories**: Interfaces extending `JpaRepository` for database interactions. (e.g., `WalletRepository`)
4. **Models / Entities**: JPA Entities mapping to database tables. (e.g., `Transaction`)
5. **DTOs**: Data Transfer Objects used for request/response payloads to decouple internal models from external APIs.

```text
Request -> Controller (DTO Validation) -> Service (Business Logic) -> Repository (DB Access) -> Response
```

## 🛠️ Key Design Decisions

- **JWT Stateless Authentication**: Uses JSON Web Tokens for stateless, scalable auth. User identity is derived securely from the `SecurityContext` on every protected request, preventing users from accessing or modifying other users' wallets by manipulating request parameters.
- **BigDecimal for Money**: The `balance` and `amount` fields strictly use `BigDecimal` instead of `double`/`float` to ensure precision and avoid floating-point rounding errors critical in financial apps.
- **@Transactional Atomic Transfers**: Money transfers perform multiple database writes (debit sender, credit receiver, save transaction log). Using `@Transactional` ensures ACID atomicity—if the credit fails, the debit rolls back, preventing money from "vanishing".
- **Idempotency (DB + Redis)**: Transfer requests require a client-generated `referenceId` (UUID). To prevent double-processing on network retries:
  - **Redis Cache (Fast Check)**: We first check Redis (sub-millisecond) to see if the transaction was already processed.
  - **Database (Source of Truth)**: The `referenceId` has a `UNIQUE` constraint in the database.
- **Redis Rate Limiting**: Login attempts are rate-limited using Redis to prevent brute-force attacks (e.g., max 5 attempts per 60 seconds). Redis's atomic `INCR` and built-in TTL mechanisms are perfect for this use case.

## 🐳 Running via Docker Compose (Recommended)

You can spin up the entire application stack (App, MySQL, Redis) with a single command.

1. Ensure Docker and Docker Compose are installed.
2. Run from the project root:
   ```bash
   docker-compose up --build
   ```
3. The app will be available at `http://localhost:8080`. MySQL is on `3306` and Redis is on `6379`.

## 💻 Running Locally (Manual Setup)

1. Make sure you have **Java 21**, **MySQL**, and **Redis** installed and running.
2. Update the credentials in `src/main/resources/application.properties` if your local MySQL username/password differ.
3. Build the project:
   ```bash
   ./mvnw clean install
   ```
4. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## 📖 API Documentation (Swagger)

Once the application is running, you can explore and test all endpoints via the interactive Swagger UI:

👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**

## ⚡ API Endpoints Quick Reference

### Auth
- `POST /api/auth/register` - Register a new user.
- `POST /api/auth/login` - Authenticate and receive a JWT.

### Wallet
- `GET /api/wallet/balance` - View current balance (Auth required).
- `POST /api/wallet/add-money` - Add funds to wallet (Auth required).

### Transactions
- `POST /api/transactions/transfer` - Transfer money to another user (Auth required). Requires `referenceId`.
- `GET /api/transactions/history` - View paginated transaction history (Auth required).

### Example cURL Commands

**Register:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Alice","email":"alice@example.com","password":"password123"}'
```

**Login:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'
```

**Transfer (Include Auth Token from Login):**
```bash
curl -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -d '{"receiverEmail":"bob@example.com","amount":50.00,"referenceId":"123e4567-e89b-12d3-a456-426614174000"}'
```
