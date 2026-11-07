package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CategoryForm {
    @NotBlank(message = "Укажите название категории")
    @Size(max = 40, message = "До 40 символов")
    private String name;

    @NotNull
    @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "Выберите цвет")
    private String color = "#5558E8";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
