package io.github.pfwikis.layercompiler.steps.validation;

/**
 * Thrown when a tile validation assertion fails.
 * Carries a human-readable message describing what was expected versus what was found.
 */
public class TileValidationException extends AssertionError {

    public TileValidationException(String message) {
        super(message);
    }

    public TileValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static TileValidationException fail(String msg) {
        throw new TileValidationException(msg);
    }
}
