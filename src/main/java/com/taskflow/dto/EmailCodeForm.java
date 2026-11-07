package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class EmailCodeForm extends EmailForm {
    @NotBlank(message = "Введите код из письма")
    @Pattern(regexp = "[0-9]{8}", message = "Код должен содержать 8 цифр")
    private String code;

    public EmailCodeForm() {
    }

    public EmailCodeForm(String email) {
        super(email);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
