# TaskFlow

TaskFlow — планировщик на Spring Boot, PostgreSQL и Thymeleaf. Веб-версия работает в браузере, а APK из папки `android` открывает тот же интерфейс на телефоне.

## APK для Android

Скачайте [TaskFlow.apk](android/build/TaskFlow.apk) на телефон с Android 7.0 или новее и откройте файл для установки. APK 1.0.1 устанавливается поверх 1.0.0. Подключите телефон к той же Wi-Fi сети, что и компьютер с запущенным TaskFlow. В APK уже указан адрес `http://192.168.1.69:8080`. Если адрес ПК изменится, нажмите «Изменить адрес» на экране ошибки подключения.

Задачи хранятся на сервере. Если компьютер выключен, запустите его и выполните `start-taskflow.cmd`. [Сборка APK и подпись](android/README.md)

## Быстрый запуск на Windows

Запустите `start-taskflow.cmd` в PowerShell из папки проекта. Он использует установленный JDK и запускает TaskFlow с PostgreSQL на `http://localhost:8080`. Если JAR отсутствует, скрипт соберёт его через Maven Wrapper. Для первой установки PostgreSQL из npm нужен Node.js. Дальнейшие запуски используют уже скачанный PostgreSQL.

Скрипт выводит адрес для телефона в той же Wi-Fi сети. База `taskflow_db` сохраняется в `.taskflow/postgres-data`. PostgreSQL слушает только `127.0.0.1:55432`. Конфигурация с отдельным PostgreSQL в IntelliJ описана ниже.

```powershell
.\start-taskflow.cmd
```

Чтобы остановить приложение и базу, выполните `.\start-taskflow.cmd stop`. Логи приложения находятся в `.taskflow/server.log` и `.taskflow/server-error.log`. Для пересборки остановите сервер, задайте `JAVA_HOME` с путём к JDK, выполните `.\mvnw.cmd package` и снова запустите `start-taskflow.cmd`.

## Почта для регистрации и восстановления пароля

Новые аккаунты подтверждают адрес кодом из письма. Если вы забыли пароль, на входе выберите «Забыли пароль?», получите код и введите новый пароль. Код действует 15 минут, используется один раз и блокируется после пяти неверных попыток. После смены пароля ранее открытые сеансы завершаются.

Для отправки через Gmail создайте файл `.taskflow/mail.properties` в корне проекта. Укажите в нём почту отправителя и [пароль приложения Google](https://support.google.com/accounts/answer/185833). Чтобы создать такой пароль, в аккаунте Google должна быть включена двухэтапная проверка. Адрес сервера и порт указаны в [настройках Gmail SMTP](https://support.google.com/mail/answer/7104828).

```properties
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-mail@gmail.com
spring.mail.password=your-app-password-without-spaces
```

Сохраните файл локально и перезапустите TaskFlow через `start-taskflow.cmd stop`, затем `start-taskflow.cmd`. Файл находится в игнорируемой папке `.taskflow`. Другой почтовый сервис можно подключить через `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` и `SMTP_FROM`. По умолчанию используется STARTTLS с проверкой сертификата.

Без настроенного SMTP новые регистрации и запросы кодов недоступны. Существующие аккаунты по-прежнему могут войти со своим паролем. Получатели могут использовать Gmail и другие почтовые адреса.

## Что реализовано

- Регистрация с подтверждением email, восстановление пароля по почте, вход, выход, BCrypt и сессионная авторизация Spring Security.
- Проверка email без учёта регистра, валидация форм, CSRF, экранирование пользовательского текста, запрет кеширования приватного HTML, Content Security Policy.
- Создание, редактирование, удаление и завершение задач, возврат в активные, описание, дата, три приоритета и категория.
- Поиск по названию и описанию, фильтры по статусу, дню, просрочке, категории и приоритету; сортировка по сроку, приоритету, названию и времени создания.
- Четыре начальные категории для каждого нового аккаунта; создание, изменение названия и цвета, удаление с сохранением задач.
- Dashboard с настоящей статистикой, прогрессом дня, ближайшими задачами, календарём и завершениями за текущую неделю.
- Таймер фокуса на 25 минут. Задачу можно выбрать из активных, поставить сессию на паузу и продолжить после перезагрузки страницы.
- Полный календарь с переходом по месяцам и задачами выбранного дня. Напоминания о сегодняшних и просроченных задачах в колокольчике.
- Редактирование имени, смена пароля с проверкой текущего и повторным входом, настройки темы.
- Светлая, тёмная и системная темы; сохранение выбора в браузере. Inter и Lucide хранятся локально.
- Мобильная навигация, плавающая кнопка создания, bottom sheet на телефоне, dialog на компьютере; клавиша **N** открывает создание задачи.
- PWA manifest, обычные и maskable PNG-иконки, Service Worker, экран отсутствия сети и установка на Android в безопасном контексте.
- Каждый запрос к задаче или категории проверяет владельца. Чужие ID возвращают 404, чужая категория не может быть назначена задаче.

Новый аккаунт пустой: задачи и статистика появляются из ваших данных. Вымышленных задач и предустановленного общего аккаунта нет.

## Зависимости

- **JDK 21** (можно использовать более новый совместимый JDK; Maven компилирует с `--release 21`).
- **PostgreSQL 17+**, локально или в Docker.
- IntelliJ IDEA с поддержкой Java и Maven. Ultimate удобнее для Spring, но не обязательна для запуска `main`.
- Maven Wrapper уже включён. Отдельно устанавливать Maven не требуется. Первый запуск требует интернета для зависимостей.
- Node.js нужен для первой установки PostgreSQL через Windows-скрипт и дополнительных тестов Service Worker.

Версия Spring Boot закреплена в `pom.xml`: **4.1.2**, обозначена стабильной в [официальной документации](https://docs.spring.io/spring-boot/).

## PostgreSQL через pgAdmin

1. Установите PostgreSQL и pgAdmin с [официального сайта](https://www.postgresql.org/download/windows/). Запомните пароль администратора `postgres`.
2. Подключитесь к локальному серверу в pgAdmin.
3. В **Login/Group Roles → Create → Login/Group Role** создайте `taskflow`. На вкладке **Definition** задайте свой пароль; на **Privileges** включите **Can login?**, не выдавая superuser.
4. В **Databases → Create → Database** создайте **`taskflow_db`**, owner **`taskflow`**, encoding **UTF8**.
5. Альтернатива в Query Tool (выполняйте команды отдельно, вне явного блока транзакции):

```sql
CREATE ROLE taskflow LOGIN PASSWORD 'replace-with-your-own-password';
CREATE DATABASE taskflow_db OWNER taskflow ENCODING 'UTF8';
```

Таблицы `app_users`, `categories`, `tasks` Hibernate создаст при первом запуске. Используется только `ddl-auto=update`, Flyway не подключён.

## PostgreSQL через Docker

Запустите Docker Desktop. В PowerShell из папки проекта:

```powershell
$env:DB_PASSWORD='your-own-local-password'
docker compose up -d db
```

Файл `compose.yaml` создаёт базу `taskflow_db` и роль `taskflow`, публикуя порт только на `127.0.0.1:5432`. Данные хранятся в именованном volume. Если локальный PostgreSQL уже занимает порт 5432, используйте его или поменяйте порт в compose и `DB_URL`.

`docker compose stop` останавливает базу с сохранением данных. **Не используйте `down -v`, если данные нужны:** этот параметр удаляет volume.

## Запуск в IntelliJ IDEA

1. **File → Open** → выберите папку проекта или `pom.xml`, откройте как Maven-проект.
2. В **Project Structure → Project SDK** выберите JDK 21. В **Settings → Build Tools → Maven → Runner → JRE** также выберите этот JDK. Дождитесь загрузки зависимостей Maven.
3. Откройте `src/main/java/com/taskflow/TaskflowApplication.java` и создайте Spring Boot/Application run configuration для `main`.
4. В **Run → Edit Configurations → Environment variables** задайте:

```text
DB_PASSWORD=your-own-local-password
DB_USERNAME=taskflow
DB_URL=jdbc:postgresql://localhost:5432/taskflow_db
APP_TIMEZONE=Asia/Baku
```

5. Запустите `TaskflowApplication`. Активные Spring profiles оставьте пустыми — профиль `test` предназначен только для автоматических тестов.
6. Откройте **[http://localhost:8080](http://localhost:8080)**, нажмите «Создать аккаунт» и зарегистрируйтесь.

Настройка имени базы и роли уже имеет значения по умолчанию; обязательна ваша переменная `DB_PASSWORD`. `.env.example` — только образец. Spring Boot **не читает `.env` автоматически**: укажите переменные в IntelliJ или терминале. Docker Compose читает свой `.env`, но это не передаёт переменные процессу Java в IntelliJ.

## Запуск в терминале

PowerShell:

```powershell
$env:JAVA_HOME='C:\path\to\jdk-21'
$env:DB_PASSWORD='your-own-local-password'
.\mvnw.cmd spring-boot:run
```

Сборка и запуск JAR:

```powershell
.\mvnw.cmd clean package
& "$env:JAVA_HOME\bin\java.exe" -jar target/taskflow-1.0.0.jar
```

Linux/macOS:

```sh
export DB_PASSWORD='your-own-local-password'
./mvnw spring-boot:run
```

Не помещайте действительные пароли в Git. `.env`, временные инструменты и логи исключены через `.gitignore`.

## Переменные конфигурации

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/taskflow_db` | JDBC URL |
| `DB_USERNAME` | `taskflow` | Роль PostgreSQL |
| `DB_PASSWORD` | нет | Пароль роли |
| `PORT` | `8080` | HTTP-порт |
| `APP_TIMEZONE` | `Asia/Baku` | Дата «сегодня», просрочка, границы недели |
| `COOKIE_SECURE` | `false` | Установите `true` при размещении по HTTPS |

За обратным прокси настройте доверенный прокси так, чтобы он перезаписывал входящие `Forwarded`/`X-Forwarded-*`, и запретите прямой доступ к backend. Приложение учитывает forwarded-заголовки. Для рабочего сервера задайте `COOKIE_SECURE=true`.

## PWA в браузере Android

**В браузере по Wi-Fi.** Запустите `start-taskflow.cmd`, подключите телефон к той же Wi-Fi сети и откройте напечатанный адрес `http://<IP-компьютера>:8080` в Chrome. Сначала проверьте, что `http://localhost:8080` открывается на ПК. Если страница открывается только на ПК, проверьте доступ Java к порту 8080 в брандмауэре для частной сети и убедитесь, что телефон подключён к основной сети Wi-Fi.

**Установка PWA через USB.** Подключите Android кабелем, включите отладку по USB и подтвердите подключение на телефоне. Если `adb` установлен вместе с Android Studio, выполните в PowerShell:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
& $adb reverse tcp:8080 tcp:8080
```

Откройте `http://localhost:8080` в Chrome на телефоне. В меню Chrome выберите **Добавить на главный экран → Установить**, когда браузер предложит установку. Через USB запросы идут на TaskFlow, запущенный на ПК. Вместо команды `adb reverse` можно использовать [перенаправление порта в Chrome DevTools](https://developer.chrome.com/docs/devtools/remote-debugging/local-server).

**Установка без USB.** Откройте TaskFlow по HTTPS с действительным сертификатом. Java-сервер можно разместить за HTTPS-прокси. Обычный HTTP по IP в Wi-Fi подходит для работы в браузере, а Service Worker требует безопасного контекста. [Требования браузера к установке PWA](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Tutorials/CycleTracker/Secure_connection)

## Как работает PWA

Service Worker кеширует только явный список публичных CSS, JS, шрифтов, иконок, manifest и `offline.html`. HTML аккаунта, CSRF-токены, задачи, профиль и POST-запросы не попадают в Cache Storage. При отсутствии сети навигация открывает общий экран подключения; изменения задач доступны после восстановления сети.

При изменении статических файлов увеличьте номер `CACHE` в `src/main/resources/static/sw.js`. Новый worker загружает публичные ресурсы с обходом старого HTTP-кеша и удаляет предыдущие кеши TaskFlow. После обновления уже открытого приложения обновите страницу.

## Тесты

```powershell
.\mvnw.cmd test
node --test src/test/js/service-worker.test.cjs
```

Java-тесты запускаются на изолированной **H2 в режиме PostgreSQL** и не требуют работающей основной базы. Тестовый H2-драйвер не включается в production JAR. Проверяется настоящий Spring-контекст: MVC, Security, JPA и рендеринг Thymeleaf.

Для прогона того же набора на настоящем PostgreSQL создайте **отдельную пустую тестовую базу** и задайте `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` перед запуском `test`. **Никогда не направляйте тесты на рабочую базу:** тестовый профиль использует `create-drop`.

Подробный отчёт о фактически выполненных проверках и ограничениях проверки — в [TESTING.md](TESTING.md).

## Архитектура

`Controller → Service → Repository → PostgreSQL`. Сервисы содержат бизнес-операции и проверку владельца; DTO — валидацию форм; сущности не привязываются напрямую к HTTP-параметрам. Общие данные навигации собираются в `CurrentUserAdvice`. `completedAt` хранит время текущего завершения задачи для недельного графика; при возврате в активные сбрасывается.

## Известные ограничения

- Полноценная офлайн-работа с задачами и синхронизация не реализованы: офлайн доступен только экран подключения.
- Напоминания работают внутри открытого приложения; фоновых push и email-уведомлений нет.
- Адрес email нельзя сменить в интерфейсе. Имя и пароль редактируются.
- Тема сохраняется в конкретном браузере, а не синхронизируется между устройствами.
- Часовой пояс задаётся для сервера, а не отдельно для каждого пользователя.
- Недельный график показывает текущие выполненные задачи по `completedAt`, а не неизменяемую историю всех действий.
- Используется Hibernate `ddl-auto=update` для локальной разработки. Перед серьёзным развёртыванием подготовьте резервные копии, управляемые миграции и операционный мониторинг.
- Списки предназначены для личного планировщика и загружаются целиком; серверной пагинации пока нет.

## Лицензии ресурсов

Inter распространяется по SIL Open Font License, Lucide — по ISC. Тексты лицензий находятся рядом с локальными файлами в `static/fonts/INTER-LICENSE` и `static/js/vendor/LUCIDE-LICENSE`. Логотип TaskFlow создан в SVG внутри проекта.

## Полная структура исходников

См. [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md). Каталоги `target` и `.tools` — результаты сборки и временной проверки, не исходники приложения.
