package com.management.software.domain.aggregate.entity;

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
 * 公告（标题 / 内容 / 附件 / 状态 / 发布时间 / 作者）
 *
 * <p>{@code attachment} 用 {@code updateStrategy = ALWAYS}：编辑时传 null 即「移除附件」。
 * {@code publishTime} 保持默认策略（null 不参与更新），因此首次发布的时间不会被后续编辑抹掉。
 */
@Data
@TableName("announcement")
public class Announcement implements Serializable {

    private static final long serialVersionUID = 1L;

    //自增id
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    //标题
    private String title;
    //内容
    private String content;
    //附件（存文件路径/URL，为空表示无附件）
    @TableField(value = "attachment", updateStrategy = FieldStrategy.ALWAYS)
    private String attachment;
    //状态 见 AnnouncementStatusEnum（1 草稿 2 已发布 3 已下线）
    private Integer status;
    //发布时间（草稿态为空，发布时写入）
    private LocalDateTime publishTime;
    //创建时间
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    //作者（存作者姓名的快照，人员改名不影响历史公告）
    private String author;

}

