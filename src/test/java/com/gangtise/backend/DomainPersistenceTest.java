package com.gangtise.backend;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gangtise.backend.domain.aggregate.Employee;
import com.gangtise.backend.domain.aggregate.User;
import com.gangtise.backend.domain.aggregate.entity.Announcement;
import com.gangtise.backend.domain.aggregate.entity.UserSchedule;
import com.gangtise.backend.infrastructure.enums.AnnouncementStatusEnum;
import com.gangtise.backend.infrastructure.repository.mapper.AnnouncementMapper;
import com.gangtise.backend.infrastructure.repository.mapper.AttendanceMapper;
import com.gangtise.backend.infrastructure.repository.mapper.AttendanceRecordMapper;
import com.gangtise.backend.infrastructure.repository.mapper.AuthorityMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeMapper;
import com.gangtise.backend.infrastructure.repository.mapper.DepartmentRankMapper;
import com.gangtise.backend.infrastructure.repository.mapper.EmployeeSalaryMapper;
import com.gangtise.backend.infrastructure.repository.mapper.RoleAuthorityMapper;
import com.gangtise.backend.infrastructure.repository.mapper.RoleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.SettlementSalaryMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserAccountMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserLoginRecordMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserScheduleMapper;
import com.gangtise.backend.infrastructure.repository.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 领域实体与数据库表的映射验证（需要本地 PostgreSQL 可用）。
 *
 * <p>覆盖三件事：
 * <ul>
 *   <li>15 张表的表名 / 列名映射是否能生成合法 SQL；</li>
 *   <li>插入时主键回填与 create_time / update_time 自动填充是否生效；</li>
 *   <li>保留字表名 {@code "user"} 的引号处理是否可用。</li>
 * </ul>
 * 测试方法均带事务并在结束后回滚，不会污染数据库。
 */
@SpringBootTest
@Transactional
class DomainPersistenceTest {

    @Autowired
    private DepartmentMapper departmentMapper;
    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private DepartmentRankMapper departmentRankMapper;
    @Autowired
    private EmployeeSalaryMapper employeeSalaryMapper;
    @Autowired
    private AttendanceMapper attendanceMapper;
    @Autowired
    private AttendanceRecordMapper attendanceRecordMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private AuthorityMapper authorityMapper;
    @Autowired
    private RoleAuthorityMapper roleAuthorityMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private SettlementSalaryMapper settlementSalaryMapper;
    @Autowired
    private UserLoginRecordMapper userLoginRecordMapper;
    @Autowired
    private UserScheduleMapper userScheduleMapper;
    @Autowired
    private AnnouncementMapper announcementMapper;

    @Test
    void allEntityMappingsWork() {
        assertMappingWorks("department", departmentMapper);
        assertMappingWorks("employee", employeeMapper);
        assertMappingWorks("department_rank", departmentRankMapper);
        assertMappingWorks("employee_salary", employeeSalaryMapper);
        assertMappingWorks("attendance", attendanceMapper);
        assertMappingWorks("attendance_record", attendanceRecordMapper);
        assertMappingWorks("role", roleMapper);
        assertMappingWorks("authority", authorityMapper);
        assertMappingWorks("role_authority", roleAuthorityMapper);
        assertMappingWorks("\"user\"", userMapper);
        assertMappingWorks("user_account", userAccountMapper);
        assertMappingWorks("settlement_salary", settlementSalaryMapper);
        assertMappingWorks("user_login_record", userLoginRecordMapper);
        assertMappingWorks("user_schedule", userScheduleMapper);
        assertMappingWorks("announcement", announcementMapper);
    }

    /**
     * 同时校验表名与列名：selectCount 只用到表名，selectList 会展开实体的全部列，
     * 数据库解析 SQL 时会校验列是否存在，因此能覆盖 @TableField / 驼峰转换的映射错误。
     */
    private <T> void assertMappingWorks(String table, BaseMapper<T> mapper) {
        Long count = mapper.selectCount(null);
        assertNotNull(count, table + " 统计结果不应为 null");
        assertNotNull(mapper.selectList(null), table + " 查询结果不应为 null");
        System.out.println("[DOMAIN] " + table + " -> " + count + " 行, 表名与列名映射 OK");
    }

    @Test
    void insertFillsPrimaryKeyAndAuditFields() {
        Employee employee = new Employee();
        employee.setWorkNo("T0001");
        employee.setName("测试员工");
        employee.setGender(1);
        employee.setStatus(1);
        employee.setEducation(3);
        employee.setRank(1);
        employee.setDepartment(1);
        employeeMapper.insert(employee);

        assertNotNull(employee.getId(), "自增主键应回填到实体");
        assertNotNull(employee.getCreateTime(), "create_time 应由 MyMetaObjectHandler 自动填充");
        assertNotNull(employee.getUpdateTime(), "update_time 应由 MyMetaObjectHandler 自动填充");
        System.out.println("[DOMAIN] insert -> id=" + employee.getId()
                + " createTime=" + employee.getCreateTime()
                + " updateTime=" + employee.getUpdateTime());

        Employee loaded = employeeMapper.selectById(employee.getId());
        assertEquals("T0001", loaded.getWorkNo());
        assertEquals("测试员工", loaded.getName());
        assertEquals(1, loaded.getDepartment());
    }

    @Test
    void updateRefreshUpdateTime() throws InterruptedException {
        Employee employee = new Employee();
        employee.setWorkNo("T0002");
        employee.setName("待更新员工");
        employeeMapper.insert(employee);
        assertNotNull(employee.getCreateTime());

        Thread.sleep(1100);
        employee.setName("已更新员工");
        employeeMapper.updateById(employee);

        Employee loaded = employeeMapper.selectById(employee.getId());
        assertNotNull(loaded.getUpdateTime(), "update_time 应在更新时刷新");
        assertTrue(loaded.getUpdateTime().isAfter(loaded.getCreateTime()),
                "更新后的 update_time 应晚于 create_time");
        System.out.println("[DOMAIN] update -> " + loaded.getName() + " " + loaded.getUpdateTime());
    }

    @Test
    void insertUserScheduleWorks() {
        LocalDateTime scheduleTime = LocalDateTime.of(2026, 10, 20, 9, 30);
        LocalDateTime remindTime = LocalDateTime.of(2026, 10, 20, 9, 0);

        UserSchedule schedule = new UserSchedule();
        schedule.setUserId(1);
        schedule.setScheduleTime(scheduleTime);
        schedule.setRemindTime(remindTime);
        schedule.setTitle("季度复盘会");
        schedule.setIntro("与产品、研发同步 Q3 目标达成情况");
        userScheduleMapper.insert(schedule);

        assertNotNull(schedule.getId(), "自增主键应回填到实体");
        assertNotNull(schedule.getCreateTime(), "create_time 应由 MyMetaObjectHandler 自动填充");

        UserSchedule loaded = userScheduleMapper.selectById(schedule.getId());
        assertEquals(1, loaded.getUserId());
        assertEquals(scheduleTime, loaded.getScheduleTime());
        assertEquals(remindTime, loaded.getRemindTime());
        assertEquals("季度复盘会", loaded.getTitle());
        assertEquals("与产品、研发同步 Q3 目标达成情况", loaded.getIntro());
        System.out.println("[DOMAIN] user_schedule insert -> id=" + loaded.getId()
                + " scheduleTime=" + loaded.getScheduleTime() + " remindTime=" + loaded.getRemindTime());

        // 提醒时间为可选字段：不填时应为 null（不能落成 0 值或当前时间）
        UserSchedule noRemind = new UserSchedule();
        noRemind.setUserId(1);
        noRemind.setScheduleTime(scheduleTime);
        noRemind.setTitle("仅预约不提醒");
        userScheduleMapper.insert(noRemind);
        assertNull(userScheduleMapper.selectById(noRemind.getId()).getRemindTime(),
                "未设置 remind_time 时应为 null");
        System.out.println("[DOMAIN] user_schedule 可选 remind_time 留空 OK, id=" + noRemind.getId());
    }

    @Test
    void insertAnnouncementWorks() {
        // 草稿态：未发布，publish_time 应为 null、status 默认落到草稿
        Announcement draft = new Announcement();
        draft.setTitle("关于国庆假期值班安排的通知");
        draft.setContent("各部门请于 9 月 30 日前提交值班人员名单。");
        draft.setAuthor("张伟");
        draft.setStatus(AnnouncementStatusEnum.DRAFT.getValue());
        announcementMapper.insert(draft);

        assertNotNull(draft.getId(), "自增主键应回填到实体");
        assertNotNull(draft.getCreateTime(), "create_time 应由 MyMetaObjectHandler 自动填充");

        Announcement loadedDraft = announcementMapper.selectById(draft.getId());
        assertEquals("关于国庆假期值班安排的通知", loadedDraft.getTitle());
        assertEquals("各部门请于 9 月 30 日前提交值班人员名单。", loadedDraft.getContent());
        assertEquals("张伟", loadedDraft.getAuthor());
        assertEquals(AnnouncementStatusEnum.DRAFT.getValue(), loadedDraft.getStatus());
        assertNull(loadedDraft.getPublishTime(), "草稿态 publish_time 应为 null");
        assertNull(loadedDraft.getAttachment(), "未上传附件时 attachment 应为 null");
        System.out.println("[DOMAIN] announcement 草稿 -> id=" + loadedDraft.getId()
                + " status=" + loadedDraft.getStatus() + " publishTime=" + loadedDraft.getPublishTime());

        // 已发布态：publish_time 与 attachment 都应原样保存
        LocalDateTime publishTime = LocalDateTime.of(2026, 10, 14, 10, 0);
        Announcement published = new Announcement();
        published.setTitle("2026 年第三季度经营情况通报");
        published.setContent("详见附件。");
        published.setAttachment("/files/announcement/2026-q3-report.pdf");
        published.setAuthor("李娜");
        published.setStatus(AnnouncementStatusEnum.PUBLISHED.getValue());
        published.setPublishTime(publishTime);
        announcementMapper.insert(published);

        Announcement loadedPublished = announcementMapper.selectById(published.getId());
        assertEquals(AnnouncementStatusEnum.PUBLISHED.getValue(), loadedPublished.getStatus());
        assertEquals(publishTime, loadedPublished.getPublishTime());
        assertEquals("/files/announcement/2026-q3-report.pdf", loadedPublished.getAttachment());
        assertEquals("李娜", loadedPublished.getAuthor());
        System.out.println("[DOMAIN] announcement 已发布 -> id=" + loadedPublished.getId()
                + " publishTime=" + loadedPublished.getPublishTime()
                + " attachment=" + loadedPublished.getAttachment());
    }

    @Test
    void reservedWordTableWorks() {
        User user = new User();
        user.setUsername("domain_test_user");
        user.setMobile("13800000000");
        user.setStatus(1);
        userMapper.insert(user);
        assertNotNull(user.getId());

        User loaded = userMapper.selectById(user.getId());
        assertEquals("domain_test_user", loaded.getUsername());
        System.out.println("[DOMAIN] \"user\" 表读写正常, id=" + user.getId());
    }

}
