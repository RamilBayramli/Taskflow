package com.taskflow;

import com.taskflow.dto.*;
import com.taskflow.entity.*;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.*;
import com.taskflow.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.mail.host=localhost",
    "spring.mail.username=test@example.test",
    "spring.mail.password=test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired TaskService tasks;
    @Autowired CategoryService categories;
    @Autowired UserRepository userRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired Clock clock;
    @MockitoBean JavaMailSender mailSender;
    @MockitoBean(name = "accountMailExecutor") TaskExecutor accountMailExecutor;
    User alice;
    User bob;

    @BeforeEach
    void setup() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(accountMailExecutor).execute(any(Runnable.class));
        alice = register("alice@example.com", "Алиса");
        bob = register("bob@example.com", "Борис");
    }

    User register(String email, String name) {
        RegisterForm form = new RegisterForm(); form.setUsername(name); form.setEmail(email);
        form.setPassword("StrongPassword42"); form.setConfirmPassword("StrongPassword42");
        User user = users.register(form);
        user.setEmailVerified(true);
        return user;
    }

    Task task(User owner, String title, LocalDate due, Priority priority) {
        TaskForm form = new TaskForm(); form.setTitle(title); form.setDueDate(due); form.setPriority(priority);
        form.setCategoryId(categories.list(owner.getId()).getFirst().getId());
        return tasks.save(null, owner, form);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndCreatesPrivateDefaults() {
        User user = register("  CHARLIE@EXAMPLE.COM  ", "Чарли");
        assertThat(user.getEmail()).isEqualTo("charlie@example.com");
        assertThat(user.getPassword()).isNotEqualTo("StrongPassword42");
        assertThat(encoder.matches("StrongPassword42", user.getPassword())).isTrue();
        assertThat(categories.list(user.getId())).extracting(Category::getName)
            .containsExactlyInAnyOrder("Учёба", "Программирование", "Личное", "Другое");
    }

    @Test
    void registrationRejectsDuplicatesAndInvalidForms() throws Exception {
        mvc.perform(post("/register").with(csrf()).param("username", "Ещё Алиса").param("email", "ALICE@example.com")
            .param("password", "StrongPassword42").param("confirmPassword", "StrongPassword42"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("registerForm", "email"));
        mvc.perform(post("/register").with(csrf()).param("username", " ").param("email", "bad")
            .param("password", "123").param("confirmPassword", "456"))
            .andExpect(model().hasErrors());
    }

    @Test
    void registrationRequiresTheCodeFromEmailBeforeLogin() throws Exception {
        String email = "dasha@example.com";
        mvc.perform(post("/register").with(csrf()).param("username", "Даша").param("email", email)
            .param("password", "StrongPassword42").param("confirmPassword", "StrongPassword42"))
            .andExpect(redirectedUrl("/confirm-email"));
        assertThat(userRepository.findByEmail(email).orElseThrow().requiresEmailConfirmation()).isTrue();
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", "StrongPassword42"))
            .andExpect(redirectedUrl("/login?error"));

        String code = sentCode(email);
        mvc.perform(post("/confirm-email").with(csrf()).param("email", email).param("code", code))
            .andExpect(redirectedUrl("/login?emailConfirmed"));
        mvc.perform(post("/confirm-email").with(csrf()).param("email", email).param("code", code))
            .andExpect(model().attributeHasFieldErrors("codeForm", "code"));
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", "StrongPassword42"))
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void forgottenPasswordUsesASingleUseCodeAndANeutralRequestResponse() throws Exception {
        Object unknownAddressNotice = mvc.perform(post("/forgot-password").with(csrf())
                .param("email", "unknown@example.test"))
            .andExpect(redirectedUrl("/reset-password"))
            .andReturn().getFlashMap().get("notice");
        verifyNoInteractions(mailSender);

        mvc.perform(post("/forgot-password").with(csrf()).param("email", alice.getEmail()))
            .andExpect(redirectedUrl("/reset-password"))
            .andExpect(flash().attribute("notice", unknownAddressNotice));
        String code = sentCode(alice.getEmail());
        mvc.perform(post("/reset-password").with(csrf()).param("email", alice.getEmail()).param("code", code)
                .param("newPassword", "RecoveredPassword42").param("confirmPassword", "RecoveredPassword42"))
            .andExpect(redirectedUrl("/login?passwordChanged"));
        mvc.perform(post("/reset-password").with(csrf()).param("email", alice.getEmail()).param("code", code)
                .param("newPassword", "DifferentPassword42").param("confirmPassword", "DifferentPassword42"))
            .andExpect(model().attributeHasFieldErrors("codeForm", "code"));
        mvc.perform(post("/login").with(csrf()).param("email", alice.getEmail()).param("password", "StrongPassword42"))
            .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("email", alice.getEmail()).param("password", "RecoveredPassword42"))
            .andExpect(redirectedUrl("/dashboard"));
    }

    private String sentCode(String email) {
        var mail = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly(email);
        var code = Pattern.compile("\\b[0-9]{8}\\b").matcher(mail.getValue().getText());
        assertThat(code.find()).isTrue();
        return code.group();
    }

    @Test
    void sessionLoginRejectsBadPasswordAndLogoutRequiresCsrf() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("email", "ALICE@example.com").param("password", "StrongPassword42"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(post("/login").with(csrf()).param("email", alice.getEmail()).param("password", "wrong"))
            .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/logout").with(user(alice.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user(alice.getEmail())).with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void privatePagesRequireLoginAndWritesRequireCsrf() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/tasks").with(user(alice.getEmail())).param("title", "Unsafe")).andExpect(status().isForbidden());
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/register")).andExpect(status().isOk());
        mvc.perform(get("/confirm-email")).andExpect(status().isOk());
        mvc.perform(get("/forgot-password")).andExpect(status().isOk());
        mvc.perform(get("/reset-password")).andExpect(status().isOk());
        mvc.perform(post("/forgot-password").param("email", alice.getEmail())).andExpect(status().isForbidden());
    }

    @Test
    void allPagesRenderWithRealData() throws Exception {
        Task task = task(alice, "Проверить интерфейс", LocalDate.now(clock), Priority.HIGH);
        task(bob, "BOB_PRIVATE_TASK", LocalDate.now(clock), Priority.LOW);
        for (String page : List.of("/dashboard", "/tasks", "/tasks/new", "/tasks/" + task.getId() + "/edit", "/calendar", "/categories", "/settings", "/profile")) {
            mvc.perform(get(page).with(user(alice.getEmail())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("BOB_PRIVATE_TASK"))));
        }
    }

    @Test
    void androidSettingsKeepThemeAndAccountWithoutTheWebInstallPrompt() throws Exception {
        mvc.perform(get("/settings").with(user(alice.getEmail())).header("User-Agent", "TaskFlowAndroid/1.0"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Внешний вид")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Выйти из аккаунта")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("install-app"))));
    }

    @Test
    void taskCrudAndCompletionPersistThroughControllers() throws Exception {
        Long category = categories.list(alice.getId()).getFirst().getId();
        mvc.perform(post("/tasks").with(user(alice.getEmail())).with(csrf()).param("title", "Первая задача")
            .param("description", "Детали").param("priority", "HIGH").param("dueDate", "2026-11-06").param("categoryId", category.toString()))
            .andExpect(redirectedUrl("/tasks"));
        Task saved = tasks.all(alice.getId()).getFirst();
        assertThat(saved.getDescription()).isEqualTo("Детали");
        mvc.perform(post("/tasks/{id}", saved.getId()).with(user(alice.getEmail())).with(csrf())
            .param("title", "Обновлено").param("priority", "LOW"))
            .andExpect(redirectedUrl("/tasks"));
        assertThat(tasks.owned(saved.getId(), alice.getId()).getTitle()).isEqualTo("Обновлено");
        mvc.perform(post("/tasks/{id}/toggle", saved.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(tasks.owned(saved.getId(), alice.getId()).isCompleted()).isTrue();
        assertThat(tasks.owned(saved.getId(), alice.getId()).getCompletedAt()).isNotNull();
        tasks.toggle(saved.getId(), alice.getId());
        assertThat(saved.isCompleted()).isFalse(); assertThat(saved.getCompletedAt()).isNull();
        mvc.perform(post("/tasks/{id}/delete", saved.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(redirectedUrl("/tasks"));
        assertThat(tasks.all(alice.getId())).isEmpty();
    }

    @Test
    void invalidTaskShowsValidationAndDoesNotPersist() throws Exception {
        mvc.perform(post("/tasks").with(user(alice.getEmail())).with(csrf()).param("title", " ").param("priority", "MEDIUM"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("taskForm", "title"));
        assertThat(tasks.all(alice.getId())).isEmpty();
    }

    @Test
    void usersCannotReadEditToggleDeleteOrAssignOtherUsersResources() throws Exception {
        Task foreign = task(bob, "Секрет", LocalDate.now(clock), Priority.HIGH);
        Long category = categories.list(bob.getId()).getFirst().getId();
        mvc.perform(get("/tasks/{id}/edit", foreign.getId()).with(user(alice.getEmail()))).andExpect(status().isNotFound());
        for (String action : List.of("/toggle", "/delete", "")) {
            mvc.perform(post("/tasks/" + foreign.getId() + action).with(user(alice.getEmail())).with(csrf())
                .param("title", "Взлом").param("priority", "MEDIUM")).andExpect(status().isNotFound());
        }
        mvc.perform(post("/tasks").with(user(alice.getEmail())).with(csrf()).param("title", "Задача")
            .param("priority", "MEDIUM").param("categoryId", category.toString())).andExpect(status().isNotFound());
        mvc.perform(post("/categories/" + category).with(user(alice.getEmail())).with(csrf()).param("name", "Взлом").param("color", "#123456")).andExpect(status().isNotFound());
        mvc.perform(post("/categories/" + category + "/delete").with(user(alice.getEmail())).with(csrf())).andExpect(status().isNotFound());
        assertThatThrownBy(() -> tasks.owned(foreign.getId(), alice.getId())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(tasks.owned(foreign.getId(), bob.getId()).getTitle()).isEqualTo("Секрет");
    }

    @Test
    void searchFiltersAndSortAreScopedAndLiteral() {
        LocalDate today = LocalDate.now(clock);
        Task first = task(alice, "Java 100%", today.minusDays(1), Priority.HIGH);
        Task second = task(alice, "SQL", today.plusDays(1), Priority.LOW);
        task(alice, "Java streams", today, Priority.MEDIUM);
        task(bob, "Java secret", today, Priority.HIGH);
        tasks.toggle(second.getId(), alice.getId());
        assertThat(tasks.search(alice.getId(), "java", "all", null, null, "due")).hasSize(2);
        assertThat(tasks.search(alice.getId(), "%", "all", null, null, "due")).containsExactly(first);
        assertThat(tasks.search(alice.getId(), "", "overdue", null, null, "due")).containsExactly(first);
        assertThat(tasks.search(alice.getId(), "", "completed", null, null, "due")).containsExactly(second);
        assertThat(tasks.search(alice.getId(), "", "today", null, null, "due")).hasSize(1);
        assertThat(tasks.search(alice.getId(), "", "active", null, Priority.HIGH, "due")).containsExactly(first);
        assertThat(tasks.search(alice.getId(), "", "all", first.getCategory().getId(), null, "priority").getFirst()).isEqualTo(first);
        assertThat(tasks.search(alice.getId(), "", "all", null, null, "title").getFirst().getTitle()).isEqualTo("Java 100%");
        assertThat(tasks.search(alice.getId(), "", "all", categories.list(bob.getId()).getFirst().getId(), null, "due")).isEmpty();
    }

    @Test
    void categoryCrudPreservesTasksOnDeletion() throws Exception {
        mvc.perform(post("/categories").with(user(alice.getEmail())).with(csrf()).param("name", "Работа").param("color", "#123456"))
            .andExpect(redirectedUrl("/categories"));
        Category category = categories.list(alice.getId()).stream().filter(c -> c.getName().equals("Работа")).findFirst().orElseThrow();
        mvc.perform(post("/categories/{id}", category.getId()).with(user(alice.getEmail())).with(csrf()).param("name", "Проект").param("color", "#654321"))
            .andExpect(redirectedUrl("/categories"));
        assertThat(categories.owned(category.getId(), alice.getId()).getName()).isEqualTo("Проект");
        Task task = task(alice, "Сохранить задачу", null, Priority.MEDIUM); task.setCategory(category); taskRepository.flush();
        mvc.perform(post("/categories/{id}/delete", category.getId()).with(user(alice.getEmail())).with(csrf())).andExpect(redirectedUrl("/categories"));
        assertThat(tasks.owned(task.getId(), alice.getId()).getCategory()).isNull();
        assertThat(tasks.all(alice.getId())).hasSize(1);
    }

    @Test
    void statisticsAndCalendarComeFromTasks() {
        LocalDate today = LocalDate.now(clock);
        Task completed = task(alice, "Готово", today, Priority.MEDIUM);
        tasks.toggle(completed.getId(), alice.getId());
        task(alice, "Просрочено", today.minusDays(1), Priority.HIGH);
        task(alice, "Сегодня", today, Priority.LOW);
        task(bob, "Чужое", today, Priority.HIGH);
        TaskService.Dashboard stats = tasks.dashboard(tasks.all(alice.getId()));
        assertThat(stats.total()).isEqualTo(3); assertThat(stats.active()).isEqualTo(2);
        assertThat(stats.completed()).isEqualTo(1); assertThat(stats.overdue()).isEqualTo(1);
        assertThat(stats.percent()).isEqualTo(33); assertThat(stats.dailyPercent()).isEqualTo(50);
        assertThat(stats.todayTasks()).extracting(Task::getTitle).containsExactly("Сегодня", "Готово");
        assertThat(stats.activeTasks()).extracting(Task::getTitle).containsExactly("Просрочено", "Сегодня");
        assertThat(stats.otherActive()).extracting(Task::getTitle).containsExactly("Просрочено");
        assertThat(stats.weekCompleted()).isEqualTo(1);
        List<TaskService.CalendarDay> days = tasks.calendar(YearMonth.from(today), tasks.all(alice.getId()));
        assertThat(days).hasSize(42);
        assertThat(days.getFirst().date().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(days.stream().filter(d -> d.date().equals(today)).findFirst().orElseThrow().count()).isEqualTo(2);
        assertThat(tasks.dashboard(List.of()).percent()).isZero();
    }

    @Test
    void profileValidatesCurrentPasswordAndUpdatesName() throws Exception {
        mvc.perform(post("/profile").with(user(alice.getEmail())).with(csrf()).param("username", "Новое имя"))
            .andExpect(redirectedUrl("/profile"));
        assertThat(users.byEmail(alice.getEmail()).getUsername()).isEqualTo("Новое имя");
        mvc.perform(post("/profile/password").with(user(alice.getEmail())).with(csrf()).param("currentPassword", "wrong")
            .param("newPassword", "NewStrongPassword42").param("confirmPassword", "NewStrongPassword42"))
            .andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword"));
        mvc.perform(post("/profile/password").with(user(alice.getEmail())).with(csrf()).param("currentPassword", "StrongPassword42")
            .param("newPassword", "NewStrongPassword42").param("confirmPassword", "NewStrongPassword42"))
            .andExpect(redirectedUrl("/login?passwordChanged"));
        assertThat(encoder.matches("NewStrongPassword42", users.byEmail(alice.getEmail()).getPassword())).isTrue();
    }

    @Test
    void maliciousTextIsEscapedAndBadDatesAreHandled() throws Exception {
        task(alice, "<script>alert('x')</script>", null, Priority.MEDIUM);
        mvc.perform(get("/tasks").with(user(alice.getEmail())))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        mvc.perform(get("/calendar?month=not-a-month").with(user(alice.getEmail()))).andExpect(status().isBadRequest());
        mvc.perform(get("/tasks?priority=INVALID").with(user(alice.getEmail()))).andExpect(status().isBadRequest());
        mvc.perform(get("/calendar?day=not-a-date").with(user(alice.getEmail()))).andExpect(status().isBadRequest());
    }

    @Test
    void pwaResourcesArePublicAndPrivateResponsesAreNotCacheable() throws Exception {
        mvc.perform(get("/dashboard").with(user(alice.getEmail()))).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        for (String asset : List.of("/css/planner.css", "/css/focus.css", "/js/focus.js")) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
        for (String asset : List.of("/manifest.webmanifest", "/sw.js", "/offline.html", "/css/app.css", "/js/app.js", "/js/theme.js", "/js/vendor/lucide.min.js", "/fonts/inter-latin.woff2", "/fonts/inter-cyrillic.woff2", "/icons/icon-192.png", "/icons/icon-512.png", "/icons/icon-maskable-512.png")) {
            mvc.perform(get(asset)).andExpect(status().isOk());
        }
        mvc.perform(get("/manifest.webmanifest"))
            .andExpect(jsonPath("$.display").value("standalone"))
            .andExpect(jsonPath("$.scope").value("/"))
            .andExpect(jsonPath("$.icons[0].sizes").value("192x192"))
            .andExpect(jsonPath("$.icons[1].sizes").value("512x512"));
        mvc.perform(get("/sw.js")).andExpect(header().string("Cache-Control", "no-cache"));
    }
}
