# Документация формата патчей

## Структура патча

```markdown
### PATCH: [название патча]

**Description:** [описание патча]  
**Author:** [автор]  
**Version:** [версия]

---

[секции файлов]

---
```

## Общие параметры (Options)

Параметры указываются в блоке `**Options:**` после `**Action:**`

### Параметры сопоставления (Match Mode)

| Параметр | Значения | По умолчанию | Описание |
|----------|----------|--------------|----------|
| MATCH_MODE | normalized, fuzzy, tokenized, semantic, regex, contains, line_range | normalized | Режим поиска совпадений |
| FUZZY_THRESHOLD | 0.0 - 1.0 | 0.85 | Порог нечёткого сравнения (только для fuzzy) |
| IGNORE_COMMENTS | true, false | false | Игнорировать комментарии при сопоставлении |
| IGNORE_EMPTY_LINES | true, false | true | Игнорировать пустые строки |
| CASE_SENSITIVE | true, false | true | Учитывать регистр |
| TOKEN_WINDOW_SIZE | число | 0 | Размер окна токенов (для tokenized) |

### Параметры структурного сопоставления

| Параметр | Значения | По умолчанию | Описание |
|----------|----------|--------------|----------|
| MATCH_FUNCTION_NAME | true, false | true | Сопоставлять имя функции |
| MATCH_CLASS_NAME | true, false | true | Сопоставлять имя класса |
| MATCH_PARAMETER_TYPES | true, false | true | Сопоставлять типы параметров |
| MATCH_PARAMETER_NAMES | true, false | false | Сопоставлять имена параметров |
| MATCH_RETURN_TYPE | true, false | true | Сопоставлять тип возвращаемого значения |
| MATCH_MODIFIERS | true, false | false | Сопоставлять модификаторы (public, private и т.д.) |

### Параметры якоря (Anchor)

| Параметр | Значения | По умолчанию | Описание |
|----------|----------|--------------|----------|
| ANCHOR_MATCH_MODE | normalized, fuzzy, semantic | normalized | Режим поиска якоря |
| ANCHOR_SCOPE | function, class, block, file, auto | auto | Область поиска относительно якоря |
| ANCHOR_SEARCH_DEPTH | число | 1 | Глубина поиска от якоря |

### Другие параметры

| Параметр | Значения | По умолчанию | Описание |
|----------|----------|--------------|----------|
| OVERWRITE | true, false | false | Перезаписать существующий файл (только для move) |

---

## Примеры действий

### 1. CREATE_FILE - Создание нового файла

```markdown
### PATCH: Add new utility class

---

#### File: `src/utils/StringHelper.kt`
**Action:** create_file
**Description:** Add string manipulation utilities

```
package utils

object StringHelper {
fun capitalize(text: String): String {
return text.replaceFirstChar { it.uppercase() }
}

    fun truncate(text: String, maxLength: Int): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
    }
}
```

---
```

### 2. REPLACE - Замена кода

```markdown
### PATCH: Update authentication method

---

#### File: `src/auth/AuthService.kt`
**Action:** replace
**Description:** Replace deprecated authentication with new OAuth2 flow

**Options:**
- MATCH_MODE: fuzzy
- FUZZY_THRESHOLD: 0.9
- IGNORE_COMMENTS: true

**From:**
```
fun authenticate(username: String, password: String): Boolean {
val hash = MD5.hash(password)
return database.verifyCredentials(username, hash)
}
```

**To:**
```
suspend fun authenticate(username: String, password: String): AuthResult {
val token = oauth2Client.authenticate(username, password)
return AuthResult.success(token)
}
```

---
```

### 3. INSERT_BEFORE - Вставка перед кодом

```markdown
### PATCH: Add validation before processing

---

#### File: `src/services/PaymentService.kt`
**Action:** insert_before
**Description:** Add amount validation before payment processing

**Options:**
- MATCH_MODE: normalized
- CASE_SENSITIVE: true

**Before this:**
```
fun processPayment(amount: Double, userId: String) {
val transaction = createTransaction(amount, userId)
gateway.process(transaction)
}
```

**Insert:**
```
private fun validateAmount(amount: Double) {
require(amount > 0) { "Amount must be positive" }
require(amount <= MAX_TRANSACTION_AMOUNT) { "Amount exceeds limit" }
}

```

---
```

### 4. INSERT_AFTER - Вставка после кода

```markdown
### PATCH: Add logging after user creation

---

#### File: `src/services/UserService.kt`
**Action:** insert_after
**Description:** Add audit logging for new users

**Options:**
- MATCH_MODE: semantic
- MATCH_FUNCTION_NAME: true

**After this:**
```
fun createUser(email: String, name: String): User {
val user = User(
id = generateId(),
email = email,
name = name
)
database.save(user)
return user
}
```

**Insert:**
```

fun logUserCreation(user: User) {
auditLogger.info("New user created: ${user.id}, email: ${user.email}")
analytics.trackEvent("user_created", mapOf("userId" to user.id))
}
```

---
```

### 5. DELETE - Удаление кода

```markdown
### PATCH: Remove deprecated debug code

---

#### File: `src/utils/Logger.kt`
**Action:** delete
**Description:** Remove old debug printing function

**Options:**
- MATCH_MODE: contains
- IGNORE_EMPTY_LINES: true

**Remove this:**
```
@Deprecated("Use Logger.debug() instead")
fun debugPrint(message: String) {
println("[DEBUG] $message")
System.out.flush()
}
```

---
```

### 6. DELETE_FILE - Удаление файла

```markdown
### PATCH: Remove legacy authentication

---

#### File: `src/auth/LegacyAuth.kt`
**Action:** delete_file
**Description:** Remove deprecated authentication module

---
```

### 7. MOVE - Перемещение файла

```markdown
### PATCH: Reorganize project structure

---

#### File: `src/utils/StringUtils.kt` → `src/common/text/StringUtils.kt`
**Action:** move
**Description:** Move string utilities to new common package

**Options:**
- OVERWRITE: false

---
```

### 8. REPLACE_FILE - Полная замена содержимого файла

```markdown
### PATCH: Replace configuration file

---

#### File: `config/database.properties`
**Action:** replace_file
**Description:** Update to new database configuration format

```
# Database Configuration v2.0
db.driver=postgresql
db.host=localhost
db.port=5432
db.name=myapp
db.pool.size=20
db.pool.timeout=30000

# Connection options
db.ssl.enabled=true
db.ssl.mode=require
```

---
```

## Примеры с якорем (Anchor)

### Вставка в контексте класса

```markdown
### PATCH: Add method to specific class

---

#### File: `src/models/User.kt`
**Action:** insert_after
**Description:** Add email validation method

**Options:**
- ANCHOR_SCOPE: class
- ANCHOR_MATCH_MODE: fuzzy

**Anchor:**
```
data class User(
val id: String,
val email: String,
val name: String
)
```

**After this:**
```
fun getDisplayName(): String {
return name.ifEmpty { email }
}
```

**Insert:**
```

fun isValidEmail(): Boolean {
val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$".toRegex()
return emailRegex.matches(email)
}
```

---
```

## Сложный пример с несколькими файлами

```markdown
### PATCH: Refactor authentication system

**Description:** Migrate from basic auth to OAuth2  
**Author:** John Doe  
**Version:** 2.0

---

#### File: `src/auth/AuthService.kt`
**Action:** replace
**Description:** Update main authentication method

**Options:**
- MATCH_MODE: fuzzy
- FUZZY_THRESHOLD: 0.85

**From:**
```
fun login(username: String, password: String): Session {
if (validateCredentials(username, password)) {
return createSession(username)
}
throw AuthException("Invalid credentials")
}
```

**To:**
```
suspend fun login(username: String, password: String): Session {
val token = oauth2Client.authenticate(username, password)
return createSession(username, token)
}
```

---

#### File: `src/auth/OAuth2Client.kt`
**Action:** create_file
**Description:** Add OAuth2 client implementation

```
package auth

import io.ktor.client.*
import io.ktor.client.request.*

class OAuth2Client(
private val clientId: String,
private val clientSecret: String,
private val tokenUrl: String
) {
private val client = HttpClient()

    suspend fun authenticate(username: String, password: String): String {
        val response = client.post(tokenUrl) {
            parameter("grant_type", "password")
            parameter("username", username)
            parameter("password", password)
            parameter("client_id", clientId)
            parameter("client_secret", clientSecret)
        }
        return response.body()
    }
}
```

---

#### File: `src/auth/BasicAuthenticator.kt`
**Action:** delete_file
**Description:** Remove old basic auth implementation

---

#### File: `src/config/auth-config.properties` → `src/config/oauth2-config.properties`
**Action:** move
**Description:** Rename config file to reflect OAuth2

---
```

## Режимы сопоставления (MATCH_MODE)

### normalized
Нормализует код (убирает лишние пробелы, переносы) перед сопоставлением.

### fuzzy
Нечёткое сопоставление с порогом similarity (FUZZY_THRESHOLD).

### tokenized
Разбивает код на токены и сравнивает последовательности токенов.

### semantic
Семантическое сопоставление (понимает структуру кода).

### regex
Использует регулярные выражения для поиска.

### contains
Простой поиск подстроки.

### line_range
Сопоставление по диапазону строк.