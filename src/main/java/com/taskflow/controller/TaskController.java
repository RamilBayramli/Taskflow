package com.taskflow.controller;

import com.taskflow.dto.TaskForm;
import com.taskflow.entity.Task;
import com.taskflow.entity.User;
import com.taskflow.service.TaskService;
import com.taskflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaskController {
    private final TaskService taskService;
    private final UserService userService;

    public TaskController(TaskService taskService, UserService userService) {
        this.taskService = taskService;
        this.userService = userService;
    }

    @GetMapping("/tasks/new")
    String create(Model model) {
        model.addAttribute("taskForm", new TaskForm());
        model.addAttribute("pageTitle", "Новая задача");
        return "task-form";
    }

    @GetMapping("/tasks/{id}/edit")
    String edit(@PathVariable Long id, Authentication auth, Model model) {
        User user = userService.byEmail(auth.getName());
        Task task = taskService.owned(id, user.getId());
        model.addAttribute("taskForm", TaskForm.from(task));
        model.addAttribute("taskId", id);
        model.addAttribute("pageTitle", "Редактировать задачу");
        return "task-form";
    }

    @PostMapping({"/tasks", "/tasks/{id}"})
    String save(
        @PathVariable(required = false) Long id,
        @Valid @ModelAttribute TaskForm taskForm,
        BindingResult errors,
        Authentication auth,
        Model model,
        RedirectAttributes redirect
    ) {
        User user = userService.byEmail(auth.getName());
        if (errors.hasErrors()) {
            if (id != null) {
                taskService.owned(id, user.getId());
            }
            model.addAttribute("taskId", id);
            model.addAttribute("pageTitle", id == null ? "Новая задача" : "Редактировать задачу");
            return "task-form";
        }

        taskService.save(id, user, taskForm);
        redirect.addFlashAttribute("success", id == null ? "Задача создана" : "Изменения сохранены");
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/{id}/toggle")
    String toggle(@PathVariable Long id, Authentication auth) {
        User user = userService.byEmail(auth.getName());
        taskService.toggle(id, user.getId());
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/{id}/delete")
    String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        User user = userService.byEmail(auth.getName());
        taskService.delete(id, user.getId());
        redirect.addFlashAttribute("success", "Задача удалена");
        return "redirect:/tasks";
    }
}
