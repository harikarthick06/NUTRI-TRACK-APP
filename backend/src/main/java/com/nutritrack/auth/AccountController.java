package com.nutritrack.auth;

import com.nutritrack.auth.AuthDtos.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
@Tag(name = "Account")
public class AccountController {
    private final AccountService accountService;
    private final AuthController authController;

    public AccountController(AccountService accountService, AuthController authController) {
        this.accountService = accountService;
        this.authController = authController;
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Pattern(regexp = AuthDtos.PASSWORD_RULE, message = AuthDtos.PASSWORD_MESSAGE) String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank @Size(max = 128) String password) {
    }

    @PutMapping("/password")
    @Operation(summary = "Change password; signs out all other sessions and returns a new one")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletResponse res) {
        return authController.withCookie(accountService.changePassword(req.currentPassword(), req.newPassword()), res);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Permanently delete the account and all of its data")
    public void deleteAccount(@Valid @RequestBody DeleteAccountRequest req, HttpServletResponse res) {
        accountService.deleteAccount(req.password());
        res.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(AuthController.REFRESH_COOKIE, "")
                .httpOnly(true).path("/api/auth").maxAge(0).build().toString());
    }
}
