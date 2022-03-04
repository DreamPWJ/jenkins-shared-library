# 数据库表设计 本文件不要提交到远程仓库 只用于设计编写!!!
use property_sales_design;
show tables;

drop table if exists base_table;
create table base_table
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,

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
    comment '(根据具体设计删减字段!!!)通用基础表';


###  表设计开始
