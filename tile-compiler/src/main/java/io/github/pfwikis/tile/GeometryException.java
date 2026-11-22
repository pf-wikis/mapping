package io.github.pfwikis.tile;

class GeometryException extends Exception {
	GeometryException(String message) {
		super(message);
	}

	GeometryException(String message, Throwable cause) {
		super(message, cause);
	}

	GeometryException(String stat, String message, Throwable cause) {
		super(message, cause);
	}
}
