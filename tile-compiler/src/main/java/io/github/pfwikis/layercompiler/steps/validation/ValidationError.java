package io.github.pfwikis.layercompiler.steps.validation;

/**
 * Checked exception raised when any PMTile validation rule detects a problem.
 * <p>
 * Because all validation rules throw this single exception type, the top-level
 * {@link io.github.pfwikis.layercompiler.steps.validation.ValidateTiles} executor
 * can catch it and turn it into a clear failure message. The exception message is
 * shown directly to the user together with the rule name that triggered the failure.
 */
public class ValidationError extends RuntimeException {

    public ValidationError(String message) {
        super(message);
    }

    public ValidationError(String message, Throwable cause) {
        super(message, cause);
    }
}
