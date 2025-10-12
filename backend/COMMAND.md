```
docker run -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  -v ~/minio-data:/data \
  quay.io/minio/minio server /data --console-address ":9001"
```

```
http://localhost:8080/h2-console
```

```
docker stop $(docker ps -q)
```
