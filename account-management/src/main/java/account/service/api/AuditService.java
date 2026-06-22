package account.service.api;

import account.dto.OperationLogQueryResponse;

import java.time.LocalDateTime;

public interface AuditService {

    OperationLogQueryResponse queryOperationLog(Integer operatorStaffId,
                                                Integer staffId,
                                                LocalDateTime timeFrom,
                                                LocalDateTime timeTo,
                                                String operationType,
                                                String targetType,
                                                String targetId);
}
