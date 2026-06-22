package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainEnums.AccountStatus;
import account.dao.model.DomainModels.FundAccount;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

public final class FundAccountDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select fund_acc_no, sec_acc_no, trade_password, withdraw_password, available_balance,
                   frozen_balance, currency, status, open_date, last_interest_date, annual_interest_rate
              from fund_account
            """;

    private static final RowMapper<FundAccount> FUND_ACCOUNT_MAPPER = resultSet -> new FundAccount(
            resultSet.getString("fund_acc_no"),
            resultSet.getString("sec_acc_no"),
            resultSet.getString("trade_password"),
            resultSet.getString("withdraw_password"),
            resultSet.getBigDecimal("available_balance"),
            resultSet.getBigDecimal("frozen_balance"),
            resultSet.getString("currency"),
            AccountStatus.fromDbValue(resultSet.getString("status")),
            getLocalDate(resultSet, "open_date"),
            getLocalDate(resultSet, "last_interest_date"),
            resultSet.getBigDecimal("annual_interest_rate")
    );

    public FundAccountDao(JdbcExecutor executor) {
        super(executor);
    }

    public Optional<FundAccount> findByAccountNo(String fundAccNo) {
        return executor.queryOne(
                SELECT_COLUMNS + " where fund_acc_no = ?",
                statement -> statement.setString(1, fundAccNo),
                FUND_ACCOUNT_MAPPER
        );
    }

    public Optional<FundAccount> findByAccountNoForUpdate(Connection connection, String fundAccNo) {
        return executor.queryOne(
                connection,
                SELECT_COLUMNS + " where fund_acc_no = ? for update",
                statement -> statement.setString(1, fundAccNo),
                FUND_ACCOUNT_MAPPER
        );
    }

    public Optional<FundAccount> findBySecurityAccountNo(String secAccNo) {
        return executor.queryOne(
                SELECT_COLUMNS + " where sec_acc_no = ?",
                statement -> statement.setString(1, secAccNo),
                FUND_ACCOUNT_MAPPER
        );
    }

    public List<FundAccount> findEligibleForInterestPosting(java.time.LocalDate interestDate) {
        String sql = SELECT_COLUMNS + """
                 where status = ?
                   and (last_interest_date is null or last_interest_date < ?)
                 order by fund_acc_no
                """;
        return executor.queryList(sql, statement -> {
            statement.setString(1, AccountStatus.NORMAL.dbValue());
            statement.setDate(2, toSqlDate(interestDate));
        }, FUND_ACCOUNT_MAPPER);
    }

    public List<FundAccount> listAll() {
        return executor.queryList(
                SELECT_COLUMNS + " order by open_date desc, fund_acc_no desc",
                statement -> {},
                FUND_ACCOUNT_MAPPER
        );
    }

    public boolean create(Connection connection, FundAccount account) {
        String sql = """
                insert into fund_account (
                    fund_acc_no, sec_acc_no, trade_password, withdraw_password, available_balance,
                    frozen_balance, currency, status, open_date, last_interest_date, annual_interest_rate
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return executor.update(connection, sql, statement -> {
            statement.setString(1, account.fundAccNo());
            statement.setString(2, account.secAccNo());
            statement.setString(3, account.tradePassword());
            statement.setString(4, account.withdrawPassword());
            statement.setBigDecimal(5, defaultMoney(account.availableBalance()));
            statement.setBigDecimal(6, defaultMoney(account.frozenBalance()));
            statement.setString(7, account.currency());
            statement.setString(8, account.status().dbValue());
            statement.setDate(9, toSqlDate(account.openDate()));
            statement.setDate(10, toSqlDate(account.lastInterestDate()));
            statement.setBigDecimal(11, account.annualInterestRate());
        }) > 0;
    }

    public boolean updateBalances(Connection connection, String fundAccNo, BigDecimal availableBalance, BigDecimal frozenBalance) {
        String sql = """
                update fund_account
                   set available_balance = ?,
                       frozen_balance = ?
                 where fund_acc_no = ?
                """;
        return executor.update(connection, sql, statement -> {
            statement.setBigDecimal(1, availableBalance);
            statement.setBigDecimal(2, frozenBalance);
            statement.setString(3, fundAccNo);
        }) > 0;
    }

    public boolean updateStatus(Connection connection, String fundAccNo, AccountStatus status) {
        String sql = "update fund_account set status = ? where fund_acc_no = ?";
        return executor.update(connection, sql, statement -> {
            statement.setString(1, status.dbValue());
            statement.setString(2, fundAccNo);
        }) > 0;
    }

    public boolean relinkSecurityAccount(Connection connection, String fundAccNo, String secAccNo) {
        String sql = "update fund_account set sec_acc_no = ? where fund_acc_no = ?";
        return executor.update(connection, sql, statement -> {
            statement.setString(1, secAccNo);
            statement.setString(2, fundAccNo);
        }) > 0;
    }

    public boolean updateTradePassword(Connection connection, String fundAccNo, String passwordHash) {
        String sql = "update fund_account set trade_password = ? where fund_acc_no = ?";
        return executor.update(connection, sql, statement -> {
            statement.setString(1, passwordHash);
            statement.setString(2, fundAccNo);
        }) > 0;
    }

    public boolean updateWithdrawPassword(Connection connection, String fundAccNo, String passwordHash) {
        String sql = "update fund_account set withdraw_password = ? where fund_acc_no = ?";
        return executor.update(connection, sql, statement -> {
            statement.setString(1, passwordHash);
            statement.setString(2, fundAccNo);
        }) > 0;
    }

    public boolean updateInterestPosting(Connection connection, String fundAccNo, BigDecimal newAvailableBalance, java.time.LocalDate interestDate) {
        String sql = """
                update fund_account
                   set available_balance = ?,
                       last_interest_date = ?
                 where fund_acc_no = ?
                """;
        return executor.update(connection, sql, statement -> {
            statement.setBigDecimal(1, newAvailableBalance);
            statement.setDate(2, toSqlDate(interestDate));
            statement.setString(3, fundAccNo);
        }) > 0;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
