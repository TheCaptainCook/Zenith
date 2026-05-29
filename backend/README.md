# Zenith Backend

This is the backend service for the Zenith application, built with [NestJS](https://nestjs.com/).

## Technologies

* **Framework:** NestJS
* **Database:** PostgreSQL (via TypeORM)
* **Job Queue:** BullMQ & Redis
* **Authentication:** Passport, JWT, bcryptjs/argon2

## Prerequisites

Make sure you have the following installed:
- [Node.js](https://nodejs.org/) (version specified in your environment)
- [PostgreSQL](https://www.postgresql.org/)
- [Redis](https://redis.io/) (for BullMQ)

## Project Setup

```bash
# Install dependencies
npm install
```

## Running the Application

You can start the main API server and the background worker using the following commands:

```bash
# Development mode (API)
npm run start

# Watch mode (API)
npm run start:dev

# Watch mode (Worker)
npm run start:worker:dev

# Production mode
npm run start:prod
```

## Running Tests

```bash
# Unit tests
npm run test

# e2e tests
npm run test:e2e

# Test coverage
npm run test:cov
```

## Linting & Formatting

```bash
# Format code
npm run format

# Run linter
npm run lint
```

## License

This project is unlicensed.
