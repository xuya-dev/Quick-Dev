# Changelog

所有显著变更记录于此，格式：`<类型> <简短中文描述>`，新条目在前。

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
- 📝 Documentation|文档 完善开源内容并生成英文版README
- 🐛 Bug Fixes|Bug 修复 loginRequired为true且无权限码的Crud端点未要求登录
- ✅ Tests|测试 补充TreeBuilder与QuickMethodOps单元测试
- 🔧 Chores|杂务 新增GitHub Actions CI与release发布profile
