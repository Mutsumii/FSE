package com.stock.publish.interceptor;

public class UserContext {
    private static final ThreadLocal<String> GLOBAL_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> ROLE = new ThreadLocal<>();

    private UserContext() {}

    public static void setGlobalUserId(String userId) {
        GLOBAL_USER_ID.set(userId);
    }

    public static String getGlobalUserId() {
        return GLOBAL_USER_ID.get();
    }

    public static void setRole(UserRole role) {
        ROLE.set(role);
    }

    public static UserRole getRole() {
        UserRole r = ROLE.get();
        return r != null ? r : UserRole.GUEST;
    }

    public static void clear() {
        GLOBAL_USER_ID.remove();
        ROLE.remove();
    }

    public enum UserRole {
        GUEST, STANDARD, PREMIUM_VIP
    }
}
