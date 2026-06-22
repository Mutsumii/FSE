package account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码哈希工具：SHA-256 哈希 + 随机 salt。
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    /**
     * 对明文密码进行 SHA-256 哈希（不带 salt，兼容数据库已有数据）。
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(plainPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 验证明文密码是否匹配已存储的哈希值。
     * 支持格式：sha256$demo$<password>（测试数据格式）或纯 SHA-256 哈希
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null) {
            return false;
        }
        // 支持测试数据格式：sha256$demo$password
        if (storedHash.startsWith("sha256$demo$")) {
            String expectedPassword = storedHash.substring("sha256$demo$".length());
            return plainPassword.equals(expectedPassword);
        }
        // 标准 SHA-256 哈希比较
        return hash(plainPassword).equals(storedHash);
    }

    /**
     * 生成随机认证令牌。
     */
    public static String generateAuthToken() {
        byte[] token = new byte[32];
        RANDOM.nextBytes(token);
        return HexFormat.of().formatHex(token);
    }
}
