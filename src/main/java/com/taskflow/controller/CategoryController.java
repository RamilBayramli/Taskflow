package com.taskflow.controller;

import com.taskflow.dto.CategoryForm;
import com.taskflow.entity.User;
import com.taskflow.exception.DuplicateValueException;
import com.taskflow.service.CategoryService;
import com.taskflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CategoryController {
    private final CategoryService categoryService;
    private final UserService userService;

    public CategoryController(CategoryService categoryService, UserService userService) {
        this.categoryService = categoryService;
        this.userService = userService;
    }

    @PostMapping({"/categories", "/categories/{id}"})
    String save(
        @PathVariable(required = false) Long id,
        Authentication auth,
        @Valid @ModelAttribute CategoryForm form,
        BindingResult errors,
        RedirectAttributes redirect
    ) {
        User user = userService.byEmail(auth.getName());
        if (errors.hasErrors()) {
            if (id != null) {
                categoryService.owned(id, user.getId());
            }
            redirect.addFlashAttribute("error", errors.getAllErrors().getFirst().getDefaultMessage());
            return "redirect:/categories";
        }

        try {
            categoryService.save(id, user, form);
            redirect.addFlashAttribute("success", "Категория сохранена");
        } catch (DuplicateValueException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "Категория с таким названием уже есть");
        }
        return "redirect:/categories";
    }

    @PostMapping("/categories/{id}/delete")
    String delete(@PathVariable Long id, Authentication auth, RedirectAttributes redirect) {
        User user = userService.byEmail(auth.getName());
        categoryService.delete(id, user.getId());
        redirect.addFlashAttribute("success", "Категория удалена. Задачи сохранены без категории.");
        return "redirect:/categories";
    }
}
