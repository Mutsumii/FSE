package account.dao;

import account.dao.core.JdbcExecutor;
import account.dao.core.RowMapper;
import account.dao.model.DomainModels.Holding;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class HoldingDao extends BaseJdbcDao {

    private static final String SELECT_COLUMNS = """
            select holding_id, sec_acc_no, stock_code, stock_name, quantity, frozen_quantity, avg_cost, updated_at
              from holding
            """;

    private static final RowMapper<Holding> HOLDING_MAPPER = resultSet -> new Holding(
            resultSet.getLong("holding_id"),
            resultSet.getString("sec_acc_no"),
            resultSet.getString("stock_code"),
            resultSet.getString("stock_name"),
            resultSet.getInt("quantity"),
            resultSet.getInt("frozen_quantity"),
            resultSet.getBigDecimal("avg_cost"),
            getLocalDateTime(resultSet, "updated_at")
    );

    public HoldingDao(JdbcExecutor executor) {
        super(executor);
    }

    public Optional<Holding> findByAccountAndStock(String secAccNo, String stockCode) {
        return executor.queryOne(
                SELECT_COLUMNS + " where sec_acc_no = ? and stock_code = ?",
                statement -> {
                    statement.setString(1, secAccNo);
                    statement.setString(2, stockCode);
                },
                HOLDING_MAPPER
        );
    }

    public Optional<Holding> findByAccountAndStockForUpdate(Connection connection, String secAccNo, String stockCode) {
        return executor.queryOne(
                connection,
                SELECT_COLUMNS + " where sec_acc_no = ? and stock_code = ? for update",
                statement -> {
                    statement.setString(1, secAccNo);
                    statement.setString(2, stockCode);
                },
                HOLDING_MAPPER
        );
    }

    public List<Holding> listBySecurityAccountNo(String secAccNo) {
        return executor.queryList(
                SELECT_COLUMNS + " where sec_acc_no = ? order by stock_code",
                statement -> statement.setString(1, secAccNo),
                HOLDING_MAPPER
        );
    }

    public int sumQuantityBySecurityAccountNo(String secAccNo) {
        return executor.queryOne(
                "select coalesce(sum(quantity), 0) as total_quantity from holding where sec_acc_no = ?",
                statement -> statement.setString(1, secAccNo),
                resultSet -> resultSet.getInt("total_quantity")
        ).orElse(0);
    }

    public Holding saveOrUpdate(Connection connection, Holding holding) {
        Optional<Holding> existing = findByAccountAndStockForUpdate(connection, holding.secAccNo(), holding.stockCode());
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            String sql = """
                    update holding
                       set stock_name = ?,
                           quantity = ?,
                           frozen_quantity = ?,
                           avg_cost = ?,
                           updated_at = ?
                     where holding_id = ?
                    """;
            executor.update(connection, sql, statement -> {
                statement.setString(1, holding.stockName());
                statement.setInt(2, holding.quantity());
                statement.setInt(3, holding.frozenQuantity());
                statement.setBigDecimal(4, holding.avgCost());
                statement.setTimestamp(5, toSqlTimestamp(now));
                statement.setLong(6, existing.get().holdingId());
            });
            return new Holding(
                    existing.get().holdingId(),
                    holding.secAccNo(),
                    holding.stockCode(),
                    holding.stockName(),
                    holding.quantity(),
                    holding.frozenQuantity(),
                    holding.avgCost(),
                    now
            );
        }

        String sql = """
                insert into holding (sec_acc_no, stock_code, stock_name, quantity, frozen_quantity, avg_cost, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """;
        long holdingId = executor.insertAndReturnKey(connection, sql, statement -> {
            statement.setString(1, holding.secAccNo());
            statement.setString(2, holding.stockCode());
            statement.setString(3, holding.stockName());
            statement.setInt(4, holding.quantity());
            statement.setInt(5, holding.frozenQuantity());
            statement.setBigDecimal(6, holding.avgCost());
            statement.setTimestamp(7, toSqlTimestamp(now));
        });
        return new Holding(
                holdingId,
                holding.secAccNo(),
                holding.stockCode(),
                holding.stockName(),
                holding.quantity(),
                holding.frozenQuantity(),
                holding.avgCost(),
                now
        );
    }

    public boolean deleteBySecurityAccountNo(Connection connection, String secAccNo) {
        return executor.update(
                connection,
                "delete from holding where sec_acc_no = ?",
                statement -> statement.setString(1, secAccNo)
        ) >= 0;
    }
}
