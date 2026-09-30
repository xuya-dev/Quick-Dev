-- 测试隔离：每个用例执行前清空全部业务表，再由 @Sql 链式重放 data.sql
-- 顺序：先删引用方（perm/role），再删被引用方（user/dept）
delete from sys_user_perm;
delete from sys_user_role;
delete from sys_user;
delete from sys_dept;
delete from sys_dict;
delete from product;
delete from demo_goods;
