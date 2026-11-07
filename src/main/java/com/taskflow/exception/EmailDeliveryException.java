package com.taskflow.exception;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() {
        super("Не удалось отправить письмо. Попробуйте позже.");
    }
}
