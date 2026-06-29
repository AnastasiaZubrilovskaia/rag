# RAG-сервис (Retrieval-Augmented Generation)

RAG-сервис для загрузки документов, их индексации и генерации ответов с использованием локальной большой языковой модели (LLM).

После загрузки документ разбивается на чанки, для каждого чанка создаются эмбеддинги с помощью модели **nomic-embed-text**, которые сохраняются в **Qdrant**. При поступлении пользовательского запроса выполняется семантический поиск наиболее релевантных фрагментов, после чего найденный контекст передается языковой модели **qwen2.5:7b**, запущенной в **Ollama**, для генерации ответа.

---



## Возможности

- Загрузка документов (`.txt`, `.md`, `.pdf`, `.docx`)
- Разбиение документов на чанки с настраиваемым размером и перекрытием
- Генерация эмбеддингов через Ollama (`nomic-embed-text`)
- Кэширование эмбеддингов для повторного использования
- Хранение документов и эмбеддингов в Qdrant
- Семантический поиск релевантных фрагментов
- Генерация ответов на основе найденного контекста (RAG)
- Потоковая передача ответов (stream=true, SSE)
- Асинхронная обработка документов (CompletableFuture)
- Веб-поиск как fallback при недостатке контекста (SearXNG)
- Удаление документов из Qdrant
- OpenAI-совместимый REST API
- Swagger/OpenAPI документация
- Docker Compose для быстрого запуска

---

## Технологии

| Технология | Версия |
|------------|---------|
| Java | 21 |
| Spring Boot | 3.4.10 |
| Spring AI | 1.0.3 |
| Qdrant Java Client | 1.12.0 |
| Springdoc OpenAPI | 2.8.9 |
| Apache PDFBox | 3.0.3 |
| Apache POI | 5.3.0 |
| Ollama | latest |
| Qdrant | latest |
| SearXNG | latest |
| Open WebUI | latest |
| Docker / Docker Compose | latest |

---

## Требования

- Docker
- Docker Compose
- Java 21+ (для локального запуска)
- Maven 3.9+

---

# Установка и запуск

## 1. Клонирование репозитория

```bash
git clone <repository-url>
cd rag
```

---

## 2. Запуск сервисов

```bash
docker compose up --build
```

После запуска будут доступны:

| Сервис | URL |
|---------|-----|
| RAG API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Open WebUI | http://localhost:3000 |
| Ollama | http://localhost:11434 |
| Qdrant Dashboard | http://localhost:6333/dashboard |
| SearXNG| http://localhost:8888 |

---

## 3. Загрузка моделей Ollama

Если модели отсутствуют:

```bash
docker exec -it ollama ollama pull qwen2.5:7b
docker exec -it ollama ollama pull nomic-embed-text
```

Проверить установленные модели:

```bash
docker exec -it ollama ollama list
```

---

# API

## Получение списка моделей

```
GET /v1/models
```

### Ответ

```json
{
  "object": "list",
  "data": [
    {
      "id": "qwen2.5:7b",
      "object": "model",
      "owned_by": "local"
    }
  ]
}
```

---

## Загрузка документа

```
POST /api/documents/upload
```

Тип запроса:

```
multipart/form-data
```

Параметры:

| Поле | Тип |
|------|-----|
| file | MultipartFile |

Поддерживаемые форматы:

- `.txt`
- `.md`
- `.pdf`
- `.docx`

### Ответ

```json
{
  "id": "30015ccb-e059-49e4-9914-ad13fec84c5c",
  "fileName": "document.txt",
  "status": "COMPLETED",
  "chunkCount": 5
}
```

---

## Получение списка документов

```
GET /api/documents
```

### Ответ

```json
[
  {
    "id": "db4dc09e-1f98-4e37-9b6d-9748ecbdffd5",
    "fileName": "txt1.txt",
    "status": "COMPLETED",
    "chunkCount": 1
  },
  {
    "id": "30015ccb-e059-49e4-9914-ad13fec84c5c",
    "fileName": "document.txt",
    "status": "COMPLETED",
    "chunkCount": 5
  }
]
```

---

## Удаление документа

```
DELETE /api/documents/{id}
```

---

## Генерация ответа

```
POST /v1/chat/completions
```

### Пример запроса (обычный режим)

```json
{
  "model": "qwen2.5:7b",
  "messages": [
    {
      "role": "user",
      "content": "Что такое Spring Boot?"
    }
  ],
  "stream": false,
  "temperature": 0.1,
  "maxTokens": 512
}
```

### Пример ответа (обычный режим)

```json
{
  "id": "chatcmpl-3dd9a93c-5429-4a01-bb06-cb3e3f07c7d3",
  "object": "chat.completion",
  "created": 1782652546,
  "model": "qwen2.5:7b",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Spring Boot — это Java-фреймворк, который автоматически конфигурирует приложение. Источник: txt1.txt"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 0,
    "completion_tokens": 0,
    "total_tokens": 0
  }
}
```
### Пример запроса (потоковый режим)

```json
{
  "model": "qwen2.5:7b",
  "messages": [
    {
      "role": "user",
      "content": "Что такое Spring Boot?"
    }
  ],
  "stream": true,
  "temperature": 0.1,
  "maxTokens": 512
}
```
Ответ (потоковый режим): Server-Sent Events (SSE)

---
# Веб-поиск (Fallback)

Если в загруженных документах недостаточно релевантной информации, сервис автоматически выполняет веб-поиск через SearXNG.

Условия активации:  
- Найдено меньше min-documents релевантных чанков   
- Веб-поиск включен в настройках

Результаты поиска добавляются в контекст, и LLM генерирует ответ на основе объединенной информации.  

```
rag:
  web-search:
    enabled: true
    min-documents: 1          
    results-limit: 3          
```

# Тестирование

## Через Swagger

```
http://localhost:8080/swagger-ui.html
```

---

## Через curl

### Загрузка документа

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@document.txt"
```

### Получение списка документов

```bash
curl http://localhost:8080/api/documents
```

### Запрос к LLM

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
-H "Content-Type: application/json" \
-d '{
  "model":"qwen2.5:7b",
  "messages":[
    {
      "role":"user",
      "content":"Что такое Spring Boot?"
    }
  ],
  "stream":false
}'
```

### Потоковый запрос: к LLM

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
-H "Content-Type: application/json" \
-d '{
  "model":"qwen2.5:7b",
  "messages":[
    {
      "role":"user",
      "content":"Что такое Spring Boot?"
    }
  ],
  "stream":true
}'
```

---

# Структура проекта

```text
src
├── main
│   ├── java
│   │   └── ru/neoflex/rag
│   │       ├── config
│   │       ├── controller
│   │       ├── model
│   │       ├── parser
│   │       ├── properties
│   │       ├── repository
│   │       └── service
│   └── resources
│       └── application.yaml
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

# Архитектура

```
Документ
    │
    ▼
Парсер (.txt/.md/.pdf/.docx)
    │
    ▼
Асинхронная обработка (CompletableFuture)
    │
    ▼
Chunking
    │
    ▼
Проверка кэша эмбеддингов
    │
    ├─── Если есть в кэше → берем из кэша
    │
    └─── Если нет → Ollama Embedding (nomic-embed-text) → сохраняем в кэш
    │
    ▼
Qdrant

───────────────

Запрос пользователя
    │
    ▼
Similarity Search в Qdrant
    │
    ▼
Достаточно документов?
    │
    ├─── Да → Формирование промпта с контекстом
    │
    └─── Нет → Веб-поиск (SearXNG) → добавляем в контекст
    │
    ▼
Ollama Chat (qwen2.5:7b)
    │
    ├─── stream=false → JSON
    │
    └─── stream=true → SSE (Server-Sent Events)
    │
    ▼
Ответ пользователю
```