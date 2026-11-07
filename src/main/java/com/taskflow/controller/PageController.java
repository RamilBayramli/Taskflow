package com.taskflow.controller;

import com.taskflow.dto.CategoryForm;
import com.taskflow.dto.PasswordForm;
import com.taskflow.dto.ProfileForm;
import com.taskflow.entity.Priority;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.service.TaskService;
import com.taskflow.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PageController {
    private final UserService userService;
    private final TaskService taskService;
    private final Clock clock;

    public PageController(UserService userService, TaskService taskService, Clock clock) {
        this.userService = userService;
        this.taskService = taskService;
        this.clock = clock;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    String dashboard(Authentication auth, Model model) {
        User user = userService.byEmail(auth.getName());
        List<Task> userTasks = taskService.all(user.getId());
        YearMonth month = YearMonth.now(clock);

        model.addAttribute("stats", taskService.dashboard(userTasks));
        model.addAttribute("calendarDays", taskService.calendar(month, userTasks));
        model.addAttribute("month", month);
        model.addAttribute("pageTitle", "Сегодня");
        return "dashboard";
    }

    @GetMapping("/tasks")
    String tasks(
        Authentication auth,
        Model model,
        @RequestParam(name = "q", defaultValue = "") String query,
        @RequestParam(defaultValue = "all") String status,
        @RequestParam(required = false) Long category,
        @RequestParam(required = false) Priority priority,
        @RequestParam(defaultValue = "due") String sort
    ) {
        User user = userService.byEmail(auth.getName());
        List<Task> matchingTasks = taskService.search(user.getId(), query, status, category, priority, sort);

        model.addAttribute("tasks", matchingTasks);
        model.addAttribute("q", query);
        model.addAttribute("status", status);
        model.addAttribute("categoryFilter", category);
        model.addAttribute("priorityFilter", priority);
        model.addAttribute("sort", sort);
        model.addAttribute("pageTitle", "Мои задачи");
        return "tasks";
    }

    @GetMapping("/calendar")
    String calendar(
        Authentication auth,
        Model model,
        @RequestParam(required = false) String month,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day
    ) {
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth selectedMonth = month == null ? currentMonth : YearMonth.parse(month);
        if (selectedMonth.getYear() < 1900 || selectedMonth.getYear() > 2200) {
            throw new IllegalArgumentException("Год вне диапазона календаря");
        }

        LocalDate defaultDay = selectedMonth.equals(currentMonth) ? today : selectedMonth.atDay(1);
        LocalDate selectedDay = day == null ? defaultDay : day;
        User user = userService.byEmail(auth.getName());
        List<Task> userTasks = taskService.all(user.getId());
        List<Task> tasksForDay = userTasks.stream()
            .filter(task -> selectedDay.equals(task.getDueDate()))
            .toList();

        model.addAttribute("month", selectedMonth);
        model.addAttribute("previousMonth", selectedMonth.minusMonths(1));
        model.addAttribute("nextMonth", selectedMonth.plusMonths(1));
        model.addAttribute("calendarDays", taskService.calendar(selectedMonth, userTasks));
        model.addAttribute("selectedDay", selectedDay);
        model.addAttribute("tasks", tasksForDay);
        model.addAttribute("pageTitle", "Календарь");
        return "calendar";
    }

    @GetMapping("/categories")
    String categories(Authentication auth, Model model) {
        User user = userService.byEmail(auth.getName());
        Map<Long, Long> taskCounts = taskService.all(user.getId()).stream()
            .filter(task -> task.getCategory() != null)
            .collect(Collectors.groupingBy(task -> task.getCategory().getId(), Collectors.counting()));

        model.addAttribute("categoryForm", new CategoryForm());
        model.addAttribute("categoryCounts", taskCounts);
        model.addAttribute("pageTitle", "Категории");
        return "categories";
    }

    @GetMapping("/settings")
    String settings(HttpServletRequest request, Model model) {
        String userAgent = request.getHeader("User-Agent");
        model.addAttribute("androidApp", userAgent != null && userAgent.contains("TaskFlowAndroid/"));
        model.addAttribute("pageTitle", "Настройки");
        return "settings";
    }

    @GetMapping("/profile")
    String profile(Authentication auth, Model model) {
        model.addAttribute("profileForm", profileForm(auth.getName()));
        model.addAttribute("passwordForm", new PasswordForm());
        model.addAttribute("pageTitle", "Профиль");
        return "profile";
    }

    @PostMapping("/profile")
    String updateProfile(
        Authentication auth,
        @Valid @ModelAttribute ProfileForm profileForm,
        BindingResult errors,
        Model model,
        RedirectAttributes redirect
    ) {
        if (errors.hasErrors()) {
            model.addAttribute("passwordForm", new PasswordForm());
            model.addAttribute("pageTitle", "Профиль");
            return "profile";
        }

        userService.updateName(auth.getName(), profileForm.getUsername());
        redirect.addFlashAttribute("success", "Имя обновлено");
        return "redirect:/profile";
    }

    @PostMapping("/profile/password")
    String password(
        Authentication auth,
        @Valid @ModelAttribute PasswordForm passwordForm,
        BindingResult errors,
        Model model,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String email = auth.getName();
        if (!errors.hasErrors()) {
            boolean passwordChanged = userService.changePassword(
                email,
                passwordForm.getCurrentPassword(),
                passwordForm.getNewPassword()
            );
            if (passwordChanged) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                return "redirect:/login?passwordChanged";
            }
            errors.rejectValue("currentPassword", "incorrect", "Неверный текущий пароль");
        }

        model.addAttribute("profileForm", profileForm(email));
        model.addAttribute("pageTitle", "Профиль");
        return "profile";
    }

    private ProfileForm profileForm(String email) {
        ProfileForm form = new ProfileForm();
        form.setUsername(userService.byEmail(email).getUsername());
        return form;
    }
}
