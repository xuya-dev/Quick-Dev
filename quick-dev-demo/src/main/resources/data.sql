-- 账号：admin/admin123（超级权限 *）、viewer/viewer123（只读 sys:user:list/detail）
insert into sys_user (username, nickname, password, email, status, create_time)
values ('admin', '管理员', 'admin123', 'admin@quickdev.cn', 1, current_timestamp),
       ('viewer', '访客', 'viewer123', 'viewer@quickdev.cn', 1, current_timestamp),
       ('alice', '爱丽丝', 'alice123', 'alice@quickdev.cn', 1, current_timestamp);

insert into sys_user_perm (user_id, perm_code)
values (1, '*'),
       (2, 'sys:user:list'),
       (2, 'sys:user:detail');

insert into product (id, name, price, stock, create_time)
values ('p0000000000000000000000000001', '机械键盘', 399.00, 120, current_timestamp),
       ('p0000000000000000000000000002', '无线鼠标', 129.50, 300, current_timestamp);
