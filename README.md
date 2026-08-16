# Minecraft Project

Небольшая, но уже вполне живая система для управления Minecraft-серверами.
Тут есть веб-панель на Spring Boot, прокси на Velocity, свой плагин авторизации и отдельная сборка для прод-запуска

> Идея простая: зайти на сайт, управлять серверами, логиниться через прокси и не собирать всё это руками каждый раз

## Что здесь есть

- Spring Boot приложение с веб-интерфейсом и API
- Velocity proxy + свой auth plugin
- PicoLimbo как auth/lobby сервер
- система аккаунтов, JWT и привязка Minecraft-аккаунта
- TOTP 2FA
- WebSocket-обновления для runtime-инфы
- готовые bat-скрипты для локального запуска и деплоя

## Из чего состоит проект

### minecraft-server-operator
Основное приложение на Spring Boot 3

Что умеет:
- регистрация и логин пользователей
- dashboard с серверами
- создание и просмотр серверов
- админка для invite-ссылок
- TOTP-настройка для аккаунта
- WebSocket-обновления runtime и деталей серверов

Технологии:
- Java 21
- Spring Boot
- Thymeleaf
- WebSocket
- Spring Data JPA
- H2

### velocity-plugin
Плагин для Velocity, который общается с backend API и управляет авторизацией игроков прямо в прокси

Команды плагина:
- `/login`
- `/getmy`
- `/getpublic`
- `/getaccessible`
- `/join`
- `/unlinkaccount`
- `/limbo`
- `/serverinfo`

### picolimbo
Лёгкий лимбо-сервер для входа и авторизации игроков

### prod
Готовая production-структура с `docker-compose.yml`, куда складывается собранный проект после деплоя

## Структура

```text
minecraft-project/
|- minecraft-server-operator/   # Spring Boot backend + web UI
|- velocity-plugin/             # плагин для Velocity
|- velocity/                    # локальная папка proxy
|- picolimbo/                   # auth/lobby сервер
|- prod/                        # production-сборка
|- build.bat
|- deploy.bat
`- run.bat
```

## Быстрый старт

### 1. Сборка

Из корня проекта:

```bat
build.bat
```

Что делает этот скрипт:
- собирает `minecraft-server-operator`
- собирает `velocity-plugin`
- копирует плагин в папку Velocity
- генерирует `run.bat` для operator, Velocity и PicoLimbo

### 2. Локальный запуск

После сборки:

```bat
run.bat
```

Этот скрипт поднимает в отдельных окнах:
- PicoLimbo
- Velocity
- Spring Boot operator

## Production / Deploy

Чтобы собрать готовую prod-папку:

```bat
deploy.bat
```

После этого проект будет лежать в папке `prod/`.

Запуск на Windows:

```bat
cd prod
run.bat
```

Запуск через Docker Compose:

```bat
cd prod
docker-compose up -d
```

## Конфиг

Основные настройки лежат тут:

- `minecraft-server-operator/src/main/resources/application.yml`
- `velocity/velocity.toml`
- `picolimbo/server.toml`

Из важного по умолчанию:
- Spring Boot приложение работает на порту `2036`
- Velocity обычно слушает `25565`
- PicoLimbo обычно слушает `25567`
- база данных по умолчанию: `H2`

## Для чего проект вообще

Если коротко, это связка для такого сценария:

1. Пользователь регистрируется и логинится
2. При желании включает 2FA
3. Заходит на прокси через Velocity
4. Привязывает Minecraft-аккаунт
5. Получает доступ к своим или публичным серверам