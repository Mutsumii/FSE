package edu.zju.se.management.model;

import java.time.LocalDateTime;

public record BlacklistEntry(
        String idCardNo,
        String userName,
        String fundAccountNo,
        String securityAccountNo,
        String reason,
        boolean active,
        LocalDateTime createdAt
) {
}
