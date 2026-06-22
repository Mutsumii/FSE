package account.integration;

public interface BlacklistClient {

    boolean isBlocked(String userName);
}
