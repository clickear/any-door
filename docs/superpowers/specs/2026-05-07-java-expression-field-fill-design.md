# AnyDoor 参数窗口 Java 表达式字段回填设计

## 背景

AnyDoor 插件当前的参数填写窗口以 JSON 文本编辑为主，支持直接编辑整份请求参数，但不支持在项目运行上下文中，通过 Java 表达式为某个字段计算值并回填。

用户需求是：

- 在“填写参数窗口”内直接使用类似 IDEA Evaluate Expression 的表达式编辑体验
- 不需要手动先打断点
- 表达式运行在项目运行上下文中，可以访问项目类与 Spring Bean
- 表达式结果只回填当前字段，不覆盖整份参数
- 目标字段定位规则是：树优先，JSON 光标兜底

当前仓库中已经有两块可复用基础：

- 编辑器体验：`MyCodeFragmentInputComponent` / `MyXDebuggerExpressionEditor`
- 项目环境执行思路：预执行代码链路 `PreRunDialogWrapper` / `VmUtil` / `AnyDoorService`

本设计的目标是复用前者的编辑体验，并借鉴后者的运行上下文执行方式，新增“Java 表达式字段回填”能力。

## 目标

- 在参数填写窗口中新增 `Java 表达式` 固定面板
- 允许用户为“当前字段”编写 Java 表达式
- 表达式在目标项目运行上下文中执行
- 表达式返回值只更新当前字段对应的 JSON 值
- 同一个方法的同一个字段支持最近表达式缓存
- 第二阶段支持字段树优先和右键弹窗入口

## 非目标

- 不接入真正依赖调试挂起栈帧的 IDEA 原生 Evaluate 执行机制
- 不在第一阶段支持批量更新多个字段
- 不在第一阶段支持表达式模板管理、历史列表、执行结果预览
- 不允许表达式直接接管整份请求 JSON 的修改逻辑

## 方案选型

### 方案一：独立弹窗

在参数窗口里点击按钮后弹出单独的表达式窗口，执行后回填字段。

优点：

- 改动范围小
- 复用现有 `PreRunDialogWrapper` 入口思路简单

缺点：

- 与主参数编辑体验割裂
- 连续调试表达式效率低

### 方案二：只做固定面板

在主窗口中直接增加一个 `Java 表达式` 固定区域。

优点：

- 主流程顺畅
- 与参数编辑联动更自然

缺点：

- 缺少从字段节点直接进入的快捷入口

### 方案三：固定面板 + 右键弹窗，两阶段实现（推荐）

Phase 1 先做固定面板，完成表达式执行与字段回填主链路。  
Phase 2 再补字段树优先与右键弹窗。

优点：

- 先交付核心能力，风险可控
- 后续补入口时不需要重做执行链

缺点：

- 需要一开始就定义清晰的组件边界

结论：

- 采用方案三
- 第一阶段以 `JSON 光标路径` 为主
- 第二阶段补 `字段树优先 + 右键入口`

## 用户体验

### Phase 1

用户流程：

1. 在参数 JSON 中把光标放在某个字段上
2. 底部 `Java 表达式` 面板显示当前字段路径与字段类型
3. 用户输入 Java 表达式
4. 点击 `应用到当前字段`
5. 插件在项目运行上下文执行表达式
6. 成功后，仅更新当前字段值
7. 失败时提示错误，原 JSON 保持不变

### Phase 2

新增交互：

- 参数字段树存在有效选中节点时，优先使用树节点目标
- 为字段树节点增加右键菜单：`Java 表达式`
- 右键弹窗复用固定面板内部的表达式编辑组件与执行服务

## 字段定位规则

目标字段定位规则固定为：

1. 如果存在有效字段树节点选中，使用树节点
2. 否则使用 JSON 编辑器当前光标路径
3. 如果两者都无法识别唯一字段，禁用执行

### JSON 光标定位规则

- 光标在字段 value 内：定位当前字段
- 光标在字段名上：定位当前字段
- 光标在对象或数组边界：尝试定位最近父字段
- 光标位于空白、根节点、或歧义位置：视为无法定位

## 表达式语义

表达式契约如下：

- 输入是“当前字段上下文”
- 输出必须是“当前字段的新值”
- 插件负责把返回值回填到字段路径
- 用户不负责手动修改整份 JSON

示例：

方法参数 JSON：

```json
{
  "user": {
    "address": {
      "city": "Beijing"
    }
  }
}
```

如果当前字段是 `user.address.city`，则表达式返回值应当是 `String`。  
成功执行后，只更新 `city` 字段，不改 `user` 下其他内容。

## 执行上下文

表达式不依赖真实调试器断点帧，而是在插件主动建立的项目运行上下文中执行。

该上下文应支持：

- 当前项目 classpath
- 当前方法所属项目运行环境
- Spring Bean 访问能力
- 目标字段的 Java 类型信息
- 当前字段值访问

建议对表达式暴露的最小上下文能力：

- `bean(Class<T>)`
- `bean(String)`
- `currentValue`
- `targetType`
- `rootParamName`

表达式示例：

```java
bean(UserService.class).findById(1L).getAddress().getCity()
```

或：

```java
currentValue == null ? "Shanghai" : currentValue
```

## 架构设计

### 1. FieldSelectionResolver

职责：

- 解析字段树当前选中节点
- 解析 JSON 光标当前字段路径
- 输出统一的 `FieldTarget`

### 2. ExpressionEditorPanel

职责：

- 复用 Evaluate 风格表达式编辑器
- 显示当前字段目标
- 提供 `应用到当前字段` / `清空`
- 恢复和保存表达式缓存

该组件同时服务于固定面板和右键弹窗。

### 3. ExpressionExecutionService

职责：

- 接收 `FieldTarget + expression + method context`
- 在项目运行上下文执行表达式
- 返回执行结果

该服务不直接操作 UI，也不直接更新 JSON。

### 4. JsonFieldPatchService

职责：

- 把执行结果转换为字段 JSON 值
- 定位并替换目标字段路径
- 返回完整 JSON 文本

### 5. ExpressionHistoryCache

职责：

- 按 `methodKey + fieldPath` 缓存最近一次表达式
- 面板切换字段时恢复表达式

## 数据模型

### FieldTarget

```java
class FieldTarget {
    String rootParamName;
    String jsonPath;
    String displayPath;
    String declaredType;
    String currentJsonValue;
    TargetSource source;
}
```

### ExpressionRequest

```java
class ExpressionRequest {
    String methodKey;
    FieldTarget target;
    String expression;
    String fullJsonText;
}
```

### ExpressionResult

```java
class ExpressionResult {
    Object value;
    String valueJson;
}
```

### FieldExpressionCacheKey

```java
class FieldExpressionCacheKey {
    String methodKey;
    String jsonPath;
}
```

## 事件流

### 字段切换

- 树节点变化或光标变化
- 重新解析 `FieldTarget`
- 更新表达式面板当前目标
- 恢复对应字段缓存表达式

### 应用表达式

- 面板收集当前 `FieldTarget`
- 构造 `ExpressionRequest`
- 调用 `ExpressionExecutionService`
- 成功后交给 `JsonFieldPatchService`
- 回写 JSON 编辑器
- 更新缓存

### 右键弹窗（Phase 2）

- 右键节点生成固定 `FieldTarget`
- 打开弹窗表达式面板
- 执行结果同样回填主编辑器
- 面板和弹窗共享服务层

## UI 状态

表达式面板需要支持以下状态：

- `NO_TARGET`
- `READY`
- `RUNNING`
- `ERROR`
- `APPLIED`

行为要求：

- `NO_TARGET` 时禁用执行按钮
- `RUNNING` 时防重复提交
- `ERROR` 时保留表达式内容
- `APPLIED` 时显示成功回填路径

## 错误处理

### 无法识别目标字段

- 提示先选择字段或移动光标到字段位置
- 禁用执行

### 字段类型无法解析

- 执行前拦截
- 不修改 JSON

### 表达式执行失败

- 保留表达式内容
- 提示错误信息
- 不修改 JSON

### 返回值类型不兼容

- 提示“返回值与目标字段类型不兼容”
- 不修改 JSON

### 序列化或回填失败

- 提示失败原因
- 不修改 JSON

核心原则：

- 失败只影响本次表达式应用
- 不污染当前 JSON
- 不关闭参数窗口

## 缓存策略

第一阶段只做轻缓存：

- 会话内保留当前输入表达式
- 按 `方法 + 字段路径` 缓存最近一次表达式

暂不支持：

- 脚本模板
- 历史记录列表
- 跨项目共享

## 对现有代码的影响

主要改动点：

- `MainUI`
- `MainPanel`
- `MyEditor`
- 参数字段视图相关组件（Phase 2）
- 新增字段定位、表达式执行、JSON 局部回填、缓存相关服务

不改变的部分：

- 现有 JSON 主编辑与执行链路
- 当前方法真正调度协议
- 现有预执行代码窗口功能

## 测试策略

至少覆盖：

- 光标路径能定位字段
- 树节点优先级高于光标路径（Phase 2）
- 基础类型字段回填成功
- DTO 字段回填成功
- 集合字段回填成功
- 表达式失败不污染 JSON
- 类型不兼容时正确拦截
- 不同字段表达式缓存互不串扰

## 范围控制

### Phase 1

- 固定表达式面板
- 光标路径定位
- 项目上下文表达式执行
- 当前字段回填
- 表达式缓存

### Phase 2

- 字段树优先
- 字段右键弹窗
- 面板与弹窗共用表达式组件

## 结论

该方案满足以下核心要求：

- 用户不需要先打断点
- 参数窗口内可直接写 Java 表达式
- 表达式体验接近 Evaluate Expression
- 执行发生在项目运行上下文
- 结果只作用于当前字段

推荐按 Phase 1 / Phase 2 逐步交付，先用最小风险交付核心能力，再扩展字段树与快捷入口。
