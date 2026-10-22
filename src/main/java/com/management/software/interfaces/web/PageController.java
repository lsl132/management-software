package com.management.software.interfaces.web;

import com.management.software.application.dto.CurrentUserVO;
import com.management.software.application.dto.NavGroupVO;
import com.management.software.application.service.NavigationService;
import com.management.software.application.service.OptionService;
import com.management.software.infrastructure.interceptor.LoginInterceptor;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 后台页面控制器（返回 Thymeleaf 视图名）。
 *
 * <p>按项目分层约定，所有返回 HTML 的 Controller 统一放在
 * {@code com.management.software.interfaces.web}；对外提供数据的 REST 接口放在
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
    private static final String VIEW_WORKFLOWS = "workflows";
    private static final String VIEW_ABOUT = "about";
    private static final String VIEW_PROFILE = "profile";
    private static final String VIEW_PASSWORD = "password";
    private static final String VIEW_NOTICES = "notices";

    private final OptionService optionService;
    private final NavigationService navigationService;
    private final Environment environment;

    @Value("${spring.application.name:backend-management}")
    private String applicationName;

    @Value("${server.port:10201}")
    private String serverPort;

    @Value("${spring.thymeleaf.cache:false}")
    private boolean thymeleafCache;

    public PageController(OptionService optionService,
                          NavigationService navigationService,
                          Environment environment) {
        this.optionService = optionService;
        this.navigationService = navigationService;
        this.environment = environment;
    }

    /**
     * 顶部二级导航的菜单数据（每个页面都要用，做成 {@code @ModelAttribute} 保证不漏挂）。
     *
     * <p>本控制器的每个页面处理方法执行前都会跑一遍；只在页面渲染时调用，不影响 REST 接口
     * （facade 层的接口是另一个控制器，不会带上这个模型属性）。
     */
    @ModelAttribute("navMenus")
    public List<NavGroupVO> navMenus() {
        return navigationService.menus();
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
     *
     * <p>币种字典供页面上的「薪酬」弹窗使用（该弹窗与员工表单是两个独立弹窗）。
     */
    @GetMapping("/employees")
    public String employees(HttpSession session, Model model) {
        model.addAttribute("active", "employees");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("departments", optionService.departments());
        model.addAttribute("genders", optionService.genders());
        model.addAttribute("educations", optionService.educations());
        model.addAttribute("employeeStatuses", optionService.employeeStatuses());
        model.addAttribute("currencies", optionService.currencies());
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

    /**
     * 流程管理页（权限字典 10004，即「审批规则」）。
     *
     * <p>一条规则 = 流程头 + 有序节点链，节点编排在同一弹窗里完成，不跳第二个页面。
     *
     * <p>服务端只下发**稳定字典**：流程状态（用于筛选与表单）。审批方式与审批模式体量小、
     * 且「直属上级」需要置灰标注，直接写在模板里；审批人（系统账号）与审批角色会随数据变化，
     * 角色走服务端渲染，用户列表由前端 ajax 调 {@code /api/options/users} 拉取。
     */
    @GetMapping("/workflows")
    public String workflows(HttpSession session, Model model) {
        model.addAttribute("active", "workflows");
        model.addAttribute("user", currentUser(session));
        model.addAttribute("workflowStatuses", optionService.workflowStatuses());
        model.addAttribute("roles", optionService.roles());
        return VIEW_WORKFLOWS;
    }

    /**
     * 个人信息页（导航「我的 → 个人信息」）。
     *
     * <p>页面内容全部由前端 ajax 调 {@code GET /api/profile} 拉取：显示名改完要立刻反映到右上角，
     * 若服务端渲染进模板，保存后还得整页刷新才能看到新名字。
     */
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        model.addAttribute("active", "profile");
        model.addAttribute("user", currentUser(session));
        return VIEW_PROFILE;
    }

    /**
     * 修改密码页（导航「我的 → 修改密码」）。
     *
     * <p>与个人信息共用 {@code /profile} 前缀，但 active 用独立取值 {@code password}：
     * 导航高亮是按 active 而不是路由首段判断的，否则这两项会同时点亮。
     */
    @GetMapping("/profile/password")
    public String password(HttpSession session, Model model) {
        model.addAttribute("active", "password");
        model.addAttribute("user", currentUser(session));
        return VIEW_PASSWORD;
    }

    /**
     * 公司公告页（导航「我的 → 公司公告」）。
     *
     * <p>与「公告管理」不是同一个页面：这里只读、只看已发布，是员工视角；
     * 公告管理是后台维护入口（草稿/发布/下线）。两者虽然读同一张表，但页面与接口都分开，
     * 免得把「草稿也能看」的能力带到员工入口上。
     */
    @GetMapping("/notices")
    public String notices(HttpSession session, Model model) {
        model.addAttribute("active", "notices");
        model.addAttribute("user", currentUser(session));
        return VIEW_NOTICES;
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

