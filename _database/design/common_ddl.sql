create table if not exists base_area
(
    id          bigint auto_increment comment '主键id'
        primary key,
    parent_code varchar(50)                        null comment '父级code',
    code        varchar(50)                        null comment '编码',
    name        varchar(50)                        null comment '名称',
    name_path   varchar(100)                       null comment '名称路径',
    level       varchar(20)                        null comment '等级 国家 省 市 区/县',
    center      varchar(30)                        null comment '经度,纬度',
    show_order  int      default 1                 null comment '显示排序号 数字越小优先级越高',
    modify_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '区域表';

create index base_area_code_index
    on base_area (code);

create index base_area_parent_code_index
    on base_area (parent_code);

create index base_area_show_order_index
    on base_area (show_order);

create table if not exists base_error_log
(
    id           bigint unsigned auto_increment comment '主键id'
        primary key,
    type         varchar(50)                        null comment '类型',
    project_name varchar(50)                        null comment '项目名称',
    environment  varchar(50)                        null comment '运行环境',
    error_code   varchar(10)                        null comment '错误码',
    content      text                               not null comment '日志异常信息',
    platform     varchar(50)                        null comment '运行平台',
    create_by    bigint                             null comment '创建人',
    create_time  datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '异常日志表';

create index base_error_log_error_code_index
    on base_error_log (error_code);

create table if not exists base_pref
(
    id           bigint unsigned auto_increment comment '主键'
        primary key,
    type         tinyint                            null comment '类型 1.api 2.sql',
    project_name varchar(50)                        null comment '项目名称',
    environment  varchar(20)                        null comment '运行环境',
    method_type  varchar(10)                        null comment '方法类型',
    api_path     varchar(255)                       null comment 'API路径',
    execute_sql  varchar(1000)                      null comment '执行sql',
    execute_time int                                null comment '执行时间 单位ms',
    platform     varchar(30)                        null comment '运行平台',
    create_time  datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '性能表';

create index base_pref_api_path_index
    on base_pref (api_path);

create table if not exists push_msg_job
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    type        tinyint                              null comment '消息类型 1: 报警通知 2: 预警通知 3: 普通通知',
    template_id varchar(100)                         null comment '微信消息模板id',
    content     varchar(1000)                        null comment '消息内容',
    send_status tinyint    default 1                 null comment '发送状态 1: 待发送 2: 发送中 3: 发送完成',
    send_num    int                                  null comment '发送数',
    success_num int        default 0                 null comment '发送成功数',
    begin_date  datetime                             null comment '开始时间',
    end_date    datetime                             null comment '结束时间',
    is_deleted  tinyint(1) default 0                 null comment '是否删除 0: 未删除 1: 已删除',
    create_time datetime   default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '推送消息任务表';

create table if not exists push_msg_log
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    job_id      bigint                             null comment '消息任务id',
    open_id     varchar(100)                       null comment '用户微信openid',
    send_status tinyint  default 0                 null comment '发送状态 0: 发送失败 1: 发送成功',
    error_msg   varchar(255)                       null comment '失败原因',
    send_date   datetime                           null comment '发送时间',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '推送消息日志表';

create table if not exists push_sms_job
(
    id             bigint unsigned auto_increment comment '主键'
        primary key,
    sign_name      varchar(20)                          null comment '短信签名',
    template_code  varchar(20)                          null comment '短信模板',
    template_param varchar(255)                         null comment '短信模板变量',
    send_status    tinyint    default 1                 null comment '发送状态 1: 待发送 2: 发送中 3: 发送完成',
    send_num       int                                  null comment '发送数',
    success_num    int        default 0                 null comment '发送成功数',
    begin_date     datetime                             null comment '开始时间',
    end_date       datetime                             null comment '结束时间',
    is_deleted     tinyint(1) default 0                 null comment '是否删除 0: 未删除 1: 已删除',
    create_time    datetime   default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '推送短信任务表';

create table if not exists push_sms_log
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    job_id      bigint                             null comment '短信任务id',
    phone       varchar(20)                        null comment '手机号',
    send_status tinyint  default 0                 null comment '发送状态 0: 发送失败 1: 发送成功',
    error_msg   varchar(255)                       null comment '失败原因',
    send_date   datetime                           null comment '发送时间',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '推送短信日志表';

create table if not exists sys_business
(
    id                bigint unsigned auto_increment comment '主键'
        primary key,
    parent_code       varchar(30)                          null comment '父级code',
    code              varchar(50)                          not null comment '业务编号',
    name              varchar(100)                         not null comment '业务名称',
    icon              varchar(100)                         null comment '菜单图标',
    vr_path           varchar(100)                         null comment '前端路径',
    vr_name           varchar(100)                         null comment '前端router name',
    vr_component_path varchar(100)                         null comment '前端组件包路径',
    vr_redirect       varchar(100)                         null comment '前端重定向地址',
    is_cache          tinyint(1) default 0                 null comment '是否缓存 0: 不缓存 1: 缓存',
    is_show           tinyint(1) default 1                 null comment '是否在菜单中显示 0: 不显示 1: 显示',
    show_order        int        default 1                 null comment '显示排序号 数字越大优先级越小',
    modify_by         bigint                               null comment '修改人',
    modify_time       datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by         bigint                               null comment '创建人',
    create_time       datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sys_business_code__uindex
        unique (code)
)
    comment '系统业务菜单表';

create index sys_business_is_show_index
    on sys_business (is_show);

create index sys_business_parent_code_index
    on sys_business (parent_code);

create index sys_business_show_order_index
    on sys_business (show_order);

create table if not exists sys_business_operate
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    business_id bigint                             not null comment '业务id',
    name        varchar(100)                       not null comment '操作名称',
    operate_key varchar(100)                       null comment '操作key 前端视图元素显示隐藏控制',
    modify_by   bigint                             null comment '修改人',
    modify_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by   bigint                             null comment '创建人',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sys_business_operate_operate_key_uindex
        unique (operate_key)
)
    comment '系统业务操作表';

create index sys_business_operate_business_id_index
    on sys_business_operate (business_id);

create table if not exists sys_business_operate_resource
(
    id                  bigint unsigned auto_increment comment '主键'
        primary key,
    business_operate_id bigint                             not null comment '业务操作id',
    operate_path        varchar(200)                       not null comment '操作路径',
    operate_method      varchar(20)                        not null comment 'url的请求方法 类型: GET POST PUT DELETE ALL',
    modify_by           bigint                             null comment '修改人',
    modify_time         datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by           bigint                             null comment '创建人',
    create_time         datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '系统业务操作资源表';

create index sys_business_operate_resource_business_operate_id_index
    on sys_business_operate_resource (business_operate_id);

create table if not exists sys_dict
(
    id           bigint unsigned auto_increment comment '主键'
        primary key,
    type_code    varchar(50)                          not null comment '类型编号',
    code         varchar(50)                          not null comment '字典码',
    value        varchar(50)                          not null comment '字典值',
    extend_value varchar(255)                         null comment '扩展值',
    remark       varchar(200)                         null comment '备注',
    show_order   int        default 1                 null comment '显示排序号 数字越小优先级越高',
    is_enabled   tinyint(1) default 1                 null comment '是否启用 0: 禁用 1: 启用',
    modify_by    bigint                               null comment '修改人',
    modify_time  datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by    bigint                               null comment '创建人',
    create_time  datetime   default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '系统字典表';

create index sys_dict_is_enabled_index
    on sys_dict (is_enabled);

create index sys_dict_show_order_index
    on sys_dict (show_order);

create index sys_dict_type_code_index
    on sys_dict (type_code);

create table if not exists sys_dict_type
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    type        tinyint                            null comment '类型 1: 系统默认 2: 自定义',
    code        varchar(50)                        not null comment '编号',
    value       varchar(50)                        not null comment '类型值',
    remark      varchar(2000)                      null comment '备注',
    show_order  int      default 1                 null comment '显示排序号 数字越小优先级越高',
    modify_by   bigint                             null comment '修改人',
    modify_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by   bigint                             null comment '创建人',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sys_dict_type_code__uindex
        unique (code)
)
    comment '系统字典类型表';

create index sys_dict_type_show_order_index
    on sys_dict_type (show_order);

create table if not exists sys_message
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    type        tinyint                            null comment '1. 用户小程序 2. 管理小程序 3. Web管理平台',
    user_ids    varchar(500)                       not null comment '关联用户id 多个用逗号,分割',
    title       varchar(30)                        null comment '标题',
    content     varchar(255)                       not null comment '内容',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '系统消息表';

create index sys_message_type_index
    on sys_message (type);

create index sys_message_user_ids_index
    on sys_message (user_ids);

create table if not exists sys_message_view_record
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    message_id  bigint                             null comment '消息id',
    user_id     bigint                             null comment '用户id',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '系统消息查看记录表';

create index sys_message_view_record_message_id_index
    on sys_message_view_record (message_id);

create index sys_message_view_record_user_id_index
    on sys_message_view_record (user_id);

create table if not exists sys_param
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    type        tinyint                              null comment '类型 : 1 整型 2 字符串  3 富文本 4 图片 5 文件',
    code        varchar(50)                          not null comment '编号',
    name        varchar(50)                          not null comment '参数名',
    value       varchar(6000)                        not null comment '参数值',
    remark      varchar(200)                         null comment '备注',
    show_order  int        default 1                 null comment '显示排序号 数字越大优先级越高',
    is_deleted  tinyint(1) default 0                 null comment '是否删除 0: 未删除 1: 已删除',
    modify_by   bigint                               null comment '修改人',
    modify_time datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by   bigint                               null comment '创建人',
    create_time datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sys_param_code__uindex
        unique (code)
)
    comment '系统配置参数表';

create index sys_param_is_deleted_index
    on sys_param (is_deleted);

create table if not exists sys_role
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    code        varchar(30)                          not null comment '角色编号',
    name        varchar(50)                          not null comment '角色名称',
    is_enabled  tinyint(1) default 1                 null comment '是否启用 0: 禁用 1: 启用',
    remark      varchar(500)                         null comment '备注',
    modify_by   bigint                               null comment '修改人',
    modify_time datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by   bigint                               null comment '创建人',
    create_time datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sys_role_code__uindex
        unique (code)
)
    comment '系统角色表';

create index sys_role_is_enabled_index
    on sys_role (is_enabled);

create table if not exists sys_role_operate
(
    id                  bigint unsigned auto_increment comment '主键'
        primary key,
    role_id             bigint                             null comment '关联角色',
    business_operate_id bigint                             null comment '关联业务操作',
    create_time         datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '角色操作表';

create index sys_role_operate_business_operate_id_index
    on sys_role_operate (business_operate_id);

create index sys_role_operate_role_id_index
    on sys_role_operate (role_id);

create table if not exists sys_user
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    user_name   varchar(50)                          not null comment '用户名',
    password    varchar(200)                         not null comment '密码',
    real_name   varchar(50)                          null comment '真实姓名',
    avatar_url  varchar(200)                         null comment '头像url',
    phone       varchar(20)                          null comment '手机号',
    email       varchar(64)                          null comment '邮箱',
    sex         tinyint    default 0                 null comment '性别 0: 未知 1: 男 2: 女',
    is_god      tinyint(1) default 0                 null comment '是否上帝用户 0: 否 1: 是',
    is_enabled  tinyint(1) default 1                 null comment '是否启用 0: 禁用 1: 启用',
    modify_by   bigint                               null comment '修改人',
    modify_time datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_by   bigint                               null comment '创建人',
    create_time datetime   default CURRENT_TIMESTAMP null comment '创建时间',
    constraint sys_user_phone_uindex
        unique (phone)
)
    comment '系统用户表';

create index sys_user_is_enabled_index
    on sys_user (is_enabled);

create index sys_user_user_name_index
    on sys_user (user_name);

create table if not exists sys_user_role
(
    id          bigint unsigned auto_increment comment '主键'
        primary key,
    user_id     bigint                             not null comment '用户id',
    role_id     bigint                             not null comment '角色id',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '用户角色表';

create index sys_user_role_role_id_index
    on sys_user_role (role_id);

create index sys_user_role_user_id_index
    on sys_user_role (user_id);


create table base_app_version
(
    id          bigint auto_increment comment '主键'
        primary key,
    app_type    tinyint                              null comment 'APP类型 1.用户端 2.管理端',
    client_type tinyint                              not null comment '客户端类型 1.android 2.ios',
    version_num varchar(20)                          not null comment '版本号x.y.z',
    content     varchar(600)                         null comment '更新内容',
    url         varchar(300)                         null comment '下载URL',
    is_force    tinyint(1) default 0                 null comment '是否强制更新 0.不强制 1.强制',
    is_release  tinyint(1) default 1                 null comment '是否发布 0.不发布 1.发布',
    modify_time datetime   default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '修改时间',
    create_time datetime   default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment 'APP版本表';

create table shedlock
(
    name        varchar(64)                         not null comment '定时任务唯一名称 主键'
        primary key,
    lock_until  timestamp(3)                        null comment '锁直到...时间结束',
    locked_at   timestamp(3)                        null comment '锁在时间',
    locked_by   varchar(255)                        not null comment '锁定冻结人',
    create_time timestamp default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '分布式定时任务锁表';
