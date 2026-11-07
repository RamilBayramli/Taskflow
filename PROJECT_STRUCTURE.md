# Полная структура проекта

```text
Taskflow/
├── .mvn/
│   └── wrapper/
│       └── maven-wrapper.properties
├── android/                       # Клиент и сборка APK
│   ├── app/
│   ├── gradle/
│   ├── build-apk.ps1
│   └── README.md
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── taskflow/
│   │   │           ├── config/
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   └── TimeConfig.java
│   │   │           ├── controller/
│   │   │           │   ├── AuthController.java
│   │   │           │   ├── CategoryController.java
│   │   │           │   ├── CurrentUserAdvice.java
│   │   │           │   ├── PageController.java
│   │   │           │   └── TaskController.java
│   │   │           ├── dto/
│   │   │           │   ├── CategoryForm.java
│   │   │           │   ├── PasswordForm.java
│   │   │           │   ├── ProfileForm.java
│   │   │           │   ├── RegisterForm.java
│   │   │           │   └── TaskForm.java
│   │   │           ├── entity/
│   │   │           │   ├── Category.java
│   │   │           │   ├── Priority.java
│   │   │           │   ├── Task.java
│   │   │           │   └── User.java
│   │   │           ├── exception/
│   │   │           │   ├── DuplicateValueException.java
│   │   │           │   ├── FormExceptionHandler.java
│   │   │           │   └── ResourceNotFoundException.java
│   │   │           ├── repository/
│   │   │           │   ├── CategoryRepository.java
│   │   │           │   ├── TaskRepository.java
│   │   │           │   └── UserRepository.java
│   │   │           ├── service/
│   │   │           │   ├── CategoryService.java
│   │   │           │   ├── TaskService.java
│   │   │           │   └── UserService.java
│   │   │           └── TaskflowApplication.java
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/
│   │       │   │   ├── app.css
│   │       │   │   ├── focus.css
│   │       │   │   └── planner.css
│   │       │   ├── fonts/
│   │       │   │   ├── inter-cyrillic.woff2
│   │       │   │   ├── inter-latin.woff2
│   │       │   │   └── INTER-LICENSE
│   │       │   ├── icons/
│   │       │   │   ├── icon-192.png
│   │       │   │   ├── icon-512.png
│   │       │   │   ├── icon-maskable-512.png
│   │       │   │   └── logo.svg
│   │       │   ├── js/
│   │       │   │   ├── vendor/
│   │       │   │   │   ├── LUCIDE-LICENSE
│   │       │   │   │   └── lucide.min.js
│   │       │   │   ├── app.js
│   │       │   │   ├── focus.js
│   │       │   │   └── theme.js
│   │       │   ├── manifest.webmanifest
│   │       │   ├── offline.html
│   │       │   └── sw.js
│   │       ├── templates/
│   │       │   ├── error/
│   │       │   │   ├── 400.html
│   │       │   │   └── 404.html
│   │       │   ├── fragments/
│   │       │   │   ├── auth.html
│   │       │   │   ├── focus.html
│   │       │   │   ├── layout.html
│   │       │   │   └── task-list.html
│   │       │   ├── calendar.html
│   │       │   ├── categories.html
│   │       │   ├── dashboard.html
│   │       │   ├── error.html
│   │       │   ├── login.html
│   │       │   ├── profile.html
│   │       │   ├── register.html
│   │       │   ├── settings.html
│   │       │   ├── task-form.html
│   │       │   └── tasks.html
│   │       └── application.properties
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── taskflow/
│       │           └── TaskflowIntegrationTest.java
│       ├── js/
│       │   └── service-worker.test.cjs
│       └── resources/
│           └── application-test.properties
├── .env.example
├── .gitattributes
├── .gitignore
├── compose.yaml
├── HELP.md
├── mvnw
├── mvnw.cmd
├── pom.xml
├── PROJECT_STRUCTURE.md
├── README.md
├── start-taskflow.cmd
├── start-taskflow.ps1
└── TESTING.md
```

Сборка находится в `target/taskflow-1.0.0.jar`. Отчёты тестов хранятся в `target/surefire-reports/`. Windows-скрипт сохраняет локальную PostgreSQL и её данные в `.taskflow/`.
