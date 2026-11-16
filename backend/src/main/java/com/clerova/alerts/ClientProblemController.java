package com.clerova.alerts;

import com.clerova.security.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Where the app reports a screen that broke in someone's browser, so it
 * shows up in the server log and the alerts instead of staying on their phone.
 */
@RestController
public class ClientProblemController {

    private static final int MAX_TEXT = 600;

    private final ProblemAlerts alerts;
    private final CurrentUserService currentUserService;

    public ClientProblemController(ProblemAlerts alerts, CurrentUserService currentUserService) {
        this.alerts = alerts;
        this.currentUserService = currentUserService;
    }

    public record ClientProblem(String message, String where, String details) {
    }

    @PostMapping("/api/client-problems")
    public ResponseEntity<Void> report(@RequestBody ClientProblem problem) {
        UUID workspace;

        try {
            workspace = currentUserService.findCurrentUser()
                    .map(user -> user.getOrganization().getId())
                    .orElse(null);
        } catch (RuntimeException e) {
            workspace = null;
        }

        String message = clip(problem.message());
        String details = clip(problem.details());

        alerts.report(
                ProblemAlerts.Kind.SCREEN_ERROR,
                "A screen stopped working in a customer's browser: " + (message.isBlank() ? "(no message)" : message),
                workspace,
                clip(problem.where()),
                details.isBlank() ? null : new RuntimeException(details)
        );

        return ResponseEntity.noContent().build();
    }

    private static String clip(String text) {
        if (text == null) {
            return "";
        }

        // One line, no control characters: this goes into the log and an email
        String clean = text.replaceAll("[\\p{Cntrl}&&[^\n]]", " ").strip();
        return clean.length() > MAX_TEXT ? clean.substring(0, MAX_TEXT) + "..." : clean;
    }
}
