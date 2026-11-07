package com.taskflow.service;

import com.taskflow.dto.CategoryForm;
import com.taskflow.entity.Category;
import com.taskflow.entity.User;
import com.taskflow.exception.DuplicateValueException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.CategoryRepository;
import com.taskflow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;

    public CategoryService(CategoryRepository categoryRepository, TaskRepository taskRepository) {
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId) {
        return categoryRepository.findByUserIdOrderByNameAsc(userId);
    }

    @Transactional(readOnly = true)
    public Category owned(Long id, Long userId) {
        return categoryRepository.findByIdAndUserId(id, userId)
            .orElseThrow(ResourceNotFoundException::new);
    }

    public void createDefaults(User user) {
        createDefault(user, "Учёба", "#D99A3D");
        createDefault(user, "Программирование", "#5558E8");
        createDefault(user, "Личное", "#47A589");
        createDefault(user, "Другое", "#8B91A2");
    }

    public void save(Long id, User user, CategoryForm form) {
        Category category = id == null ? new Category() : owned(id, user.getId());
        String name = form.getName().strip();
        long ignoredCategoryId = id == null ? -1L : id;
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(user.getId(), name, ignoredCategoryId)) {
            throw new DuplicateValueException("Категория с таким названием уже есть");
        }

        category.setName(name);
        category.setColor(form.getColor());
        category.setUser(user);
        categoryRepository.saveAndFlush(category);
    }

    public void delete(Long id, Long userId) {
        Category category = owned(id, userId);
        taskRepository.findByCategoryIdAndUserId(id, userId).forEach(task -> task.setCategory(null));
        taskRepository.flush();
        categoryRepository.delete(category);
    }

    private void createDefault(User user, String name, String color) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setColor(color);
        categoryRepository.save(category);
    }
}
