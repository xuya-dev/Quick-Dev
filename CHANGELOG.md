# Changelog

所有显著变更记录于此，格式：`<类型> <简短中文描述>`，新条目在前。

## 0.4.0 (2026-09-30)

- ✨ Features|新功能 排序支持多列orderBy与order逐列对应
- ✨ Features|新功能 新增TranslateSource SPI关联翻译优先内存取值不回源查库
- ✨ Features|新功能 新增DictJson标准JSON字典格式解析配合缓存全量替换
- 🐛 Bug Fixes|Bug 修复 数据权限空集合改为恒假条件避免空IN导致SQL错误
- 🐛 Bug Fixes|Bug 修复 排序跳过非表字段避免构造参数触发500
- 🐛 Bug Fixes|Bug 修复 防重被拒重试不再续期时间窗口
- 🐛 Bug Fixes|Bug 修复 Excel导入解析阶段强制行数上限防超大文件耗尽内存
- 🐛 Bug Fixes|Bug 修复 LIKE查询支持escapeWildcard按字面量匹配防通配符放大
- 🐛 Bug Fixes|Bug 修复 写路径强制清空逻辑删除与审计字段防越权删除与伪造归属
- 🔧 Chores|杂务 URL传token默认禁用避免泄露访问日志需时显式配置

## 0.3.0 (2026-09-30)

- ✨ Features|新功能 修改操作支持提交字段部分校验避免部分更新被整实体校验误伤
- ✨ Features|新功能 新增Create与Update校验分组按阶段自动应用
- ✨ Features|新功能 新增QuickRequire条件必填注解支持依赖字段判断与分组

## 0.2.0 (2026-09-30)

- ✨ Features|新功能 Translate新增APPEND模式保留原值并附加兄弟字段输出翻译
- ✨ Features|新功能 新增CrudHook生命周期钩子支持写流程聚合校验与缓存刷新
- ✨ Features|新功能 CRUD写操作统一纳入事务批量新增与删除具备原子性
- ✨ Features|新功能 新增quick-dev.crud全局默认includes与excludes配置
- ♻️ Refactoring|代码重构 移除AsyncOperationLogSink默认打印落库由用户自实现
- 🔧 Chores|杂务 接入CentralPortal发布插件GPG签名与发布手册支持deploy到中央仓库

## 0.1.0 (2026-09-29)

- ✨ Features|新功能 新增导出导入行数与IN条件防御上限及异常详情开关
- ✨ Features|新功能 新增内置字典管理接口写后自动刷新缓存
- ✨ Features|新功能 导出支持translate翻译为中文标签与导入反解闭环
- ✨ Features|新功能 字典改为全量内存缓存并提供带权限的刷新接口
- ✨ Features|新功能 新增内置数据库字典JdbcDictProvider零代码双向翻译
- ✨ Features|新功能 Excel导入支持字典标签反向转换由DictReverseResolver自主实现
- ✨ Features|新功能 新增DataScope行级数据权限按可见范围自动过滤
- ✨ Features|新功能 字段翻译新增枚举字典模式免建字典表
- ✨ Features|新功能 新增Translate字段翻译支持字典与关联表两种模式
- ✨ Features|新功能 新增createBy和updateBy操作人自动填充
- ✨ Features|新功能 新增Redis可选starter支持Sa-Token缓存与防重提交原子实现
- ✨ Features|新功能 新增QuickLog操作日志注解与OperationLogSink扩展
- ✨ Features|新功能 新增NoRepeatSubmit防重复提交注解
- ✨ Features|新功能 新增TREE树形查询接口并修复非表字段参与查询条件
- ✨ Features|新功能 新增saveBatch批量新增与导入模板下载接口
- ✨ Features|新功能 新增RequiresRole角色注解与Sa-Token角色桥接
- ✨ Features|新功能 新增QuickSave等五个方法级注解与Excel导入导出
- ✨ Features|新功能 内置Sa-Token自动接管登录态与权限校验
- ✨ Features|新功能 新增count统计接口与BETWEEN范围查询
- ✨ Features|新功能 新增createTime和updateTime自动填充
- ✨ Features|新功能 初始化quick-dev快速开发框架starter
- ♻️ Refactoring|代码重构 移除框架JDBC直查改为DictLoader接口与导入端点提供字典数据
- ✨ Features|新功能 字典数据源提供DictLoader接口支持用户自定义实现
- ✨ Features|新功能 新增字典定时刷新saveOrUpdate与操作日志查询端点
- ✨ Features|新功能 新增quick-dev-codegen代码生成器从表结构生成三件套
- ✨ Features|新功能 springdoc可选集成动态CRUD端点自动注入Swagger文档
- ✨ Features|新功能 操作日志支持async异步落地不影响业务请求
- 📝 Documentation|文档 新增docs目录包含使用手册与Agent使用说明
- 📝 Documentation|文档 完善开源内容并生成英文版README
- 🐛 Bug Fixes|Bug 修复 loginRequired为true且无权限码的Crud端点未要求登录
- ✅ Tests|测试 补充TreeBuilder与QuickMethodOps单元测试
- 🔧 Chores|杂务 新增GitHub Actions CI与release发布profile
