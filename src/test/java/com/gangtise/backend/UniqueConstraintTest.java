package com.gangtise.backend;

import com.gangtise.backend.domain.aggregate.Authority;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.entity.UserAccount;
import com.gangtise.backend.infrastructure.enums.AuthorityTypeEnum;
import com.gangtise.backend.infrastructure.enums.EmployeeStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.AuthorityMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserAccountMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务键唯一性约束验证。
 *
 * <p>三个业务键必须有唯一约束：{@code employee.work_no}、{@code authority.mnemonic}、
 * {@code user_account.identifier}。前者是员工的业务主键，中者是权限字典的幂等冲突键，
 * 后者是登录标识。这里从两个角度锁定该约定：
 * <ol>
 *   <li>元数据：对应列上存在非主键的唯一索引；</li>
 *   <li>行为：重复写入会被数据库拒绝，并翻译成 {@link DuplicateKeyException}，
 *       由 {@code GlobalExceptionHandler} 转成友好提示（而非 500 系统繁忙）。</li>
 * </ol>
 * 测试方法均带事务并回滚，不会污染数据库。
 */
@SpringBootTest
@Transactional
class UniqueConstraintTest {

    /** 目标列上是否存在非主键的唯一索引（单列索引；复合索引也会命中其中任一列） */
    private static final String UNIQUE_INDEX_SQL = """
            select count(*)
            from pg_index i
            join pg_class t on t.oid = i.indrelid
            join pg_attribute a on a.attrelid = t.oid and a.attnum = any(i.indkey::int2[])
            where t.relname = ?
              and a.attname = ?
              and i.indisunique
              and not i.indisprimary
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private AuthorityMapper authorityMapper;
    @Autowired
    private UserAccountMapper userAccountMapper;

    @ParameterizedTest(name = "{0}.{1} 上有唯一索引")
    @CsvSource({
            "employee,     work_no",
            "authority,    mnemonic",
            "user_account, identifier"
    })
    @DisplayName("三个业务键均存在唯一索引")
    void uniqueIndexExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(UNIQUE_INDEX_SQL, Integer.class, table, column);
        assertTrue(count != null && count > 0,
                table + "." + column + " 缺少唯一索引，请检查 docs/db/schema.sql 是否已执行");
        System.out.println("[UNIQUE] " + table + "." + column + " 唯一索引校验通过");
    }

    @Test
    @DisplayName("重复工号被拒绝（employee.work_no）")
    void duplicateWorkNoIsRejected() {
        String workNo = "DUP-WORK-NO";

        Employee first = new Employee();
        first.setWorkNo(workNo);
        first.setName("唯一约束测试-1");
        first.setStatus(EmployeeStatusEnum.FORMAL.getStatus());
        employeeMapper.insert(first);

        Employee duplicate = new Employee();
        duplicate.setWorkNo(workNo);
        duplicate.setName("唯一约束测试-2");
        duplicate.setStatus(EmployeeStatusEnum.FORMAL.getStatus());

        assertDuplicateRejected("employee.work_no", () -> employeeMapper.insert(duplicate));
    }

    @Test
    @DisplayName("重复助记符被拒绝（authority.mnemonic）")
    void duplicateMnemonicIsRejected() {
        int mnemonic = 99990001;

        Authority first = new Authority();
        first.setMnemonic(mnemonic);
        first.setAuthName("唯一约束测试-权限");
        first.setType(AuthorityTypeEnum.PAGE.getValue());
        authorityMapper.insert(first);

        Authority duplicate = new Authority();
        duplicate.setMnemonic(mnemonic);
        duplicate.setAuthName("唯一约束测试-权限副本");
        duplicate.setType(AuthorityTypeEnum.PAGE.getValue());

        assertDuplicateRejected("authority.mnemonic", () -> authorityMapper.insert(duplicate));
    }

    @Test
    @DisplayName("重复登录标识被拒绝（user_account.identifier）")
    void duplicateIdentifierIsRejected() {
        String identifier = "dup_identifier_test";

        UserAccount first = new UserAccount();
        first.setUserId(1);
        first.setIdentifier(identifier);
        first.setCredential("$2a$10$uniqueConstraintTestOnly");
        userAccountMapper.insert(first);

        UserAccount duplicate = new UserAccount();
        duplicate.setUserId(2);
        duplicate.setIdentifier(identifier);
        duplicate.setCredential("$2a$10$uniqueConstraintTestOnly");

        assertDuplicateRejected("user_account.identifier", () -> userAccountMapper.insert(duplicate));
    }

    private void assertDuplicateRejected(String target, Runnable action) {
        Throwable thrown = assertThrows(Throwable.class, action::run, target + " 重复写入应被数据库拒绝");
        assertTrue(thrown instanceof DuplicateKeyException,
                target + " 期望抛出 DuplicateKeyException（GlobalExceptionHandler 依赖该类型返回友好提示），"
                        + "实际为 " + thrown.getClass().getName() + "：" + thrown.getMessage());
        System.out.println("[UNIQUE] " + target + " 重复写入被拒绝 -> " + thrown.getClass().getSimpleName());
    }

}
