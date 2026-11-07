package com.taskflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailForm {
    @NotBlank(message = "Укажите email")
    @Email(message = "Проверьте адрес email")
    @Size(max = 254, message = "Адрес email слишком длинный")
    private String email;

    public EmailForm() {
    }

    public EmailForm(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
