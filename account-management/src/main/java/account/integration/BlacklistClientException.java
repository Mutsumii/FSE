package account.integration;

public final class BlacklistClientException extends RuntimeException {

    public BlacklistClientException(String message) {
        super(message);
    }

    public BlacklistClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
