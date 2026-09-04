package dev.ledger.app.web;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the exceptions the services actually throw onto status codes.
 *
 * <p>Deliberately small. A rejected regex, a category that does not exist and a transaction that is
 * not in the review queue are the three things a client gets wrong, and each needs a different code
 * and a message the user can act on.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ApiError> notFound(NoSuchElementException e) {
    return error(HttpStatus.NOT_FOUND, e.getMessage());
  }

  /** Covers a bad category id and a regex rejected by {@code RegexGuard}. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
    return error(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  /** Covers confirming a transaction that is not awaiting review. */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> conflict(IllegalStateException e) {
    return error(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(field -> field.getField() + " " + field.getDefaultMessage())
            .findFirst()
            .orElse("request body is invalid");
    return error(HttpStatus.BAD_REQUEST, detail);
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(new ApiError(status.value(), status.getReasonPhrase(), message, Instant.now()));
  }

  public record ApiError(int status, String error, String message, Instant timestamp) {}
}
