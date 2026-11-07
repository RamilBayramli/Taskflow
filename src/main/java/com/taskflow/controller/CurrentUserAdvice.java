package com.taskflow.controller;

import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.service.CategoryService;
import com.taskflow.service.TaskService;
import com.taskflow.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@ControllerAdvice(assignableTypes = {PageController.class, TaskController.class})
public class CurrentUserAdvice {
    private final UserService userService;
    private final TaskService taskService;
    private final CategoryService categoryService;
    private final Clock clock;

    public CurrentUserAdvice(
        UserService userService,
        TaskService taskService,
        CategoryService categoryService,
        Clock clock
    ) {
        this.userService = userService;
        this.taskService = taskService;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    @ModelAttribute
    void currentUser(Authentication auth, Model model, HttpServletRequest request) {
        if (auth == null) {
            return;
        }

        User user = userService.byEmail(auth.getName());
        List<Task> userTasks = taskService.all(user.getId());
        LocalDate today = LocalDate.now(clock);
        List<Task> reminders = userTasks.stream()
            .filter(task -> !task.isCompleted())
            .filter(task -> task.getDueDate() != null && !task.getDueDate().isAfter(today))
            .sorted(Comparator.comparing(Task::getDueDate))
            .toList();

        model.addAttribute("currentUser", user);
        model.addAttribute("userCategories", categoryService.list(user.getId()));
        model.addAttribute("today", today);
        model.addAttribute("appZone", clock.getZone());
        model.addAttribute("path", request.getRequestURI());
        model.addAttribute("activeCount", userTasks.stream().filter(task -> !task.isCompleted()).count());
        model.addAttribute("reminders", reminders);
    }
}
