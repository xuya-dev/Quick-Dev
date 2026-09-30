-- 账号：admin/admin123（超级权限 *）、viewer/viewer123（只读 sys:user:list/detail）
-- disabled/disabled123：停用账号（status=0，财务部），用于验证"账号已被停用"负路径
insert into sys_user (username, nickname, password, email, status, dept_id, create_time)
values ('admin', '管理员', 'admin123', 'admin@quickdev.cn', 1, 2, current_timestamp),
       ('viewer', '访客', 'viewer123', 'viewer@quickdev.cn', 1, 2, current_timestamp),
       ('alice', '爱丽丝', 'alice123', 'alice@quickdev.cn', 1, 5, current_timestamp),
       ('disabled', '停用账号', 'disabled123', 'disabled@quickdev.cn', 0, 5, current_timestamp);

insert into sys_user_perm (user_id, perm_code)
values (1, '*'),
       (2, 'sys:user:list'),
       (2, 'sys:user:detail');

-- 角色：admin 拥有 admin 角色，viewer 无角色
insert into sys_user_role (user_id, role_code)
values (1, 'admin');

-- 部门树：总公司(1) -> 研发部(2) -> 前端组(3)/后端组(4)；总公司 -> 财务部(5)
insert into sys_dept (id, name, parent_id, create_time)
values (1, '总公司', 0, current_timestamp),
       (2, '研发部', 1, current_timestamp),
       (3, '前端组', 2, current_timestamp),
       (4, '后端组', 2, current_timestamp),
       (5, '财务部', 1, current_timestamp);

-- 数据字典
insert into sys_dict (dict_type, dict_value, dict_label)
values ('user_status', '1', '启用'),
       ('product_channel', '1', '线上'),
       ('product_channel', '2', '线下'),
       ('user_status', '0', '停用');

insert into product (id, name, type, channel, price, stock, create_time)
values ('p0000000000000000000000000000001', '机械键盘', 1, 1, 399.00, 120, current_timestamp),
       ('p0000000000000000000000000000002', '无线鼠标', 2, 2, 129.50, 300, current_timestamp);

insert into demo_goods (name, remark, stock, reason, status, create_time) values
       ('演示商品A', '库存充足', 100, null, 1, current_timestamp),
       ('演示商品B', null,       0, '清仓',   0, current_timestamp);
