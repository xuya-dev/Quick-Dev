name: 功能建议
description: 提出新功能或改进建议
labels: [ enhancement ]
body:

- type: textarea
  id: problem
  attributes:
  label: 你的使用场景
  description: 这个功能解决了你的什么问题？
  validations:
  required: true
- type: textarea
  id: proposal
  attributes:
  label: 期望的方案
  description: 期望的 API / 注解形式，最好附带示例代码
  validations:
  required: true
- type: textarea
  id: alternatives
  attributes:
  label: 考虑过的替代方案
