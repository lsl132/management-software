package com.gangtise.backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户（同时作为会话中保存的对象）。
 *
 * <p>这里用普通类而非 record：模板（Thymeleaf/SpEL）只识别 {@code getXxx()} 形式的属性访问器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserVO {

    private Integer userId;
    private String username;
    private String roleName;

}
