package com.manarah.common.exception;

import org.springframework.http.HttpStatus;

/** Domain-level exceptions carrying an HTTP status, translated by {@link GlobalExceptionHandler}. */
public class ApiExceptions {

    private ApiExceptions() {
    }

    public static class ApiException extends RuntimeException {
        private final HttpStatus status;

        public ApiException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }

    public static class NotFoundException extends ApiException {
        public NotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, message);
        }

        public static NotFoundException of(String entity, Object id) {
            return new NotFoundException(entity + " غير موجود (" + id + ")");
        }
    }

    public static class BadRequestException extends ApiException {
        public BadRequestException(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }
    }

    public static class ConflictException extends ApiException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, message);
        }
    }

    public static class ForbiddenException extends ApiException {
        public ForbiddenException(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String message) {
            super(HttpStatus.UNAUTHORIZED, message);
        }
    }

    public static class TooManyRequestsException extends ApiException {
        public TooManyRequestsException(String message) {
            super(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }
}
