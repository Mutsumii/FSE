package account.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 账户编号生成器。
 */
public final class AccountNumberGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private AccountNumberGenerator() {
    }

    /**
     * 生成新的资金账户号：FA + 日期(8位) + 随机数(6位)。
     */
    public static String generateFundAccountNo() {
        String datePart = LocalDate.now().format(DATE_FMT);
        int randomPart = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "FA" + datePart + String.format("%06d", randomPart);
    }

    /**
     * 生成新的证券账户号：SA + 日期(8位) + 随机数(6位)。
     */
    public static String generateSecurityAccountNo() {
        String datePart = LocalDate.now().format(DATE_FMT);
        int randomPart = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "SA" + datePart + String.format("%06d", randomPart);
    }
}
