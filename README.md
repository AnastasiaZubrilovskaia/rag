# RAG-сервис (Retrieval-Augmented Generation)

RAG-сервис для загрузки документов, их индексации и генерации ответов с использованием гибридного подхода: эмбеддинги локально, генерация через облачную LLM.

Эмбеддинги для документов создаются с помощью модели **nomic-embed-text**, запущенной локально в Ollama, и сохраняются в **Qdrant**. При поступлении пользовательского запроса сервис извлекает последний вопрос, учитывает историю диалога, выполняет поиск релевантных фрагментов, применяет защитные и качественные фильтры, а затем передает контекст облачной LLM **glm-4.7:cloud**.

---



## Возможности

- Загрузка документов в форматах `.txt`, `.md`, `.pdf`, `.docx`, `.html`, `.htm`
- Асинхронная обработка документов (`CompletableFuture`)
- Нормализация текста перед индексацией
- Разбиение на чанки с учётом границ слов и настраиваемым overlap
- Генерация эмбеддингов через Ollama (`nomic-embed-text`)
- Внутренний кэш эмбеддингов с ограничением размера и очисткой при удалении документа
- Хранение чанков и метаданных в Qdrant
- Семантический поиск релевантных фрагментов
- Поддержка диалога: история сообщений передается в промпт, поиск выполняется по последнему вопросу
- Классификация запросов: `CHAT`-реплики могут обходить RAG-поиск
- Защита от prompt injection из контекста документов
- Exact-term guard для технических токенов (`/path`, `*.md`, `CONSTANT_CASE`, `CamelCase`)
- Двухуровневый фильтр качества источников (`strong` / `borderline`)
- Веб-поиск через SearXNG как fallback при недостатке контекста
- Переключение стиля ответа через суффикс имени модели: `-simple`, `-eli5`
- Потоковая передача ответа через `/v1/chat/completions` при `stream=true` (SSE)
- OpenAI-совместимый REST API
- Диагностический endpoint `/rag/debug`
- `request_id`, список источников и тайминги в ответе
- Таймауты, retry, circuit breaker, time limiter и bulkhead через Resilience4j
- Обработка квоты Ollama (`HTTP 429`) с извлечением `Retry-After`
- Swagger / OpenAPI документация
- Docker Compose для быстрого запуска
- Unit-тесты сервисного слоя

---

## Что было доработано

Ниже перечислены ключевые доработки, внесенные после технической проверки:

1. **Чанкинг текста**
   - текст нормализуется перед разбиением;
   - чанки не обрезаются посреди слова;
   - сохраняется перекрытие между соседними фрагментами.

2. **Кэш эмбеддингов**
   - кэш ограничен по размеру;
   - при удалении документа связанные записи удаляются из кэша.

3. **Защита от инъекций через документы**
   - контекст передается в отдельной секции как данные;
   - системный промпт явно запрещает следовать инструкциям из контекста.

4. **Поддержка диалога**
   - в промпт передается история сообщений;
   - поиск строится по последнему вопросу пользователя.

5. **Поддержка HTML**
   - добавлен парсер `.html` / `.htm`;
   - используется `Jsoup` + `Boilerpipe` для извлечения основного текста страницы.

6. **Консистентное удаление**
   - документ сначала удаляется из Qdrant;
   - только после успешного удаления очищается внутреннее состояние сервиса и кэш.

7. **Отказоустойчивость**
   - настроены таймауты на обращения к Qdrant, Ollama и SearXNG;
   - добавлены retry / circuit breaker / time limiter / bulkhead.

8. **Условный запуск RAG**
   - перед поиском выполняется классификация запроса;
   - короткие chat-запросы могут не проходить через RAG-конвейер.

9. **Стили ответа**
   - поддерживаются режимы `EXPERT`, `SIMPLE`, `ELI5`;
   - стиль выбирается по суффиксу имени модели.

10. **Обработка `HTTP 429`**
    - перехватываются ошибки квоты Ollama;
    - извлекается `Retry-After` из заголовка или тела ответа.

11. **Прозрачность ответа**
    - в ответ добавлены `request_id`, `sources`, `timings`;
    - при отсутствии контекста LLM не вызывается, возвращается честный ответ без генерации.

12. **Дополнительные защитные фильтры**
    - реализован exact-term guard;
    - добавлен диагностический endpoint `/rag/debug` для анализа пайплайна.

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
| Jsoup | 1.18.3 |
| Boilerpipe | 1.1.0 |
| Resilience4j | 2.2.0 |
| Ollama (локальный) | latest |
| Ollama Cloud | — |
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
- Локально установленный Ollama (для эмбеддингов)
- API-ключ для Ollama Cloud (для генерации)
---

# Установка и запуск

## 1. Клонирование репозитория

```bash
git clone <repository-url>
cd rag
```

---

## 2. Настройка переменных окружения

Создайте файл .env в корне проекта:

```
OLLAMA_API_KEY=ваш_ключ
```

---

## 3. Запуск сервисов

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
| Qdrant Dashboard | http://localhost:6333/dashboard |
| SearXNG | http://localhost:8888 |

---

## 4.  Запуск локального Ollama для эмбеддингов

```
ollama serve
```

Проверить, что эмбеддинг-модель загружена:

```bash
ollama pull nomic-embed-text
ollama list
```

---

## 5. Базовые настройки

Основные параметры в `application.yaml`:

```yaml
rag:
  chunk-size: 1000
  chunk-overlap: 200
  search:
    top-k: 5
    similarity-threshold: 0.7
  web-search:
    enabled: false
    min-documents: 1
    results-limit: 3
  filter:
    strong-threshold: 0.75
    borderline-threshold: 0.6
    min-context-sources: 2
    exact-term-guard-enabled: true
```

Таймауты внешних вызовов:

```yaml
spring:
  ai:
    ollama:
      chat:
        timeout: 30s

qdrant:
  timeout: 5s

searxng:
  timeout: 10s
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
      "id": "nomic-embed-text:latest",
      "object": "model",
      "owned_by": "local"
    },
    {
      "id": "qwen2.5:7b",
      "object": "model",
      "owned_by": "local"
    },
    {
      "id": "deepseek-v4-pro",
      "object": "model",
      "owned_by": "cloud"
    },
    {
      "id": "minimax-m2.1",
      "object": "model",
      "owned_by": "cloud"
    },
    {
      "id": "minimax-m3",
      "object": "model",
      "owned_by": "cloud"
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
- `.html`
- `.htm`

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

Если удалить документ из Qdrant не удалось, запрос завершится ошибкой, а локальное состояние сервиса не будет очищено.

---

## Генерация ответа

```
POST /v1/chat/completions
```

### Особенности

- в поиск уходит **последнее сообщение пользователя**;
- история диалога добавляется в промпт;
- стиль ответа выбирается по имени модели:
  - `glm-4.7:cloud` -> экспертный режим;
  - `glm-4.7:cloud-simple` -> простой стиль;
  - `glm-4.7:cloud-eli5` -> объяснение "как для ребёнка".

### Пример запроса

```json
{
  "model": "glm-4.7:cloud",
  "messages": [
    {"role": "user", "content": "Что такое @SpringBootApplication?"}
  ],
  "stream": false
}
```

### Пример ответа (обычный режим)

```json
{
  "id": "chatcmpl-e9b8d85c",
  "object": "chat.completion",
  "created": 1783803820,
  "model": "glm-4.7:cloud",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "@SpringBootApplication — это аннотация для автоконфигурации Spring Boot.\nИсточник: test.txt"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 0,
    "completion_tokens": 0,
    "total_tokens": 0
  },
  "request_id": "req-270e4a4b",
  "sources": [
    {
      "documentId": "e59e5d5e-cf54-4e58-8da1-274ae9440fe3",
      "fileName": "test.txt",
      "position": 0,
      "score": 1,
      "text": "@SpringBootApplication — это аннотация для автоконфигурации Spring Boot."
    },
    {
      "documentId": "459749a0-9de0-40c0-99a2-823c4cda89bc",
      "fileName": "txt1.txt",
      "position": 0,
      "score": 0,
      "text": "Spring Boot — это Java-фреймворк. Spring Boot автоматически конфигурирует приложение. Bean создается контейнером Spring."
    }
  ],
  "timings": {
    "retrieval_ms": 7041,
    "prompt_ms": 2,
    "generation_ms": 2494,
    "total_ms": 9541
  }
}
```
## Генерация ответа (потоковый режим)

```
POST /v1/chat/completions
```

### Пример запроса (потоковый режим)

```json
{
  "model": "glm-4.7:cloud",
  "messages": [
    {"role": "user", "content": "Что такое @SpringBootApplication?"}
  ],
  "stream": true
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

- Unit-тесты на сервисный слой.
- Общее покрытие: ~57%.

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
  "model":"glm-4.7:cloud",
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
  "model":"glm-4.7:cloud",
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
│   │       ├── filter
│   │       ├── interceptor
│   │       ├── model
│   │       ├── parser
│   │       ├── properties
│   │       ├── repository
│   │       ├── service
│   │       └── util
│   └── resources
│       └── application.yaml
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

# Архитектура

```
Документ
    │
    ▼
Парсер (.txt / .md / .pdf / .docx / .html)
    │
    ▼
Нормализация текста
    │
    ▼
Chunking с учетом границ слов и overlap
    │
    ▼
Индексация в Qdrant

────────────────────────────────────────

История сообщений + последний вопрос
    │
    ▼
Классификация запроса (CHAT / SEARCH)
    │
    ├── CHAT ──► обычный ответ без retrieval
    │
    └── SEARCH
           │
           ▼
           Similarity Search в Qdrant
           │
           ▼
           Exact-term guard
           │
           ▼
           Двухуровневый фильтр качества источников
           │
           ├── Нет допустимого контекста ──► no_context, LLM не вызывается
           │
           └── Контекст допустим
                  │
                  ├── при необходимости ► Web Search (SearXNG)
                  │
                  ▼
                  Формирование защищенного промпта
                  │
                  ▼
                  Ollama Cloud (glm-4.7:cloud / -simple / -eli5)
                  │
                  ├── stream=false ► /v1/chat/completions ► JSON + request_id + sources + timings
                  └── stream=true  ► /v1/chat/completions ► SSE
```
