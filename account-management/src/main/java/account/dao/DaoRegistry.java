package account.dao;

import account.dao.core.ConnectionProvider;
import account.dao.core.DriverManagerConnectionProvider;
import account.dao.core.JdbcExecutor;
import account.dao.core.TransactionManager;
import account.integration.BlacklistClient;
import account.service.AccountBlacklistSupport;
import java.util.Objects;

public final class DaoRegistry {

    private final JdbcExecutor executor;
    private final TransactionManager transactionManager;
    private final InvestorDao investorDao;
    private final SecurityAccountDao securityAccountDao;
    private final FundAccountDao fundAccountDao;
    private final FundTransactionLogDao fundTransactionLogDao;
    private final HoldingDao holdingDao;
    private final HoldingChangeLogDao holdingChangeLogDao;
    private final OperationLogDao operationLogDao;
    private final StaffDao staffDao;
    private final LoginCertificateStateDao loginCertificateStateDao;

    public DaoRegistry(ConnectionProvider connectionProvider) {
        Objects.requireNonNull(connectionProvider, "connectionProvider");
        this.executor = new JdbcExecutor(connectionProvider);
        this.transactionManager = new TransactionManager(connectionProvider);
        this.investorDao = new InvestorDao(executor);
        this.securityAccountDao = new SecurityAccountDao(executor);
        this.fundAccountDao = new FundAccountDao(executor);
        this.fundTransactionLogDao = new FundTransactionLogDao(executor);
        this.holdingDao = new HoldingDao(executor);
        this.holdingChangeLogDao = new HoldingChangeLogDao(executor);
        this.operationLogDao = new OperationLogDao(executor);
        this.staffDao = new StaffDao(executor);
        this.loginCertificateStateDao = new LoginCertificateStateDao(executor);
    }

    public static DaoRegistry forDriverManager(String url, String username, String password) {
        return new DaoRegistry(new DriverManagerConnectionProvider(url, username, password));
    }

    public TransactionManager transactionManager() {
        return transactionManager;
    }

    public InvestorDao investorDao() {
        return investorDao;
    }

    public SecurityAccountDao securityAccountDao() {
        return securityAccountDao;
    }

    public FundAccountDao fundAccountDao() {
        return fundAccountDao;
    }

    public FundTransactionLogDao fundTransactionLogDao() {
        return fundTransactionLogDao;
    }

    public HoldingDao holdingDao() {
        return holdingDao;
    }

    public HoldingChangeLogDao holdingChangeLogDao() {
        return holdingChangeLogDao;
    }

    public OperationLogDao operationLogDao() {
        return operationLogDao;
    }

    public StaffDao staffDao() {
        return staffDao;
    }

    public LoginCertificateStateDao loginCertificateStateDao() {
        return loginCertificateStateDao;
    }

    public AccountBlacklistSupport blacklistSupport(BlacklistClient blacklistClient) {
        return new AccountBlacklistSupport(blacklistClient, investorDao, securityAccountDao, fundAccountDao);
    }
}
