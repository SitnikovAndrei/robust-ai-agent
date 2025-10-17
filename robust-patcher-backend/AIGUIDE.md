# Robust Patcher - Руководство для AI

## Краткий обзор

Robust Patcher - сервис для применения структурированных патчей к файлам. Используется для автоматизации изменений в кодовой базе.

**API Endpoint:** `POST http://localhost:8080/api/patch/apply`

---

## Формат патча

```
=== PATCH START ===
NAME: Название патча
DESCRIPTION: Описание
AUTHOR: Автор
VERSION: 1.0
---

--- FILE: путь/к/файлу.ext ---
ACTION: тип_действия
DESCRIPTION: Что делает

<<< БЛОК_ТИП
содержимое блока
БЛОК_ТИП >>>

=== PATCH END ===
```

---

## 8 типов действий

### 1. REPLACE - замена блока кода
```
ACTION: replace
<<< FIND
старый код
FIND >>>
<<< REPLACE
новый код
REPLACE >>>
```

### 2. INSERT_BEFORE - вставка перед маркером
```
ACTION: insert_before
<<< MARKER
маркер в файле
MARKER >>>
<<< CONTENT
новый код
CONTENT >>>
```

### 3. INSERT_AFTER - вставка после маркера
```
ACTION: insert_after
<<< MARKER
маркер в файле
MARKER >>>
<<< CONTENT
новый код
CONTENT >>>
```

### 4. DELETE - удаление блока
```
ACTION: delete
<<< FIND
код для удаления
FIND >>>
```

### 5. CREATE_FILE - создание нового файла
```
ACTION: create_file
<<< CONTENT
содержимое нового файла
CONTENT >>>
```

### 6. REPLACE_FILE - полная замена содержимого файла
```
ACTION: replace_file
<<< CONTENT
новое содержимое файла
CONTENT >>>
```
⚠️ Файл должен существовать! Для создания используй CREATE_FILE.

### 7. DELETE_FILE - удаление файла
```
ACTION: delete_file
<<< CONFIRM
true
CONFIRM >>>
```
🔴 Обязательно требуется CONFIRM: true

### 8. MOVE_FILE - перемещение/переименование
```
ACTION: move_file
<<< TO
новый/путь/файла.ext
TO >>>
<<< OVERWRITE
false
OVERWRITE >>>
```

---

## Правила для AI

### ✅ ОБЯЗАТЕЛЬНО:

1. **Точное совпадение** - блоки FIND/MARKER должны ТОЧНО совпадать с файлом (пробелы, отступы)
2. **Уникальные маркеры** - используй достаточно длинные блоки для уникальности
3. **CONFIRM для удаления** - всегда добавляй `CONFIRM: true` для delete_file
4. **Полные пути** - указывай полные пути к файлам от корня проекта
5. **Описания** - добавляй понятные DESCRIPTION для каждого действия

### ⚠️ ЧАСТЫЕ ОШИБКИ:

❌ **Неправильные отступы в FIND**
```
<<< FIND
function test(){  // БЕЗ пробела
FIND >>>
```

✅ **Правильно:**
```
<<< FIND
function test() {  // С пробелом как в оригинале
FIND >>>
```

❌ **Слишком короткий маркер**
```
<<< MARKER
{
MARKER >>>
```

✅ **Правильно:**
```
<<< MARKER
    companion object {
        const val VERSION = "1.0"
MARKER >>>
```

❌ **Забыл CONFIRM**
```
ACTION: delete_file
```

✅ **Правильно:**
```
ACTION: delete_file
<<< CONFIRM
true
CONFIRM >>>
```

### 🎯 КОГДА ИСПОЛЬЗОВАТЬ КАКОЙ ACTION:

| Задача | Action |
|--------|--------|
| Изменить часть файла | `replace` |
| Добавить строки в начало/конец блока | `insert_before` / `insert_after` |
| Удалить блок кода | `delete` |
| Создать новый файл | `create_file` |
| Переписать весь файл | `replace_file` |
| Удалить файл | `delete_file` |
| Переместить файл | `move_file` |

### 💡 СОВЕТЫ:

1. **Для больших изменений** - лучше использовать `replace_file` чем множество `replace`
2. **Порядок операций**:
   - Сначала создавай файлы (`create_file`)
   - Потом модифицируй (`replace`, `insert_*`)
   - В конце удаляй (`delete_file`)
3. **Группируй связанные изменения** в один патч
4. **Используй dry-run** - по умолчанию `dryRun: true`

---

## Быстрый шаблон

```
=== PATCH START ===
NAME: Краткое название
DESCRIPTION: Что делает патч
AUTHOR: AI Assistant
VERSION: 1.0
---

--- FILE: полный/путь/к/файлу.ext ---
ACTION: тип_действия
DESCRIPTION: Что делает с этим файлом

<<< БЛОК
содержимое
БЛОК >>>

=== PATCH END ===
```

---

## Примеры для копирования

### Добавить импорт
```
--- FILE: src/Main.kt ---
ACTION: insert_after
DESCRIPTION: Add logging import

<<< MARKER
package com.example
MARKER >>>

<<< CONTENT

import org.slf4j.LoggerFactory
CONTENT >>>
```

### Заменить функцию
```
--- FILE: src/Utils.kt ---
ACTION: replace
DESCRIPTION: Update helper function

<<< FIND
fun helper(x: Int): Int {
    return x * 2
}
FIND >>>

<<< REPLACE
fun helper(x: Int, multiplier: Int = 2): Int {
    return x * multiplier
}
REPLACE >>>
```

### Создать конфиг
```
--- FILE: config/settings.json ---
ACTION: create_file
DESCRIPTION: Create configuration file

<<< CONTENT
{
  "version": "1.0",
  "enabled": true
}
CONTENT >>>
```

### Переместить файл
```
--- FILE: old/location/File.kt ---
ACTION: move_file
DESCRIPTION: Move to new package

<<< TO
new/location/File.kt
TO >>>

<<< OVERWRITE
false
OVERWRITE >>>
```

---

## Статусы ответа

- **SUCCESS** - выполнено успешно ✅
- **SKIPPED** - пропущено (блок не найден, файл существует) ⏭️
- **FAILED** - ошибка выполнения ❌
- **FILE_NOT_FOUND** - файл не найден 🔍

---

## Чеклист перед созданием патча

- [ ] Все пути указаны полностью от корня проекта
- [ ] Блоки FIND/MARKER скопированы точно из файла
- [ ] Добавлен CONFIRM: true для delete_file
- [ ] Добавлены DESCRIPTION для всех операций
- [ ] Выбран правильный тип ACTION для задачи
- [ ] Проверена уникальность маркеров
- [ ] Патч начинается с `=== PATCH START ===`
- [ ] Патч заканчивается `=== PATCH END ===`

---

## Типичный workflow

1. **Создай патч** с правильной структурой
2. **Проверь** через `/api/patch/validate`
3. **Тестируй** с `dryRun: true`
4. **Применяй** с `dryRun: false`

---

**Помни:** Точность - ключ к успеху! Один лишний пробел = SKIPPED статус.