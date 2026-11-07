package com.taskflow.dto;

import com.taskflow.entity.Priority;
import com.taskflow.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class TaskForm {
    @NotBlank(message = "Назовите задачу")
    @Size(max = 160, message = "Название — до 160 символов")
    private String title;

    @Size(max = 4000, message = "Описание — до 4000 символов")
    private String description;

    @NotNull(message = "Выберите приоритет")
    private Priority priority = Priority.MEDIUM;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    private Long categoryId;

    public static TaskForm from(Task task) {
        TaskForm form = new TaskForm();
        form.title = task.getTitle();
        form.description = task.getDescription();
        form.priority = task.getPriority();
        form.dueDate = task.getDueDate();
        var category = task.getCategory();
        form.categoryId = category == null ? null : category.getId();
        return form;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
