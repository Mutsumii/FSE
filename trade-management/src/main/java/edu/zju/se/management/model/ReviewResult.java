package edu.zju.se.management.model;

public record ReviewResult(
        String reviewId,
        String orderId,
        boolean approved,
        String reviewStatus,
        String riskLevel,
        String rejectCode,
        String reason,
        String message
) {
    public static ReviewResult approved(String reviewId, String orderId) {
        return new ReviewResult(reviewId, orderId, true, "AUTO_APPROVED", "LOW", null, null, "审查通过");
    }

    public static ReviewResult pendingManual(String reviewId, String orderId, String rejectCode, String reason) {
        return new ReviewResult(reviewId, orderId, false, "PENDING_MANUAL", "MEDIUM", rejectCode, reason, "进入人工核验");
    }

    public static ReviewResult rejected(String reviewId, String orderId, String rejectCode, String reason) {
        return new ReviewResult(reviewId, orderId, false, "REJECTED", "HIGH", rejectCode, reason, null);
    }
}
