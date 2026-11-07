package com.taskflow.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

public class ResetPasswordForm extends EmailCodeForm {
    @NotBlank(message = "Придумайте новый пароль")
    @Size(min = 8, max = 72, message = "Пароль — от 8 до 72 символов")
    private String newPassword;

    private String confirmPassword;

    public ResetPasswordForm() {
    }

    public ResetPasswordForm(String email) {
        super(email);
    }

    @AssertTrue(message = "Пароли не совпадают")
    public boolean isPasswordMatching() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    @AssertTrue(message = "Пароль должен занимать не более 72 байт UTF-8")
    public boolean isPasswordByteLengthValid() {
        return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
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
