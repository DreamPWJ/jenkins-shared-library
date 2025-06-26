# 数据库表设计常用字段

create table base_common_fields
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    user_id     bigint                               null comment '用户id',
    image_url   varchar(255)                         null comment '图片url',
    lon         decimal(10, 6)                       null comment '经度',
    lat         decimal(10, 6)                       null comment '纬度',
    amount      decimal(12, 2)                       null comment '金额 单位: 元',

    is_online   tinyint(1) default 0                 null comment '是否在线 0.否 1.是',

    remark      varchar(255)                         null comment '备注',
    show_order  int        default 1                 null comment '显示排序号 数字越大优先级越高',
    version     int        default 1                 null comment '数据版本',
    is_enabled  tinyint(1) default 1                 null comment '是否启用 0.禁用 1.启用',
    is_deleted  tinyint(1) default 0                 null comment '是否删除 0.未删除 1.已删除',
    modify_by   bigint                               null comment '修改人',
    modify_time datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by   bigint                               null comment '创建人',
    create_time datetime   default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '通用表设计字段表';

#  创建索引
/*
create index table_name_column_name_index
    on table_name (column_name);
*/
