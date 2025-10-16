# Robust Patcher - Документация

## Содержание

- [Обзор](#обзор)
- [Возможности](#возможности)
- [API Endpoints](#api-endpoints)
- [Формат патча](#формат-патча)
- [Типы действий](#типы-действий)
- [Примеры патчей](#примеры-патчей)
- [Статусы выполнения](#статусы-выполнения)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Примеры использования API](#примеры-использования-api)

---

## Обзор

**Robust Patcher** - это RESTful сервис для применения структурированных патчей к файлам в проекте. Сервис позволяет автоматизировать изменения в кодовой базе через декларативный формат патчей.

### Технологии
- **Язык**: Kotlin
- **Фреймворк**: Ktor
- **Порт по умолчанию**: 8080

---

## Возможности

✅ **Модификация содержимого файлов**
- Замена блоков кода
- Вставка перед/после маркеров
- Удаление блоков

✅ **Управление файлами**
- Создание новых файлов
- Полная замена содержимого файлов
- Удаление файлов
- Перемещение/переименование файлов

✅ **Безопасность**
- Режим dry-run для предварительного просмотра
- Подтверждение для опасных операций
- Path traversal защита

✅ **Валидация**
- Проверка корректности патчей перед применением
- Детальная информация об ошибках

---

## API Endpoints

### 1. Health Check

**GET** `/api/health`

Проверка работоспособности сервиса.

**Response:**
```
OK
```

---

### 2. Применение патча

**POST** `/api/patch/apply`

Применяет патч к файловой системе.

#### Request Body

```json
{
  "patchContent": "string",
  "dryRun": true,
  "baseDir": "."
}
```

**Параметры:**

| Параметр | Тип | Обязательный | По умолчанию | Описание |
|----------|-----|--------------|--------------|----------|
| `patchContent` | string | Да | - | Содержимое патча в специальном формате |
| `dryRun` | boolean | Нет | `true` | Если `true`, изменения не применяются |
| `baseDir` | string | Нет | `"."` | Базовая директория для применения патча |

#### Response

```json
{
  "success": true,
  "metadata": {
    "name": "Patch Name",
    "description": "Description",
    "author": "Author Name",
    "version": "1.0"
  },
  "results": [
    {
      "file": "path/to/file.kt",
      "description": "Action description",
      "action": "replace",
      "status": "success",
      "message": "Success message"
    }
  ],
  "stats": {
    "success": 5,
    "skipped": 2,
    "failed": 0
  }
}
```

---

### 3. Валидация патча

**POST** `/api/patch/validate`

Проверяет корректность патча без его применения.

#### Request Body

```json
{
  "patchContent": "string"
}
```

#### Response (Success)

```json
{
  "valid": true,
  "metadata": {
    "name": "Patch Name",
    "description": "Description",
    "author": "Author Name",
    "version": "1.0"
  },
  "patchCount": 3
}
```

#### Response (Error)

```json
{
  "valid": false,
  "error": "Error message"
}
```

---

## Формат патча

### Базовая структура

Каждый патч должен начинаться с маркера `=== PATCH START ===` и заканчиваться `=== PATCH END ===`.

```
=== PATCH START ===
NAME: Patch Name
DESCRIPTION: What this patch does
AUTHOR: Your Name
VERSION: 1.0
---

[File patches here]

=== PATCH END ===
```

### Метаданные патча

| Поле | Обязательное | Описание |
|------|--------------|----------|
| `NAME` | Нет | Название патча (по умолчанию "Unnamed") |
| `DESCRIPTION` | Нет | Описание того, что делает патч |
| `AUTHOR` | Нет | Автор патча (по умолчанию "Unknown") |
| `VERSION` | Нет | Версия патча (по умолчанию "1.0") |

### Структура патча для файла

```
--- FILE: path/to/file.ext ---
ACTION: action_name
DESCRIPTION: What this action does

<<< BLOCK_TYPE
Block content here
BLOCK_TYPE >>>
```

---

## Типы действий

### 1. REPLACE - Замена блока кода

Заменяет найденный блок текста на новый.

#### Синтаксис

```
--- FILE: src/Main.kt ---
ACTION: replace
DESCRIPTION: Update greeting message

<<< FIND
fun greet() {
    println("Hello")
}
FIND >>>

<<< REPLACE
fun greet(name: String) {
    println("Hello, $name!")
}
REPLACE >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `FIND` | Блок текста для поиска (точное совпадение) |
| `REPLACE` | Блок текста для замены |

#### Особенности

- ✅ Блок `FIND` должен точно совпадать с содержимым файла
- ✅ Учитываются пробелы и отступы
- ⚠️ Если блок не найден, статус будет `SKIPPED`

---

### 2. INSERT_BEFORE - Вставка перед маркером

Вставляет новый код перед указанным маркером.

#### Синтаксис

```
--- FILE: src/Config.kt ---
ACTION: insert_before
DESCRIPTION: Add new configuration property

<<< MARKER
    companion object {
MARKER >>>

<<< CONTENT
    const val API_TIMEOUT = 30000
CONTENT >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `MARKER` | Маркер, перед которым вставляется контент |
| `CONTENT` | Контент для вставки |

#### Особенности

- ✅ Маркер остается в файле
- ✅ Новый контент вставляется с новой строки перед маркером
- 💡 Полезно для добавления импортов, свойств, методов

---

### 3. INSERT_AFTER - Вставка после маркера

Вставляет новый код после указанного маркера.

#### Синтаксис

```
--- FILE: src/Dependencies.kt ---
ACTION: insert_after
DESCRIPTION: Add new dependency

<<< MARKER
dependencies {
MARKER >>>

<<< CONTENT
    implementation("io.ktor:ktor-server-cors:2.3.0")
CONTENT >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `MARKER` | Маркер, после которого вставляется контент |
| `CONTENT` | Контент для вставки |

#### Особенности

- ✅ Маркер остается в файле
- ✅ Новый контент вставляется с новой строки после маркера
- 💡 Хорошо подходит для добавления элементов в списки

---

### 4. DELETE - Удаление блока кода

Удаляет найденный блок из файла.

#### Синтаксис

```
--- FILE: src/OldFeature.kt ---
ACTION: delete
DESCRIPTION: Remove deprecated function

<<< FIND
@Deprecated("Use newFunction instead")
fun oldFunction() {
    // old implementation
}
FIND >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `FIND` | Блок текста для удаления |

#### Особенности

- ⚠️ Полностью удаляет найденный блок
- ⚠️ Если блок не найден, статус будет `SKIPPED`
- 🔴 Используйте осторожно!

---

### 5. CREATE_FILE - Создание нового файла

Создает новый файл с указанным содержимым.

#### Синтаксис

```
--- FILE: src/NewFeature.kt ---
ACTION: create_file
DESCRIPTION: Create new feature file

<<< CONTENT
package com.example

class NewFeature {
    fun execute() {
        println("New feature!")
    }
}
CONTENT >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `CONTENT` | Полное содержимое нового файла |

#### Особенности

- ✅ Автоматически создает родительские директории
- ⚠️ Если файл уже существует, статус будет `SKIPPED`
- ✅ Не перезаписывает существующие файлы

---

### 6. REPLACE_FILE - Полная замена содержимого файла

Полностью заменяет содержимое существующего файла новым контентом.

#### Синтаксис

```
--- FILE: src/Config.kt ---
ACTION: replace_file
DESCRIPTION: Update entire configuration file

<<< CONTENT
package com.example.config

object AppConfig {
    const val VERSION = "2.0.0"
    const val API_URL = "https://api.example.com"
    const val TIMEOUT = 30000
}
CONTENT >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `CONTENT` | Новое содержимое файла |

#### Особенности

- 🔴 Полностью заменяет содержимое файла
- ⚠️ Если файл не существует, статус будет `FILE_NOT_FOUND`
- 💡 Используйте когда нужно полностью переписать файл
- ⚠️ Нет резервного копирования - используйте осторожно!

**Когда использовать REPLACE_FILE:**
- Файл нужно полностью переписать
- Проще заменить всё, чем делать множество мелких изменений
- Генерация конфигурационных файлов

**Когда НЕ использовать:**
- Для частичных изменений используйте `REPLACE`
- Если файл может не существовать, используйте `CREATE_FILE`

---

### 7. DELETE_FILE - Удаление файла

Удаляет файл.

#### Синтаксис

```
--- FILE: src/Deprecated.kt ---
ACTION: delete_file
DESCRIPTION: Remove deprecated file

<<< CONFIRM
true
CONFIRM >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `CONFIRM` | Подтверждение удаления (`true` или `yes`) |

#### Особенности

- 🔴 **Требует подтверждение** через `CONFIRM: true`
- ⚠️ Без подтверждения статус будет `FAILED`
- ⚠️ Если файл не существует, статус будет `SKIPPED`
- 🔴 Файл удаляется без возможности восстановления

---

### 8. MOVE_FILE - Перемещение/переименование файла

Перемещает файл в новое местоположение или переименовывает его.

#### Синтаксис

```
--- FILE: src/old/path/File.kt ---
ACTION: move_file
DESCRIPTION: Reorganize project structure

<<< TO
src/new/path/File.kt
TO >>>

<<< OVERWRITE
false
OVERWRITE >>>
```

#### Параметры

| Блок | Описание |
|------|----------|
| `TO` | Путь назначения |
| `OVERWRITE` | Перезаписать существующий файл (`true` или `yes`) |

#### Особенности

- ✅ Автоматически создает директории назначения
- ⚠️ Если файл назначения существует и `OVERWRITE: false`, статус будет `FAILED`
- ✅ Исходный файл удаляется после успешного копирования
- 💡 Можно использовать для переименования файла в той же директории

---

## Примеры патчей

### Пример 1: Простое обновление кода

```
=== PATCH START ===
NAME: Update Logger
DESCRIPTION: Replace println with proper logging
AUTHOR: John Doe
VERSION: 1.0
---

--- FILE: src/Main.kt ---
ACTION: replace
DESCRIPTION: Use logger instead of println

<<< FIND
println("Application started")
FIND >>>

<<< REPLACE
logger.info("Application started")
REPLACE >>>

=== PATCH END ===
```

---

### Пример 2: Добавление новой зависимости

```
=== PATCH START ===
NAME: Add CORS Support
DESCRIPTION: Add CORS plugin to the application
AUTHOR: Jane Smith
VERSION: 1.1
---

--- FILE: build.gradle.kts ---
ACTION: insert_after
DESCRIPTION: Add CORS dependency

<<< MARKER
dependencies {
MARKER >>>

<<< CONTENT
    implementation("io.ktor:ktor-server-cors:2.3.0")
CONTENT >>>

--- FILE: src/Application.kt ---
ACTION: insert_after
DESCRIPTION: Configure CORS

<<< MARKER
fun Application.module() {
MARKER >>>

<<< CONTENT
    configureCORS()
CONTENT >>>

--- FILE: src/plugins/CORS.kt ---
ACTION: create_file
DESCRIPTION: Create CORS configuration

<<< CONTENT
package com.example.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCORS() {
    install(CORS) {
        anyHost()
    }
}
CONTENT >>>

=== PATCH END ===
```

---

### Пример 3: Полная замена конфигурационного файла

```
=== PATCH START ===
NAME: Update Configuration
DESCRIPTION: Replace entire config with new version
AUTHOR: DevOps Team
VERSION: 2.0
---

--- FILE: src/config/AppConfig.kt ---
ACTION: replace_file
DESCRIPTION: Update to new configuration format

<<< CONTENT
package com.example.config

object AppConfig {
    // Database settings
    const val DB_HOST = "localhost"
    const val DB_PORT = 5432
    const val DB_NAME = "myapp"
    
    // API settings
    const val API_VERSION = "v2"
    const val API_TIMEOUT = 30000
    const val API_BASE_URL = "https://api.example.com"
    
    // Feature flags
    const val FEATURE_AUTH = true
    const val FEATURE_CACHE = true
}
CONTENT >>>

=== PATCH END ===
```

---

### Пример 4: Рефакторинг структуры проекта

```
=== PATCH START ===
NAME: Reorganize Models
DESCRIPTION: Move models to separate package and cleanup
AUTHOR: Dev Team
VERSION: 2.0
---

--- FILE: src/Models.kt ---
ACTION: move_file
DESCRIPTION: Move to models package

<<< TO
src/models/DomainModels.kt
TO >>>

<<< OVERWRITE
false
OVERWRITE >>>

--- FILE: src/old/LegacyCode.kt ---
ACTION: delete_file
DESCRIPTION: Remove legacy code

<<< CONFIRM
true
CONFIRM >>>

--- FILE: src/models/README.md ---
ACTION: create_file
DESCRIPTION: Add documentation for models package

<<< CONTENT
# Domain Models

This package contains all domain models for the application.

## Structure
- `DomainModels.kt` - Core domain entities
- `DTOs.kt` - Data Transfer Objects
- `Validators.kt` - Model validation logic
CONTENT >>>

=== PATCH END ===
```

---

### Пример 5: Комплексный патч с аутентификацией

```
=== PATCH START ===
NAME: Feature Authentication
DESCRIPTION: Add JWT authentication to the API
AUTHOR: Security Team
VERSION: 1.0
---

--- FILE: build.gradle.kts ---
ACTION: insert_after
DESCRIPTION: Add JWT dependencies

<<< MARKER
dependencies {
MARKER >>>

<<< CONTENT
    implementation("io.ktor:ktor-server-auth:2.3.0")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.0")
CONTENT >>>

--- FILE: src/Application.kt ---
ACTION: replace
DESCRIPTION: Add authentication configuration

<<< FIND
fun Application.module() {
    configureSerialization()
    configureRouting()
}
FIND >>>

<<< REPLACE
fun Application.module() {
    configureSerialization()
    configureAuthentication()
    configureRouting()
}
REPLACE >>>

--- FILE: src/plugins/Authentication.kt ---
ACTION: create_file
DESCRIPTION: Create authentication plugin

<<< CONTENT
package com.example.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

fun Application.configureAuthentication() {
    val secret = environment.config.property("jwt.secret").getString()
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT
                    .require(Algorithm.HMAC256(secret))
                    .withAudience(audience)
                    .withIssuer(issuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains(audience)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
CONTENT >>>

--- FILE: src/routes/ProtectedRoutes.kt ---
ACTION: create_file
DESCRIPTION: Add protected routes example

<<< CONTENT
package com.example.routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.protectedRoutes() {
    authenticate("auth-jwt") {
        get("/api/protected") {
            call.respondText("This is a protected endpoint")
        }
    }
}
CONTENT >>>

=== PATCH END ===
```

---

### Пример 6: Удаление устаревшего кода

```
=== PATCH START ===
NAME: Cleanup Deprecated Code
DESCRIPTION: Remove all deprecated functions and files
AUTHOR: Maintenance Team
VERSION: 1.0
---

--- FILE: src/Utils.kt ---
ACTION: delete
DESCRIPTION: Remove deprecated helper function

<<< FIND
@Deprecated("Use newHelper instead")
fun oldHelper(data: String): String {
    return data.uppercase()
}
FIND >>>

--- FILE: src/legacy/OldAPI.kt ---
ACTION: delete_file
DESCRIPTION: Remove old API implementation

<<< CONFIRM
true
CONFIRM >>>

--- FILE: src/docs/MIGRATION.md ---
ACTION: create_file
DESCRIPTION: Add migration guide

<<< CONTENT
# Migration Guide

## Deprecated Code Removal

### Removed Functions
- `oldHelper()` → Use `newHelper()` instead

### Removed Files
- `src/legacy/OldAPI.kt` → Use `src/api/NewAPI.kt`

Please update your code accordingly.
CONTENT >>>

=== PATCH END ===
```

---

## Статусы выполнения

| Статус | Описание |
|--------|----------|
| `SUCCESS` | Операция выполнена успешно |
| `SKIPPED` | Операция пропущена (файл/блок не найден, файл уже существует и т.д.) |
| `FAILED` | Операция завершилась с ошибкой |
| `FILE_NOT_FOUND` | Файл не найден (для операций с содержимым) |

---

## Best Practices

### 1. Всегда используйте dry-run сначала

Перед применением патча тестируйте его с `dryRun: true`:

```bash
curl -X POST http://localhost:8080/api/patch/apply \
  -H "Content-Type: application/json" \
  -d '{
    "patchContent": "...",
    "dryRun": true,
    "baseDir": "/path/to/project"
  }'
```

### 2. Точность маркеров и блоков FIND

При использовании FIND/MARKER блоков:

- ✅ Копируйте блоки напрямую из файлов
- ✅ Сохраняйте оригинальные отступы
- ✅ Используйте уникальные маркеры
- ⚠️ Проверяйте отсутствие лишних пробелов

**Плохо:**
```
<<< FIND
function test(){
FIND >>>
```

**Хорошо:**
```
<<< FIND
function test() {
    console.log("test");
}
FIND >>>
```

### 3. Описательные метаданные

Всегда добавляйте понятные описания:

```
NAME: Add User Authentication
DESCRIPTION: Implements JWT-based authentication for user endpoints
AUTHOR: Security Team
VERSION: 1.0
```

Для каждого файла:
```
DESCRIPTION: Add authentication middleware to protect admin routes
```

### 4. Версионирование патчей

Увеличивайте версию при изменениях:

```
VERSION: 1.0  # Initial release
VERSION: 1.1  # Fixed typo in file path
VERSION: 2.0  # Major refactoring
```

### 5. Подтверждение опасных операций

Всегда требуйте подтверждение для `delete_file`:

```
--- FILE: important/data.json ---
ACTION: delete_file
DESCRIPTION: Remove sensitive data file

<<< CONFIRM
true
CONFIRM >>>
```

### 6. Правильный порядок операций

Применяйте патчи в логическом порядке:

1. **Создание новых файлов** (`create_file`)
2. **Модификация существующих** (`replace`, `insert_before`, `insert_after`)
3. **Полная замена содержимого** (`replace_file`)
4. **Перемещение файлов** (`move_file`)
5. **Удаление устаревших файлов** (`delete_file`)

### 7. Атомарность патчей

Группируйте связанные изменения в один патч:

```
NAME: Feature Complete Authentication
DESCRIPTION: Adds all necessary components for JWT auth
```

А не создавайте отдельные патчи для каждого маленького изменения.

### 8. Выбор правильного действия

| Задача | Действие |
|--------|----------|
| Изменить часть файла | `replace`, `insert_before`, `insert_after` |
| Переписать весь файл | `replace_file` |
| Создать новый файл | `create_file` |
| Удалить блок кода | `delete` |
| Удалить файл | `delete_file` |
| Переместить файл | `move_file` |

### 9. Валидация перед применением

Используйте endpoint `/api/patch/validate` для проверки:

```bash
curl -X POST http://localhost:8080/api/patch/validate \
  -H "Content-Type: application/json" \
  -d @patch.json
```

---

## Troubleshooting

### Проблема: "Блок не найден"

**Статус:** `SKIPPED`

**Причина:** Точное совпадение не найдено в файле.

**Решения:**

1. Проверьте пробелы и отступы:
```
# Неправильно
<<< FIND
function test(){
FIND >>>

# Правильно
<<< FIND
function test() {
FIND >>>
```

2. Убедитесь, что блок существует в файле
3. Используйте более специфичный маркер
4. Проверьте кодировку файла (должна быть UTF-8)

---

### Проблема: "Файл уже существует"

**Статус:** `SKIPPED`

**Причина:** При `create_file` файл уже существует.

**Решения:**

- Используйте `replace_file` вместо `create_file`
- Используйте `replace` для изменения частей файла
- Удалите существующий файл сначала

---

### Проблема: "Требуется подтверждение"

**Статус:** `FAILED`

**Причина:** `delete_file` требует `CONFIRM: true`.

**Решение:**

```
--- FILE: path/to/file.txt ---
ACTION: delete_file

<<< CONFIRM
true
CONFIRM >>>
```

---

### Проблема: "Файл назначения существует"

**Статус:** `FAILED`

**Причина:** При `move_file` целевой файл уже существует.

**Решение:**

```
--- FILE: old/path/file.txt ---
ACTION: move_file

<<< TO
new/path/file.txt
TO >>>

<<< OVERWRITE
true
OVERWRITE >>>
```

---

### Проблема: "Файл не существует"

**Статус:** `FILE_NOT_FOUND`

**Причина:** Файл для модификации/замены/перемещения не найден.

**Решения:**

- Проверьте путь к файлу
- Используйте `create_file` если файл должен быть создан
- Проверьте параметр `baseDir`

---

### Проблема: "Содержимое не изменилось"

**Статус:** `SKIPPED`

**Причина:** После применения операции содержимое файла не изменилось.

**Возможные причины:**

- Замена идентична оригиналу
- Блок `FIND` и `REPLACE` одинаковы
- Контент уже присутствует в файле

---

### Проблема: "Ошибка записи"

**Статус:** `FAILED`

**Причина:** Нет прав на запись файла.

**Решения:**

- Проверьте права доступа к файлу
- Проверьте права доступа к директории
- Убедитесь, что файл не заблокирован другим процессом

---

## Примеры использования API

### cURL

#### Применение патча

```bash
curl -X POST http://localhost:8080/api/patch/apply \
  -H "Content-Type: application/json" \
  -d '{
    "patchContent": "=== PATCH START ===\nNAME: Test\n...\n=== PATCH END ===",
    "dryRun": false,
    "baseDir": "/path/to/project"
  }'
```

#### Валидация патча

```bash
curl -X POST http://localhost:8080/api/patch/validate \
  -H "Content-Type: application/json" \
  -d @patch.json
```

#### Health check

```bash
curl http://localhost:8080/api/health
```

---

### Python

```python
import requests
import json

# Читаем патч из файла
with open('patch.txt', 'r', encoding='utf-8') as f:
    patch_content = f.read()

# Применяем патч (dry-run)
response = requests.post(
    'http://localhost:8080/api/patch/apply',
    json={
        'patchContent': patch_content,
        'dryRun': True,
        'baseDir': '/home/user/project'
    }
)

result = response.json()

if result['success']:
    print(f"✅ Успешно: {result['stats']['success']}")
    print(f"⏭️  Пропущено: {result['stats']['skipped']}")
    print(f"❌ Ошибок: {result['stats']['failed']}")
    
    # Если всё ок, применяем реально
    if result['stats']['failed'] == 0:
        response = requests.post(
            'http://localhost:8080/api/patch/apply',
            json={
                'patchContent': patch_content,
                'dryRun': False,
                'baseDir': '/home/user/project'
            }
        )
        print("Патч применён!")
else:
    print(f"❌ Ошибка: {result.get('error')}")
```

---

### JavaScript/Node.js

```javascript
const axios = require('axios');
const fs = require('fs');

async function applyPatch(patchFile, baseDir, dryRun = true) {
    const patchContent = fs.readFileSync(patchFile, 'utf8');
    
    try {
        const response = await axios.post(
            'http://localhost:8080/api/patch/apply',
            {
                patchContent,
                dryRun,
                baseDir
            }
        );
        
        const result = response.data;
        
        console.log(`✅ Success: ${result.stats.success}`);
        console.log(`⏭️  Skipped: ${result.stats.skipped}`);
        console.log(`❌ Failed: ${result.stats.failed}`);
        
        return result;
    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
        throw error;
    }
}

// Использование
(async () => {
    // Сначала dry-run
    const dryRunResult = await applyPatch('patch.txt', '/path/to/project', true);
    
    // Если нет ошибок, применяем
    if (dryRunResult.stats.failed === 0) {
        await applyPatch('patch.txt', '/path/to/project', false);
        console.log('✅ Патч успешно применён!');
    }
})();
```

---

### Kotlin

```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import java.io.File

data class PatchRequest(
    val patchContent: String,
    val dryRun: Boolean = true,
    val baseDir: String = "."
)

suspend fun applyPatch(patchFile: String, baseDir: String, dryRun: Boolean = true) {
    val client = HttpClient()
    val patchContent = File(patchFile).readText()
    
    val response: HttpResponse = client.post("http://localhost:8080/api/patch/apply") {
        contentType(ContentType.Application.Json)
        setBody(
            PatchRequest(
                patchContent = patchContent,
                dryRun = dryRun,
                baseDir = baseDir
            )
        )
    }
    
    println(response.bodyAsText())
    client.close()
}
```

---

## Запуск сервиса

### Gradle

```bash
./gradlew run
```

### Проверка работоспособности

```bash
curl http://localhost:8080/api/health
# Ответ: OK
```

### Изменение порта

Отредактируйте `Application.kt`:

```kotlin
embeddedServer(
    Netty, 
    port = 9090,  // Ваш порт
    host = "0.0.0.0",
    module = Application::module
).start(wait = true)
```

---

## Безопасность

⚠️ **Важные замечания:**

1. **Базовая директория** должна существовать и быть доступной
2. **Path traversal защита:** используется `canonicalFile` для предотвращения выхода за пределы базовой директории
3. **Нет резервного копирования:** операции выполняются напрямую без создания бэкапов
4. **Dry-run режим** включен по умолчанию
5. **CORS настроен на `anyHost()`** - **ОБЯЗАТЕЛЬНО ограничьте в production!**

### Рекомендации для Production

```kotlin
// CORS.kt
fun Application.configureCORS() {
    install(CORS) {
        allowHost("your-frontend-domain.com", schemes = listOf("https"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Post)
    }
}
```

---

## Лицензия

Robust Patcher Backend v1.0.0

© 2025. Все права защищены.