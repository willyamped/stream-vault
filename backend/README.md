# StreamVault Backend

StreamVault is a backend system supporting large file uploads with chunking, deduplication, event-driven processing, and distributed services. It integrates Redis for chunk tracking, PostgreSQL for metadata persistence, MinIO for file storage, and Kafka for asynchronous post-upload processing.

---

## 🚀 Features

* **Chunked file uploads** for large file handling
* **Redis** for fast upload progress state
* **Kafka** event-driven processing (file merge + dedupe)
* **MinIO** S3-compatible file storage
* **PostgreSQL** persistent metadata storage
* **File deduplication** via hashing
* **Dockerized full environment** for local development

---

## 📦 Services Overview

| Component           | Purpose                       |
| ------------------- | ----------------------------- |
| Spring Boot Backend | Handles API + upload logic    |
| PostgreSQL          | Stores file metadata          |
| Redis               | Tracks chunk upload progress  |
| MinIO               | Stores final merged files     |
| Kafka               | Signals when uploads complete |
| Docker Compose      | Runs full stack locally       |

---

## 🏗️ Architecture

```
Client → Backend
      → Redis (chunk tracking)
      → Kafka (upload complete event)
      → MinIO (final file storage)
      → PostgreSQL (file metadata)
```

---

## 📋 Prerequisites

* Docker & Docker Compose
* Java 17+

---

## ▶️ Running the Project

Start the full stack:

```bash
docker compose up --build
```

Stop everything:

```bash
docker compose down
```

---

## 🗄️ Database Access

### PostgreSQL

```bash
docker exec -it stream-vault-postgres-1 psql -U user -d streamvault
\dt
SELECT * FROM file_entity;
\q
```

### Redis

```bash
docker exec -it stream-vault-redis-1 sh
redis-cli
KEYS *
HGETALL <key>
exit
```

---

## 📁 MinIO UI

Access MinIO dashboard:

```
http://localhost:9001
```

Default credentials:

```
minioadmin / minioadmin
```

## Kibana UI

Access Kibana dashboard:

```
http://localhost:5601 > Discovery
```