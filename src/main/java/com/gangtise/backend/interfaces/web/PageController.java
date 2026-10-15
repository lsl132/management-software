package com.gangtise.backend.interfaces.web;

import com.gangtise.backend.application.dto.CurrentUserVO;
import com.gangtise.backend.application.service.OptionService;
import com.gangtise.backend.infrastructure.interceptor.LoginInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 后台页面控制器（返回 Thymeleaf 视图名）。
 *
 * <p>按项目分层约定，所有返回 HTML 的 Controller 统一放在
 * {@code com.gangtise.backend.interfaces.web}；对外提供数据的 REST 接口放在
 * {@code interfaces.facade}。页面控制器只组装视图模型，不含业务逻辑。
 *
 * <p>除 {@code /login} 外的页面均由 {@link LoginInterceptor} 保护，未登录会被重定向到登录页。
 */
@Controller
public class PageController {

    private static final String VIEW_LOGIN = "login";
    private static final String VIEW_HOME = "home";
    private static final String VIEW_EMPLOYEES = "employees";
    private static final String VIEW_DEPARTMENTS = "departments";
    private static final String VIEW_ANNOUNCEMENTS = "announcements";
    private static final String VIEW_SCHEDULES = "schedules";
    private static final String VIEW_USERS = "users";
    private static final String VIEW_ROLES = "roles";
    private static final String VIEW_AUTHORITIES = "authorities";
    private static final String VIEW_ABOUT = "about";

    private final OptionService optionService;
    private final Environment environment;

    @Value("${spring.application.name:backend-management}")
    private String applicationName;

    @Value("${server.port:10201}")
    private String serverPort;

    @Value("${spring.thymeleaf.cache:false}")
    private boolean thymeleafCache;

    public PageController(OptionService optionService, Environment environment) {
        this.optionService = optionService;
        this.environment = environment;
    }

    /** 站点根路径统一引导到系统欢迎页。 */
    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    /** 登录页；已登录则直接进入欢迎页。 */
    @GetMapping("/login")
    public String login(HttpSession session) {
        return currentUser(session) == null ? VIEW_LOGIN : "redirect:/home";
    }

    /**
     * 个人工作台（欢迎页）。
     *
     * <p>只渲染与登录人相关的固定内容（用户名、角色、今天日期），
     * 登录趋势 / 日历 / 公告三块数据由前端 ajax 拉取。
     */
    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        model.addAttribute("active", "home");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("today", LocalDate.now());
        return VIEW_HOME;
    }

    /**
     * 员工管理页。
     *
     * <p>下拉框选项中，部门/性别/学历/状态为固定字典，服务端直接渲染；
     * 职级隶属于部门，需随所选部门变化，改由前端 ajax 调 {@code /api/options/ranks} 动态加载。
     */
    @GetMapping("/employees")
    public String employees(HttpSession session, Model model) {
        model.addAttribute("active", "employees");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("departments", optionService.departments());
        model.addAttribute("genders", optionService.genders());
        model.addAttribute("educations", optionService.educations());
        model.addAttribute("employeeStatuses", optionService.employeeStatuses());
        return VIEW_EMPLOYEES;
    }

    /**
     * 部门管理页。
     *
     * <p>部门与部门职级共用该页面：上级部门下拉、职级序列下拉为固定字典，服务端直接渲染；
     * 职级隶属于部门，部门下的职级列表由前端 ajax 调
     * {@code /api/departments/{departmentId}/ranks} 动态加载。
     */
    @GetMapping("/departments")
    public String departments(HttpSession session, Model model) {
        model.addAttribute("active", "departments");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("departments", optionService.departments());
        model.addAttribute("rankTypes", optionService.rankTypes());
        return VIEW_DEPARTMENTS;
    }

    /** 公告管理页：状态为固定字典，服务端渲染筛选下拉。 */
    @GetMapping("/announcements")
    public String announcements(HttpSession session, Model model) {
        model.addAttribute("active", "announcements");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("announcementStatuses", optionService.announcementStatuses());
        return VIEW_ANNOUNCEMENTS;
    }

    /** 日程管理页：数据按月查询，月份由前端维护，无需服务端下发字典。 */
    @GetMapping("/schedules")
    public String schedules(HttpSession session, Model model) {
        model.addAttribute("active", "schedules");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("today", LocalDate.now());
        return VIEW_SCHEDULES;
    }

    /**
     * 用户管理页（系统账号）。
     *
     * <p>角色与状态为固定字典，服务端直接渲染筛选与表单下拉。
     * 密码不由页面填写：新用户与重置密码统一使用系统初始密码，由服务端散列写入。
     */
    @GetMapping("/users")
    public String users(HttpSession session, Model model) {
        model.addAttribute("active", "users");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("roles", optionService.roles());
        model.addAttribute("userStatuses", optionService.userStatuses());
        return VIEW_USERS;
    }

    /**
     * 角色管理页。
     *
     * <p>权限树由前端 ajax 调 {@code /api/authorities} 动态加载（结构调整无需改模板）。
     */
    @GetMapping("/roles")
    public String roles(HttpSession session, Model model) {
        model.addAttribute("active", "roles");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("roleStatuses", optionService.roleStatuses());
        return VIEW_ROLES;
    }

    /**
     * 权限管理页。
     *
     * <p>权限树由前端 ajax 调 {@code /api/authorities} 动态加载（与角色授权弹窗同一份数据）；
     * 类型下拉为固定字典，服务端直接渲染。
     */
    @GetMapping("/authorities")
    public String authorities(HttpSession session, Model model) {
        model.addAttribute("active", "authorities");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("authorityTypes", optionService.authorityTypes());
        return VIEW_AUTHORITIES;
    }

    /** 运行信息页。 */
    @GetMapping("/about")
    public String about(HttpSession session, Model model) {
        String[] activeProfiles = environment.getActiveProfiles();

        model.addAttribute("active", "about");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("appName", applicationName);
        model.addAttribute("profiles", activeProfiles.length == 0
                ? "default"
                : String.join(", ", Arrays.asList(activeProfiles)));
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name"));
        model.addAttribute("serverPort", serverPort);
        model.addAttribute("serverTime", LocalDateTime.now());
        model.addAttribute("thymeleafCache", thymeleafCache);
        return VIEW_ABOUT;
    }

    private CurrentUserVO currentUser(HttpSession session) {
        if (session == null) {
            return null;
        }
        return (CurrentUserVO) session.getAttribute(LoginInterceptor.SESSION_USER);
    }

}

