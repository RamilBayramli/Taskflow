package com.taskflow.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public class RegisterForm {
    @NotBlank(message = "Укажите имя")
    @Size(max = 60, message = "Имя — до 60 символов")
    private String username;

    @NotBlank(message = "Укажите email")
    @Email(message = "Проверьте адрес email")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Придумайте пароль")
    @Size(min = 8, max = 72, message = "Пароль — от 8 до 72 символов")
    private String password;

    private String confirmPassword;

    @AssertTrue(message = "Пароли не совпадают")
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }

    @AssertTrue(message = "Пароль должен занимать не более 72 байт UTF-8")
    public boolean isPasswordByteLengthValid() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
