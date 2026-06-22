package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainEnums.AccountStatus;
import account.dao.model.DomainModels.SecurityAccount;
import java.sql.Connection;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

public final class SecurityAccountDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select sec_acc_no, investor_id, status, open_date, linked_fund_acc
              from security_account
            """;

    private static final RowMapper<SecurityAccount> SECURITY_ACCOUNT_MAPPER = resultSet -> new SecurityAccount(
            resultSet.getString("sec_acc_no"),
            resultSet.getInt("investor_id"),
            AccountStatus.fromDbValue(resultSet.getString("status")),
            getLocalDate(resultSet, "open_date"),
            resultSet.getString("linked_fund_acc")
    );

    public SecurityAccountDao(JdbcExecutor executor) {
        super(executor);
    }

    public Optional<SecurityAccount> findByAccountNo(String secAccNo) {
        return executor.queryOne(
                SELECT_COLUMNS + " where sec_acc_no = ?",
                statement -> statement.setString(1, secAccNo),
                SECURITY_ACCOUNT_MAPPER
        );
    }

    public Optional<SecurityAccount> findByAccountNoForUpdate(Connection connection, String secAccNo) {
        return executor.queryOne(
                connection,
                SELECT_COLUMNS + " where sec_acc_no = ? for update",
                statement -> statement.setString(1, secAccNo),
                SECURITY_ACCOUNT_MAPPER
        );
    }

    public Optional<SecurityAccount> findLatestNonClosedByInvestorId(int investorId) {
        return executor.queryOne(
                SELECT_COLUMNS + " where investor_id = ? and status <> ? order by open_date desc limit 1",
                statement -> {
                    statement.setInt(1, investorId);
                    statement.setString(2, AccountStatus.CLOSED.dbValue());
                },
                SECURITY_ACCOUNT_MAPPER
        );
    }

    public List<SecurityAccount> listByInvestorId(int investorId) {
        return executor.queryList(
                SELECT_COLUMNS + " where investor_id = ? order by open_date desc, sec_acc_no desc",
                statement -> statement.setInt(1, investorId),
                SECURITY_ACCOUNT_MAPPER
        );
    }

    public List<SecurityAccount> listAll() {
        return executor.queryList(
                SELECT_COLUMNS + " order by open_date desc, sec_acc_no desc",
                statement -> {},
                SECURITY_ACCOUNT_MAPPER
        );
    }

    public boolean create(Connection connection, SecurityAccount account) {
        String sql = """
                insert into security_account (sec_acc_no, investor_id, status, open_date, linked_fund_acc)
                values (?, ?, ?, ?, ?)
                """;
        return executor.update(connection, sql, statement -> {
            statement.setString(1, account.secAccNo());
            statement.setObject(2, account.investorId(), Types.INTEGER);
            statement.setString(3, account.status().dbValue());
            statement.setDate(4, toSqlDate(account.openDate()));
            statement.setString(5, account.linkedFundAcc());
        }) > 0;
    }

    public boolean updateStatus(Connection connection, String secAccNo, AccountStatus status) {
        String sql = "update security_account set status = ? where sec_acc_no = ?";
        return executor.update(connection, sql, statement -> {
            statement.setString(1, status.dbValue());
            statement.setString(2, secAccNo);
        }) > 0;
    }

    public boolean bindFundAccount(Connection connection, String secAccNo, String fundAccNo) {
        String sql = "update security_account set linked_fund_acc = ? where sec_acc_no = ?";
        return executor.update(connection, sql, statement -> {
            statement.setString(1, fundAccNo);
            statement.setString(2, secAccNo);
        }) > 0;
    }

    public boolean unbindFundAccount(Connection connection, String secAccNo) {
        String sql = "update security_account set linked_fund_acc = null where sec_acc_no = ?";
        return executor.update(connection, sql, statement -> statement.setString(1, secAccNo)) > 0;
    }
}
