package com.taskflow.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public class PasswordForm {
    @NotBlank(message = "Введите текущий пароль")
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 72, message = "Новый пароль — от 8 до 72 символов")
    private String newPassword;

    private String confirmPassword;

    @AssertTrue(message = "Новые пароли не совпадают")
    public boolean isPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @AssertTrue(message = "Пароль должен занимать не более 72 байт UTF-8")
    public boolean isPasswordByteLengthValid() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
