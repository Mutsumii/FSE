package account.service;

import account.dao.DaoRegistry;
import account.dao.model.DomainModels;
import account.dto.OperationLogQueryResponse;
import account.dto.OperationLogView;
import account.service.api.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 审计日志查询 Service 实现。
 */
@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    private final DaoRegistry dao;
    private final OperationLogViewMapper operationLogViewMapper;

    public AuditServiceImpl(DaoRegistry dao, OperationLogViewMapper operationLogViewMapper) {
        this.dao = dao;
        this.operationLogViewMapper = operationLogViewMapper;
    }

    @Override
    public OperationLogQueryResponse queryOperationLog(Integer operatorStaffId,
                                                       Integer staffId,
                                                       LocalDateTime timeFrom,
                                                       LocalDateTime timeTo,
                                                       String operationType,
                                                       String targetType,
                                                       String targetId) {
        var query = new DomainModels.OperationLogQuery(
                staffId, timeFrom, timeTo, operationType, targetType, targetId, 200, 0
        );

        var logs = dao.operationLogDao().query(query);

        List<OperationLogView> logViews = logs.stream()
                .map(operationLogViewMapper::toView)
                .toList();

        log.info("[queryOperationLog] staff_id={} time_from={} time_to={} count={}",
                staffId, timeFrom, timeTo, logViews.size());

        dao.transactionManager().execute(connection -> {
            dao.operationLogDao().create(connection, new DomainModels.OperationLog(
                    null,
                    operatorStaffId,
                    "查询操作日志",
                    "AUDIT",
                    Optional.ofNullable(targetId).orElse("operation_log"),
                    "查询条件: staff_id=" + staffId
                            + ", operation_type=" + operationType
                            + ", target_type=" + targetType
                            + ", time_from=" + timeFrom
                            + ", time_to=" + timeTo,
                    LocalDateTime.now()
            ));
            return null;
        });

        return OperationLogQueryResponse.builder()
                .logs(logViews)
                .total(logViews.size())
                .build();
    }
}
