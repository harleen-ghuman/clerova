package com.clerova.exception;

import com.clerova.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(
            ApiException e
    ) {

        ApiError error = new ApiError(
                e.getCode(),
                e.getMessage()
        );

        return ResponseEntity
                .status(e.getStatus())
                .body(error);
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiError> handleAiServiceException(
            AiServiceException exception
    ) {

        ApiError error = new ApiError(
                "AI_SERVICE_ERROR",
                "Clerova couldn't create a suggestion right now. Please try again in a minute."
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflictException(
            ConflictException e
    ) {

        ApiError error = new ApiError(
                "INVALID_STATE_TRANSITION",
                e.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException e
    ) {

        ApiError error = new ApiError(
                "CONCURRENT_UPDATE",
                "This item was changed by another request. Refresh and try again."
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {

        ApiError error = new ApiError(
                "CONFLICT",
                "This change conflicts with another update. Refresh and try again."
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(error);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFoundException(
            NotFoundException e
    ) {

        // The exception names internal ids; keep those out of the app
        ApiError error = new ApiError(
                "NOT_FOUND",
                "We couldn't find that. It may have been removed. Refresh and try again."
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException e
    ) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(" "));

        ApiError error = new ApiError(
                "VALIDATION_ERROR",
                message
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(
            HttpMessageNotReadableException e
    ) {

        ApiError error = new ApiError(
                "INVALID_REQUEST",
                "Something in that request wasn't right. Refresh and try again."
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(error);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleBadParameter(Exception e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(
                        "INVALID_REQUEST",
                        "Something in that request wasn't right. Refresh and try again."
                ));
    }

    /** Spring's own errors (unknown route, wrong method) keep their status. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleSpringError(ErrorResponseException e) {
        return ResponseEntity
                .status(e.getStatusCode())
                .body(new ApiError("REQUEST_FAILED", "That didn't work. Refresh and try again."));
    }

    /** Anything unexpected: logged in full, shown in plain words. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) throws Exception {
        if (e instanceof org.springframework.web.ErrorResponse errorResponse) {
            return ResponseEntity
                    .status(errorResponse.getStatusCode())
                    .body(new ApiError("REQUEST_FAILED", "That didn't work. Refresh and try again."));
        }

        if (e instanceof org.springframework.security.access.AccessDeniedException
                || e instanceof org.springframework.security.core.AuthenticationException) {
            // Let Spring Security answer with 401/403
            throw e;
        }

        log.error("Unexpected error", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(
                        "SERVER_ERROR",
                        "Something went wrong on our side. Please try again in a minute."
                ));
    }
}
