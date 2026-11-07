package com.taskflow.controller;

import com.taskflow.dto.EmailCodeForm;
import com.taskflow.dto.EmailForm;
import com.taskflow.dto.RegisterForm;
import com.taskflow.dto.ResetPasswordForm;
import com.taskflow.exception.DuplicateValueException;
import com.taskflow.exception.EmailDeliveryException;
import com.taskflow.service.EmailAccountService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private static final String MAIL_UNAVAILABLE = "Отправка писем временно недоступна.";
    private static final String INVALID_CODE = "Код неверный или истёк. Запросите новый код.";
    private static final String CODE_REQUESTED = "Если для этого адреса можно отправить код, он придёт на почту.";

    private final EmailAccountService accounts;

    public AuthController(EmailAccountService accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute("mailConfigured")
    boolean mailConfigured() {
        return accounts.isMailConfigured();
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/register")
    String register(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute RegisterForm registerForm, BindingResult errors,
                    RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "register";
        }

        try {
            accounts.register(registerForm);
        } catch (DuplicateValueException | DataIntegrityViolationException exception) {
            errors.rejectValue("email", "duplicate", "Этот email уже зарегистрирован");
            return "register";
        } catch (EmailDeliveryException exception) {
            errors.reject("mailUnavailable", MAIL_UNAVAILABLE);
            return "register";
        }

        redirect.addFlashAttribute("codeForm", new EmailCodeForm(registerForm.getEmail()));
        redirect.addFlashAttribute("notice", "Код отправлен на вашу почту. Он действует 15 минут.");
        return "redirect:/confirm-email";
    }

    @GetMapping("/confirm-email")
    String confirmEmail(@ModelAttribute("codeForm") EmailCodeForm form, Model model) {
        return codePage(model, false);
    }

    @PostMapping("/confirm-email")
    String confirmEmail(@Valid @ModelAttribute("codeForm") EmailCodeForm form, BindingResult errors, Model model) {
        if (errors.hasErrors()) {
            return codePage(model, false);
        }
        if (accounts.confirmEmail(form.getEmail(), form.getCode())) {
            return "redirect:/login?emailConfirmed";
        }
        errors.rejectValue("code", "invalidCode", INVALID_CODE);
        return codePage(model, false);
    }

    @PostMapping("/confirm-email/resend")
    String resendConfirmation(@Valid @ModelAttribute("emailForm") EmailForm form, BindingResult errors,
                              RedirectAttributes redirect) {
        redirect.addFlashAttribute("codeForm", new EmailCodeForm(form.getEmail()));
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("formError", "Укажите действительный email.");
        } else if (!mailConfigured()) {
            redirect.addFlashAttribute("formError", MAIL_UNAVAILABLE);
        } else {
            accounts.resendConfirmation(form.getEmail());
            redirect.addFlashAttribute("notice", CODE_REQUESTED);
        }
        return "redirect:/confirm-email";
    }

    @GetMapping("/forgot-password")
    String forgotPassword(@ModelAttribute("emailForm") EmailForm form) {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    String requestReset(@Valid @ModelAttribute("emailForm") EmailForm form, BindingResult errors,
                        RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "forgot-password";
        }
        if (!mailConfigured()) {
            errors.reject("mailUnavailable", MAIL_UNAVAILABLE);
            return "forgot-password";
        }
        accounts.requestPasswordReset(form.getEmail());
        redirect.addFlashAttribute("codeForm", new ResetPasswordForm(form.getEmail()));
        redirect.addFlashAttribute("notice", CODE_REQUESTED);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    String resetPassword(@ModelAttribute("codeForm") ResetPasswordForm form, Model model) {
        return codePage(model, true);
    }

    @PostMapping("/reset-password")
    String resetPassword(@Valid @ModelAttribute("codeForm") ResetPasswordForm form, BindingResult errors,
                         Model model) {
        if (errors.hasErrors()) {
            return codePage(model, true);
        }
        if (accounts.resetPassword(form.getEmail(), form.getCode(), form.getNewPassword())) {
            return "redirect:/login?passwordChanged";
        }
        errors.rejectValue("code", "invalidCode", INVALID_CODE);
        return codePage(model, true);
    }

    private String codePage(Model model, boolean resetMode) {
        model.addAttribute("resetMode", resetMode);
        return "account-code";
    }
}
