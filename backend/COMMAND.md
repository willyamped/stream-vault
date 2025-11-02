```
docker compose up --build
```

```
docker compose down
```

```
docker exec -it stream-vault-postgres-1 psql -U user -d streamvault
\dt
SELECT * FROM <table_name>;
\q
```

```
docker exec -it stream-vault-redis-1 sh
redis-cli
KEYS *
HGETALL <key>
exit
```

```
Perfect 🎉 — that means everything’s wired up properly now: PostgreSQL, Redis, MinIO, and your backend are all talking to each other through Docker!

Let’s summarize what your app currently does and how data flows through it:

---

## 🚀 **Stream Vault — Current Architecture & Capabilities**

### 🧩 1. **Chunked Upload Handling**

* The backend accepts **file uploads in chunks** (useful for large files or unstable connections).
* Each chunk is received via your `/upload-chunk` endpoint.
* Instead of holding everything in memory or writing immediately to permanent storage, the app **temporarily stores upload metadata and progress in Redis** — super fast and ephemeral.

✅ **Before:**

* Chunks (or upload progress) were tracked in the **H2 database**, which was local, volatile, and slow for frequent updates.

✅ **Now:**

* Upload progress (like chunk indices, statuses, and temporary metadata) is stored in **Redis**, which is in-memory and extremely fast.
* This also allows you to **scale horizontally** — multiple backend containers can share the same upload progress state.

---

### 📦 2. **MinIO Integration**

* Once all chunks are uploaded and assembled, the final file is **stored in MinIO** — your object storage (S3-compatible).
* You can manage uploaded files directly in MinIO’s dashboard (`http://localhost:9000`, login: `minioadmin / minioadmin`).
* This mirrors how modern apps like Google Drive or Dropbox store files — chunks → merged → object storage.

---

### 🧠 3. **PostgreSQL Database**

* Now that you’ve switched from H2, your persistent data (e.g. file records, users, etc.) live in a **PostgreSQL database**.
* Unlike H2, this persists even after container restarts.
* Example data stored here:

  * File metadata (filename, size, upload user, upload date, etc.)
  * Possibly deduplication hashes
  * Any user-related or access control data

---

### ⚙️ 4. **Redis**

* Redis is now your **in-memory cache and temporary state manager**.
* Common use cases in your current setup:

  * Track chunk upload progress
  * Store temporary upload session info
  * Cache lookups or rate-limit keys if implemented

---

### 🧱 5. **Dockerized Full Stack**

* Everything is now containerized:

  * `backend` (Spring Boot app)
  * `postgres` (persistent DB)
  * `redis` (fast in-memory store)
  * `minio` (file storage)

All managed by **`docker-compose`**, meaning:

```bash
docker-compose up --build
```

brings up your entire environment in one command.

---

### 💡 Summary of Data Flow

```
Client → [backend] → Redis (upload tracking)
                         ↓
                      MinIO (final file storage)
                         ↓
                     PostgreSQL (file metadata)
```

---

Would you like me to diagram this visually (a simple architecture diagram showing all these components and data flow)?
It’ll help you or others understand the project setup at a glance.

```

```
