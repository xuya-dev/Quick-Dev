# Changelog

所有显著变更记录于此，格式：`<类型> <简短中文描述>`，新条目在前。

## Unreleased

- 🐛 Bug Fixes|Bug 修复 操作日志不再采集明文令牌参数敏感键脱敏且跳过上传文件
- 🐛 Bug Fixes|Bug 修复 关联翻译反查改走分页插件方言不再硬编码limit语法
- 🐛 Bug Fixes|Bug 修复 翻译缓存超限先清过期条目再全清降低回源雪崩风险
- 🐛 Bug Fixes|Bug 修复 404与405交还Spring默认处理不再被兜底handler包装成业务错误
- 🐛 Bug Fixes|Bug 修复 全局异常处理器显式声明最低优先级用户Advice让位语义稳定
- 🧪 Tests|测试 补充登录负路径用例覆盖密码错误与停用账号分支
- 🐛 Bug Fixes|Bug 修复 Redis防重存储改为可选依赖无模板Bean时回落内存不再启动崩溃
- 🐛 Bug Fixes|Bug 修复 防重TTL下限钳制避免Redis对0与-1过期时间的报错与永久锁死
- 🐛 Bug Fixes|Bug 修复 翻译执行器静态注册改为无条件装配用户自定义Bean不再静默失效
- 🐛 Bug Fixes|Bug 修复 字典配置补servlet条件与属性注册非web应用不再启动失败
- 🐛 Bug Fixes|Bug 修复 字典内置翻译与反解按方向独立让位自定义单方向不再丢失另一方向
- 🐛 Bug Fixes|Bug 修复 字典懒加载双重检查防并发惊群JSON解析移出锁外
- 🐛 Bug Fixes|Bug 修复 防重指纹按条目过期时间淘汰不同接口窗口互不干扰
- ⚡ Performance|性能优化 实体元信息进程级缓存热路径不再重复全字段反射扫描
- ⚡ Performance|性能优化 springdoc补saveOrUpdate摘要与servlet条件修复WebFlux组合类加载
- 🔧 Chores|杂务 静态装配改为类型化SmartInitializingSingleton替代String类型Bean
- 🔧 Chores|杂务 Redis starter显式声明spring-data-redis依赖并修复demo登录常量时间比较
- ✨ Features|新功能 新增limits.query-max-rows护栏list与tree超限返回400不再全量返回
- ✨ Features|新功能 新增limits.export-batch-size导出分批取数内存占用与导出总量解耦
- ✨ Features|新功能 排序字段非法时返回400不再静默忽略便于定位排序不生效问题
- 🐛 Bug Fixes|Bug 修复 导出改为分批拉取修复全量物化后才截断的内存放大问题
- 🐛 Bug Fixes|Bug 修复 树形接口检测parentId环与自引用避免序列化无限递归
- 🐛 Bug Fixes|Bug 修复 树形孤儿节点按根返回不再从结果中静默消失
- 🐛 Bug Fixes|Bug 修复 鉴权拒绝时不再遗留用户身份到线程避免跨请求串号
- 🐛 Bug Fixes|Bug 修复 translate.cache-seconds=0按文档语义禁用缓存不再永久缓存
- 🐛 Bug Fixes|Bug 修复 Excel导入表头不匹配直接报错不再静默插入空记录
- 🐛 Bug Fixes|Bug 修复 Excel导入行数恰好等于上限不再被误拒并正确拒绝超限文件
- 🐛 Bug Fixes|Bug 修复 翻译反解返回类型固定为String消除随缓存状态变化的导入行为
- 🐛 Bug Fixes|Bug 修复 方法级注解写入路径补齐事务与类级CRUD语义一致
- 🐛 Bug Fixes|Bug 修复 校验器缺失时显式告警不再静默停用全部校验
- 🐛 Bug Fixes|Bug 修复 分页size非法值钳制不再透传负数到分页插件
- 🧪 Tests|测试 防御性上限测试改为进程内调整并还原移除demo中的测试专用配置
- 🔧 Chores|杂务 demo对starter的依赖改为${project.version}避免解析到历史发布版本

## 0.4.0 (2026-09-30)

- ✨ Features|新功能 唯一键冲突转友好400提示不透出SQL细节
- ✨ Features|新功能 Excel导入支持QuickRequire条件必填校验
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
