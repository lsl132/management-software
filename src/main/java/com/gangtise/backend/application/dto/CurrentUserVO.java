package com.gangtise.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户（同时作为会话中保存的对象）。
 *
 * <p>这里用普通类而非 record：模板（Thymeleaf/SpEL）只识别 {@code getXxx()} 形式的属性访问器。
 *
 * <p>{@code username} 与 {@code identifier} 的用途**不要混**：前者是展示名（可随时改成中文姓名，
 * 顶栏就是显示它），后者是 {@code user_account.identifier}（登录标识，唯一索引），
 * 凡是需要「记录是谁操作的」这类审计场景都用 {@code identifier}——
 * 展示名一改，历史记录就对不上人了。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserVO {

    private Integer userId;
    /** 登录标识，来自 {@code user_account.identifier}；审计一律用它。 */
    private String identifier;
    /** 展示名，来自 {@code user.username}；仅用于界面显示。 */
    private String username;
    private String roleName;

}
