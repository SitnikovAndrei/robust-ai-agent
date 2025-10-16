# 🔧 Robust Patcher - Шпаргалка

## ⚡ Быстрый старт

```bash
kotlinc -script RobustPatcher.kts -- patch.patch           # Применить
kotlinc -script RobustPatcher.kts -- patch.patch --dry-run # Проверить
```

## 📋 Все операции

| Операция | Назначение | Блоки |
|----------|-----------|-------|
| `replace` | Заменить код | `FIND`, `REPLACE` |
| `insert_before` | Вставить перед | `MARKER`, `CONTENT` |
| `insert_after` | Вставить после | `MARKER`, `CONTENT` |
| `delete` | Удалить блок | `FIND` |
| `create` | Создать файл | `CONTENT` |
| `delete_file` | Удалить файл | `CONFIRM` |

## 🎯 Шаблон патча

```
=== PATCH START ===
NAME: <название>
DESCRIPTION: <описание>
AUTHOR: <автор>
VERSION: 1.0

--- FILE: <путь/к/файлу> ---
ACTION: <операция>
DESCRIPTION: <что делаем>

<<< BLOCK_TYPE
содержимое блока
BLOCK_TYPE >>>

=== PATCH END ===
```

## 📝 Примеры

### 1️⃣ Replace - Замена кода

```
--- FILE: src/Service.kt ---
ACTION: replace
DESCRIPTION: Обновить метод

<<< FIND
fun old() {
    println("old")
}
FIND >>>

<<< REPLACE
fun new() {
    println("new")
}
REPLACE >>>
```

### 2️⃣ Insert Before - Добавить перед

```
--- FILE: src/Service.kt ---
ACTION: insert_before
DESCRIPTION: Добавить импорт

<<< MARKER
class Service {
MARKER >>>

<<< CONTENT
import java.util.*

CONTENT >>>
```

### 3️⃣ Insert After - Добавить после

```
--- FILE: src/Service.kt ---
ACTION: insert_after
DESCRIPTION: Добавить метод

<<< MARKER
class Service {
MARKER >>>

<<< CONTENT
    fun helper() = 42
CONTENT >>>
```

### 4️⃣ Delete - Удалить блок

```
--- FILE: src/Service.kt ---
ACTION: delete
DESCRIPTION: Удалить deprecated

<<< FIND
@Deprecated
fun old() {
    // old code
}
FIND >>>
```

### 5️⃣ Create - Создать файл

```
--- FILE: src/NewService.kt ---
ACTION: create
DESCRIPTION: Новый сервис

<<< CONTENT
package com.example

class NewService {
    fun work() {}
}
CONTENT >>>
```

### 6️⃣ Delete File - Удалить файл

```
--- FILE: src/OldService.kt ---
ACTION: delete_file
DESCRIPTION: Удалить старый файл

<<< CONFIRM
true
CONFIRM >>>
```

## 💡 Полный пример

```
=== PATCH START ===
NAME: Add Email Service
DESCRIPTION: Создать email сервис и интегрировать
AUTHOR: AI
VERSION: 1.0

--- FILE: src/EmailService.kt ---
ACTION: create
DESCRIPTION: Создать сервис

<<< CONTENT
package com.example

class EmailService {
    fun send(to: String) {
        println("Email to: $to")
    }
}
CONTENT >>>

--- FILE: src/UserService.kt ---
ACTION: insert_before
DESCRIPTION: Добавить импорт

<<< MARKER
class UserService(
MARKER >>>

<<< CONTENT
import com.example.EmailService

CONTENT >>>

--- FILE: src/UserService.kt ---
ACTION: replace
DESCRIPTION: Инжектить EmailService

<<< FIND
class UserService(
    private val repo: UserRepo
) {
FIND >>>

<<< REPLACE
class UserService(
    private val repo: UserRepo,
    private val emailService: EmailService
) {
REPLACE >>>

--- FILE: src/UserService.kt ---
ACTION: insert_after
DESCRIPTION: Отправить email после создания

<<< MARKER
    fun create(user: User): User {
        val saved = repo.save(user)
MARKER >>>

<<< CONTENT
        emailService.send(saved.email)
CONTENT >>>

--- FILE: src/OldNotifier.kt ---
ACTION: delete_file
DESCRIPTION: Удалить старый notifier

<<< CONFIRM
true
CONFIRM >>>

=== PATCH END ===
```

## 🔒 Безопасность

| Операция | Защита |
|----------|--------|
| `create` | Пропускает если файл существует |
| `delete_file` | Требует `CONFIRM: true` |
| Все изменения | Создают `.backup` |
| `delete_file` | Создаёт `.deleted` бэкап |

## ✅ Best Practices

1. **Порядок операций**: `create` → `modify` → `delete_file`
2. **Проверка**: Всегда начинай с `--dry-run`
3. **Контекст**: В `FIND` включай 2-3 строки вокруг
4. **Точность**: Копируй код из файла с отступами
5. **Опциональность**: Используй `OPTIONAL: true` для необязательных файлов

## 🎨 Статусы выполнения

- ✅ **SUCCESS** - Применено
- ⏭️ **SKIPPED** - Пропущено (не найдено/уже есть)
- ❌ **FAILED** - Ошибка
- ⚠️ **FILE_NOT_FOUND** - Файл не найден (ок если `OPTIONAL: true`)

## 🚀 Типичный workflow

```bash
# 1. Создать патч (создать/изменить/удалить)
nano my-feature.patch

# 2. Проверить что изменится
kotlinc -script RobustPatcher.kts -- my-feature.patch --dry-run

# 3. Применить изменения
kotlinc -script RobustPatcher.kts -- my-feature.patch

# 4. Собрать проект
./gradlew build
```

## 📚 Дополнительно

**Опциональные файлы:**
```
--- FILE: src/Optional.kt ---
ACTION: replace
OPTIONAL: true
...
```

**Множество изменений в одном файле:**
```
--- FILE: src/Same.kt ---
ACTION: replace
...

--- FILE: src/Same.kt ---
ACTION: insert_after
...
```

**Восстановление из бэкапа:**
```bash
mv file.kt.backup file.kt        # Откат одного файла
mv file.kt.deleted file.kt       # Восстановление удалённого
```

---

💡 **Совет**: Один патч = одна логическая задача (фича/фикс/рефакторинг)