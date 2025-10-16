@echo off

chcp 65001

REM Redis Dumper - Windows batch wrapper script
REM Автоматически находит JAR файл и запускает утилиту

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set JAR_NAME=upatcher.kts

REM Поиск JAR файла
if exist "%SCRIPT_DIR%%JAR_NAME%" (
    set JAR_PATH=%SCRIPT_DIR%%JAR_NAME%
)



REM Настройки JVM (можно изменить при необходимости)
if not defined JVM_OPTS set JVM_OPTS=-Xmx2g -Xms512m

REM Запуск приложения
kotlinc -script "%JAR_PATH%" -- %*