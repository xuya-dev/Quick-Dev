name: Bug 报告
description: 报告一个可复现的问题
labels: [ bug ]
body:

- type: textarea
  id: description
  attributes:
  label: 问题描述
  description: 简明扼要地说明遇到了什么问题
  validations:
  required: true
- type: textarea
  id: reproduce
  attributes:
  label: 复现步骤
  placeholder: |
  1. 引入依赖 ...
  2. 调用接口 ...
  3. 出现 ...
  validations:
  required: true
- type: textarea
  id: expectation
  attributes:
  label: 期望行为
  validations:
  required: true
- type: textarea
  id: environment
  attributes:
  label: 环境信息
  placeholder: |
  JDK 版本：
  Spring Boot 版本：
  MyBatis-Plus 版本：
  quick-dev 版本：
  数据库类型：
- type: textarea
  id: logs
  attributes:
  label: 相关日志 / 截图
