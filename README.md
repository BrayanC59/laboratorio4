# Laboratorio 4: API Java con PostgreSQL en Dokploy

API de tareas con operaciones CRUD básicas, escrita en Java sin dependencias de framework. Guarda los datos en PostgreSQL, que se ejecuta en un contenedor distinto.

Diplomado CloudForge AI 5.0, Módulo 4 (Unicomfacauca).

## Contenido

| Archivo | Función |
|---|---|
| `App.java` | Servidor HTTP con el CRUD y el acceso a PostgreSQL por JDBC |
| `Dockerfile` | Construye la imagen de la API |
| `docker-compose.yml` | Servicios `db` (PostgreSQL) y `api`, volumen `pgdata` |
| `docker-compose.local.yml` | Publica el puerto 8080 solo para pruebas locales |

## Prueba local

```
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build -d
curl -s -X POST localhost:8080/tareas -d '{"titulo":"Aprender Dokploy"}'
curl -s localhost:8080/tareas
```

## Despliegue en Dokploy

1. Crear un servicio de tipo Compose con este repositorio, rama `main` y archivo `./docker-compose.yml`.
2. En Environment, definir `DB_PASSWORD` con una clave propia.
3. Desplegar y agregar un dominio al servicio `api`, con el puerto de contenedor `8080`.

## Operaciones

| Operación | Petición | Respuesta |
|---|---|---|
| Crear | `POST /tareas` con `{"titulo":"..."}` | 201 |
| Listar | `GET /tareas` | 200 |
| Leer | `GET /tareas/{id}` | 200 o 404 |
| Actualizar | `PUT /tareas/{id}` con `{"titulo":"..."}` | 200 o 404 |
| Borrar | `DELETE /tareas/{id}` | 204 o 404 |
