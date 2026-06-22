package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainEnums.InvestorType;
import account.dao.model.DomainModels.Investor;
import java.sql.Connection;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class InvestorDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select investor_id, type, name, gender, id_type, id_number, phone, address, work_unit, occupation, education,
                   legal_number, business_license, executor_name, executor_id_number, executor_phone, executor_address,
                   agent_name, agent_id_number, created_at
              from investor
            """;

    private static final RowMapper<Investor> INVESTOR_MAPPER = resultSet -> new Investor(
            resultSet.getInt("investor_id"),
            InvestorType.fromDbValue(resultSet.getString("type")),
            resultSet.getString("name"),
            resultSet.getString("gender"),
            resultSet.getString("id_type"),
            resultSet.getString("id_number"),
            resultSet.getString("phone"),
            resultSet.getString("address"),
            resultSet.getString("work_unit"),
            resultSet.getString("occupation"),
            resultSet.getString("education"),
            resultSet.getString("legal_number"),
            resultSet.getString("business_license"),
            resultSet.getString("executor_name"),
            resultSet.getString("executor_id_number"),
            resultSet.getString("executor_phone"),
            resultSet.getString("executor_address"),
            resultSet.getString("agent_name"),
            resultSet.getString("agent_id_number"),
            getLocalDateTime(resultSet, "created_at")
    );

    public InvestorDao(JdbcExecutor executor) {
        super(executor);
    }

    public Optional<Investor> findById(int investorId) {
        return executor.queryOne(
                SELECT_COLUMNS + " where investor_id = ?",
                statement -> statement.setInt(1, investorId),
                INVESTOR_MAPPER
        );
    }

    public Optional<Investor> findByIdNumber(String idNumber) {
        return executor.queryOne(
                SELECT_COLUMNS + " where id_number = ?",
                statement -> statement.setString(1, idNumber),
                INVESTOR_MAPPER
        );
    }

    public List<Investor> searchByName(String keyword) {
        return executor.queryList(
                SELECT_COLUMNS + " where name like ? order by created_at desc",
                statement -> statement.setString(1, likeKeyword(keyword)),
                INVESTOR_MAPPER
        );
    }

    public int create(Connection connection, Investor investor) {
        String sql = """
                insert into investor (
                    type, name, gender, id_type, id_number, phone, address, work_unit, occupation, education,
                    legal_number, business_license, executor_name, executor_id_number, executor_phone, executor_address,
                    agent_name, agent_id_number, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return Math.toIntExact(executor.insertAndReturnKey(connection, sql, statement -> {
            statement.setString(1, investor.type().dbValue());
            statement.setString(2, investor.name());
            statement.setString(3, investor.gender());
            statement.setString(4, investor.idType());
            statement.setString(5, investor.idNumber());
            statement.setString(6, investor.phone());
            statement.setString(7, investor.address());
            statement.setString(8, investor.workUnit());
            statement.setString(9, investor.occupation());
            statement.setString(10, investor.education());
            statement.setString(11, investor.legalNumber());
            statement.setString(12, investor.businessLicense());
            statement.setString(13, investor.executorName());
            statement.setString(14, investor.executorIdNumber());
            statement.setString(15, investor.executorPhone());
            statement.setString(16, investor.executorAddress());
            statement.setString(17, investor.agentName());
            statement.setString(18, investor.agentIdNumber());
            statement.setTimestamp(19, toSqlTimestamp(defaultTimestamp(investor.createdAt())));
        }));
    }

    public boolean update(Connection connection, Investor investor) {
        String sql = """
                update investor
                   set type = ?,
                       name = ?,
                       gender = ?,
                       id_type = ?,
                       id_number = ?,
                       phone = ?,
                       address = ?,
                       work_unit = ?,
                       occupation = ?,
                       education = ?,
                       legal_number = ?,
                       business_license = ?,
                       executor_name = ?,
                       executor_id_number = ?,
                       executor_phone = ?,
                       executor_address = ?,
                       agent_name = ?,
                       agent_id_number = ?
                 where investor_id = ?
                """;
        return executor.update(connection, sql, statement -> {
            statement.setString(1, investor.type().dbValue());
            statement.setString(2, investor.name());
            statement.setString(3, investor.gender());
            statement.setString(4, investor.idType());
            statement.setString(5, investor.idNumber());
            statement.setString(6, investor.phone());
            statement.setString(7, investor.address());
            statement.setString(8, investor.workUnit());
            statement.setString(9, investor.occupation());
            statement.setString(10, investor.education());
            statement.setString(11, investor.legalNumber());
            statement.setString(12, investor.businessLicense());
            statement.setString(13, investor.executorName());
            statement.setString(14, investor.executorIdNumber());
            statement.setString(15, investor.executorPhone());
            statement.setString(16, investor.executorAddress());
            statement.setString(17, investor.agentName());
            statement.setString(18, investor.agentIdNumber());
            statement.setObject(19, investor.investorId(), Types.INTEGER);
        }) > 0;
    }

    private LocalDateTime defaultTimestamp(LocalDateTime createdAt) {
        return createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
