package com.management.software.domain.aggregate;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 权限
 */
@Data
@TableName("authority")
public class Authority implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    //权限助记符
    private Integer mnemonic;
    //权限名称
    private String authName;
    //权限介绍说明：可空，保存时留空即清空（ALWAYS 策略）
    @TableField(value = "description", updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    //权限类型 1 模块分组（菜单）2 功能页，见 AuthorityTypeEnum
    private Integer type;
    //功能页路由（如 /employees），二级导航的跳转地址；为空表示该功能页尚未实现，菜单里置灰。
    //模块分组是下拉容器，不设路由。ALWAYS 策略：表单留空即清空，不会因为 null 被跳过而永远删不掉
    @TableField(value = "path", updateStrategy = FieldStrategy.ALWAYS)
    private String path;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;


}
