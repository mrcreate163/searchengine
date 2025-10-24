# Динамическое управление сайтами / Dynamic Site Management

Этот документ описывает, как использовать новые API endpoints для динамического добавления и удаления сайтов для индексации без необходимости изменения файла конфигурации application.yaml.

This document describes how to use the new API endpoints for dynamically adding and removing sites for indexing without needing to modify the application.yaml configuration file.

## Добавление сайта / Adding a Site

### Endpoint
```
POST /api/site
Content-Type: application/json
```

### Пример запроса / Request Example
```bash
curl -X POST http://localhost:8080/api/site \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "name": "Example Website"
  }'
```

### Успешный ответ / Success Response
```json
{
  "result": true
}
```

### Ответ с ошибкой / Error Response
```json
{
  "result": false,
  "error": "Сайт с таким URL уже существует в конфигурации"
}
```

### Возможные ошибки / Possible Errors
- `URL не может быть пустым` - URL is empty
- `Название сайта не может быть пустым` - Site name is empty
- `URL должен начинаться с http:// или https://` - URL must start with http:// or https://
- `Сайт с таким URL уже существует в конфигурации` - Site with this URL already exists

## Удаление сайта / Removing a Site

### Endpoint
```
DELETE /api/site?url={siteUrl}
```

### Пример запроса / Request Example
```bash
curl -X DELETE "http://localhost:8080/api/site?url=https://example.com"
```

### Успешный ответ / Success Response
```json
{
  "result": true
}
```

### Ответ с ошибкой / Error Response
```json
{
  "result": false,
  "error": "Сайт с таким URL не найден в конфигурации"
}
```

### Возможные ошибки / Possible Errors
- `URL не может быть пустым` - URL is empty
- `Сайт с таким URL не найден в конфигурации` - Site with this URL not found
- `Невозможно удалить сайт: индексация в процессе. Остановите индексацию перед удалением.` - Cannot remove site: indexing in progress. Stop indexing before removal.

## Рабочий процесс / Workflow

### 1. Добавление сайта и запуск индексации / Add site and start indexing
```bash
# Добавить сайт / Add site
curl -X POST http://localhost:8080/api/site \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://newsite.com",
    "name": "New Site"
  }'

# Запустить индексацию / Start indexing
curl http://localhost:8080/api/startIndexing
```

### 2. Остановка индексации и удаление сайта / Stop indexing and remove site
```bash
# Остановить индексацию / Stop indexing
curl http://localhost:8080/api/stopIndexing

# Удалить сайт / Remove site
curl -X DELETE "http://localhost:8080/api/site?url=https://newsite.com"
```

## Важные заметки / Important Notes

1. **Сохранение конфигурации / Configuration Persistence**: 
   - Сайты, добавленные через API, хранятся только в памяти приложения
   - После перезапуска приложения останутся только сайты из application.yaml
   - Sites added via API are stored only in application memory
   - After application restart, only sites from application.yaml will remain

2. **Совместимость / Compatibility**:
   - Новый API работает вместе с существующим методом через application.yaml
   - Можно использовать оба метода одновременно
   - The new API works alongside the existing application.yaml method
   - Both methods can be used simultaneously

3. **Проверка безопасности / Security**:
   - URL валидируется на наличие протокола http:// или https://
   - Проверяется уникальность URL перед добавлением
   - URL is validated for http:// or https:// protocol
   - URL uniqueness is checked before adding

4. **Удаление во время индексации / Removal during indexing**:
   - Нельзя удалить сайт, который в данный момент индексируется
   - Сначала остановите индексацию, затем удалите сайт
   - Cannot remove a site that is currently being indexed
   - Stop indexing first, then remove the site

## Примеры использования / Usage Examples

### Python Example
```python
import requests

# Add site
response = requests.post(
    'http://localhost:8080/api/site',
    json={'url': 'https://example.com', 'name': 'Example'}
)
print(response.json())

# Remove site
response = requests.delete(
    'http://localhost:8080/api/site',
    params={'url': 'https://example.com'}
)
print(response.json())
```

### JavaScript/Node.js Example
```javascript
// Add site
fetch('http://localhost:8080/api/site', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    url: 'https://example.com',
    name: 'Example'
  })
})
.then(res => res.json())
.then(data => console.log(data));

// Remove site
fetch('http://localhost:8080/api/site?url=https://example.com', {
  method: 'DELETE'
})
.then(res => res.json())
.then(data => console.log(data));
```
