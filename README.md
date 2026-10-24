# AnyDoor 任意门

目标：执行Spring项目任意对象的任意方法

执行项目的任意方法提出者：[Lin ZiHao](https://github.com/schneiderlin)

## 适合场景
- xxlJob
- rpc入口
- mq入口
- 改动的测试

**Tips：配合热加载可以做到随时修改即可测试**

## 功能展示
![功能展示](dosc/image/功能展示1.gif)


## 快速开始（结合插件）
总结：安装插件 -> 启动项目 -> 右键方法 -> 填写参数 -> Run
### 1 安装插件
1.1安装，方便快捷，但需要自由上网（**推荐**）

1.2安装，提供了国内下载地址，但需要手动导入

#### 1.1 直接插件市场下载（推荐）
插件市场直接搜索 anydoor

![img.png](dosc/image/安装插件.png)

#### 1.2 下载提供的压缩包
下载完成后导入到idea [国内下载地址](https://gitee.com/lgp547/any-door-plugin-download)
![img.png](dosc/image/安装插件压缩包.png)


### 2 启动项目
平常方式启动java的main函数

Tips：一般情况下会主动填充运行进程的进程id（可在配置页面修改）

### 3 执行调用
1. 找到想要执行的方法，右键弹出选择打开任意门（有对应的快捷键 control/command+shift+D）

![img.png](dosc/image/打开任意门.png)

2. 填写要调用的参数，并点击 ok 按钮

![img.png](dosc/image/启动1.png)

3. **你将会发现当前方法被执行了！（可进行断点查看）**

## 插件属性说明
![img.png](dosc/image/方法执行.png)
![img_1.png](dosc/image/配置页1.png)


## 核心包支持功能
- 对象相关：
  - 对于是Spring注册的Bean，会通过上下文拿到对象（若有代理既是代理对象），执行当前方法
  - 对于非Spring注册的Bean，会通过反射创建对象，执行当前方法
- 方法相关：
  - 支持同步或异步执行当前方法，默认异步
  - 支持私有方法
  - 打印响应结果
- 参数相关：
  - 对给的参数进行序列化，支持json格式
  - 函数的参数支持lambda表达式入参，例如：Function的可以 `A -> A`
  - 时间支持：LocalDateTime传yyyy-MM-dd'T'HH:mm:ss、yyyy-MM-dd HH:mm:ss、yyyy-MM-dd

## 插件支持功能
- 执行参数将会缓存
- 可在任意地方重复上一次的调用
- 配置页面可打印Idea插件的路径
- 保留通过Http进行调用

## IDEA 兼容范围
- 最低支持 IntelliJ IDEA 2024.3（Build 243）
- 不限制最高版本
- 发布前通过 Plugin Verifier 验证 2024.3、2025.1、2025.2、2025.3、2026.1、2026.2



## 地址
[AnyDoorPlugin-idea插件中心](https://plugins.jetbrains.com/plugin/20385-anydoor)

## 后续支持
详情见issues

## 发布版本
> 注意发布版本的时候记得要把依赖打进去
### 2.7.1 上游改进整合
- 合入原仓库 `lgp547/any-door` 的 2.3.2 主线改进
- Agent 依赖包改为构建时生成，减少 Run/Debug 启动卡顿，支持插件目录只读时调用
- 使用不可变的方法调用快照，避免对话框回调中的 PSI 读访问异常
- 改进参数模板初始化、静态字段过滤和调试表达式编辑器的数据访问

### 2.7.0 Spring Bean 精确选择
- 同类型存在多个 Spring Bean 时，调用前按名称、容器和代理类型选择实例
- 支持从抽象父类的方法调用所选实现 Bean，抽象方法参数按位置传递
- 修复 Spring Boot 可执行 JAR、父子加载器和同名类隔离场景的类查找及线程上下文加载器
- `.anydoor` 请求文件支持 `@beanName`、`@beanContextId`、`@classLoaderId`

### 2.3.0 参数库版本
- [feat]参数库升级为类、方法、参数方案三级树形展示
- [feat]支持搜索、JSON 预览、重命名、批量删除和双击打开
- [feat]调用窗口支持另存为参数方案和保存修改

### 2.2.10 修复版本
- [fix]修复 IDEA 2026.2 中生成全部参数示例时的 PSI 读访问异常

### 2.2.9 修复版本
- [fix]修复 MANIFEST.MF 文件中替换符未正常替换的问题

### 2.2.0 功能扩展版本
- 支持提前执行任意代码（方便设置上下文）
- 支持 Json 带有注释的内容

### 2.1.4
- 支持高版本代码，调整默认支持未来所有版本
- [fix]24.1版本报错问题

### 2.1.3 修复版本
- 支持IDEA的24.1版本
- 返回值是Optional类型直接打印内容
- [fix]增加jsonKey判断逻辑

### 2.1.2 修复版本
- 支持23.3版本
- [fix]jsonKey的判断
- [fix]批量情况接口的参数传递

### 2.1.1 代码提示和导航+批量参数+修复
- 增加参数窗口填写Key的时候代码完成提示
- 增加参数窗口Key的导航跳转
- 支持批量参数批量的调用（数组格式）
- [fix]打开参数窗口遇到文件失效
- [fix]在黑色主题下窗口背景颜色不正确

### 2.0.2 修复版本
- [fix]修复获取不到bean的情况

### 2.0.1 修复版本
- [fix]修复私有方法丢失依赖

### 2.0.0 团队参数共享
- 团队协助，共享调用参数 [@zhouhao91](https://github.com/zhouhao91)
- ui页面优化 [@zhouhao91](https://github.com/zhouhao91)
- [fix]无提供spring项目的调用
- [fix]懒加载情况下会有无法实例化类情况
- [fix]参数传递中文路径
- [fix]重载方法参数有基本数据类型时报错问题 [@jinghong](https://github.com/auto-generated)

### 1.4.0 类加载隔离
- 类加载隔离，和运行项目不做耦合，无依赖冲突问题
- 支持没有 Spring 依赖的 Java 项目，插件运行无需使用方提供 Spring 依赖
- 同类型存在多个 Spring Bean 时，调用前选择 Bean 名称、所属容器和代理类型；非 Spring 对象仍优先使用已有实例
- [fix]无参方法无记录上一次调用

## 项目说明
- any-door-common：解决 attach 模块和 core 模块的共同依赖对象，减少重复代码
- any-door-attach：Java 动态织入入口，插件进程调度到 Java 进程的入口
  - 重要入口 AnyDoorAttach#agentmain （执行到这个方法，说明成功对 Java 进程侵入了）
- any-door-core：真正执行反射调用所在模块，以及支持各种扩展功能
  - 重要入口 AnyDoorService#run （一个是动态织入的入口，一个是非默认的 mvc 入口）
- any-door-plugin： IDEA 插件 UI 相关实现所在模块
  - 重要入口 AnyDoorOpenAction#actionPerformed AnyDoorPerformed#invoke

### 如何参与开发
- 启动 plugin 模块，将会打开一个新的沙盒 IDEA 进行调试
  - 更改 build.gradle.kts 文件下的 todo 
  - 模块启动入口在 Gradle 的 Tasks -> intellij -> runlde
- 对上面说到的重要入口进行打断点，然后再沙盒环境下操作即可

### 在 GitHub Actions 生成插件安装包

推送到 `codex/**` 开发分支、创建 Pull Request，或在 Actions → Build Plugin → Run workflow 手动运行，都会构建插件。流水线先运行 Maven 核心和 Attach 测试，再构建 ZIP、上传 `any-door-plugin` 产物，随后执行插件测试并上传测试报告。

打开对应 Actions 运行页面，在 Artifacts 中下载 `any-door-plugin`，解压外层产物压缩包后，可在 IDEA 的 Plugins → Install Plugin from Disk 中选择里面的插件 ZIP。即使后续插件测试失败，已构建并上传的安装包也可下载；测试状态需要同时查看。

正式发布使用 Actions → Release Plugin 手动运行，或推送 `v*` 标签。标签触发还会创建 GitHub Release 并附带安装包；该工作流会额外执行多版本 IDEA 兼容性检查。

### 选择 Spring Bean

同一个类型注册了多个 Spring Bean（例如 `primaryClient` 和 `secondaryClient`）时，点击 RUN 后会弹出选择窗口，显示 Bean 名称、所属 Spring 容器和代理类型（JDK / CGLIB / 普通对象）。选定后只调用该 Bean；取消选择不会执行方法。只有一个 Bean 或普通非 Spring 对象时，无需额外选择。候选查询不会初始化懒加载 Bean，未初始化对象会显示“未实例化”。

公共方法保留选中 Bean 的 AOP 行为，私有方法则调用该 Bean 对应的目标对象。如果 Bean 已删除、类型不符或容器已失效，调用会报错，不会退回其他实例。

在抽象父类声明的方法上打开任意门时，同样会列出各个具体实现 Bean。选定后，父类已实现的方法在该实例上执行，抽象方法则调用所选子类的重写实现。
插件会自动按位置传递抽象方法的参数，因此子类重写时可以使用不同的参数名。直接通过 HTTP 调用抽象方法时，可在 `content` 中使用 `args0`、`args1` 等位置参数。

`.anydoor` 请求文件可以明确指定 Bean：

```text
### 查询备用客户端
@beanName = secondaryClient
RUN demo.Client#query
{"id": 1}
```

未指定 `@beanName` 时沿用上述选择流程。同名 Bean 分布在多个容器时，还需要 `@beanContextId = 容器标识`；容器标识可从选择窗口或候选查询结果获得，仅对当前运行中的容器有效，重启后应重新查询。

HTTP 模式同样支持选择，可先向 `/any_door/run` 发送候选查询：

```json
{"operation":"listBeans","className":"demo.Client"}
```

返回的数组包含 `beanName`、`beanContextId`、`contextName`、`className`、`classLoaderId`、`proxyType`。调用时将选中的 `beanName`、`beanContextId` 和 `classLoaderId` 加入原调用 JSON。省略选择且存在多个候选时会报错，即使其中一个 Bean 标记了 `@Primary` 也需要明确选择。

Attach 模式保留 Arthas VmTool 查找真实对象的方式，先通过 JVM 的已加载类和应用加载器确定目标类型，再查找该类型的实例。Spring Boot 可执行 JAR、父子加载器或多个加载器加载同名类时，候选列表会附带 `classLoaderId`；弹窗选择会自动携带它，也可在 `.anydoor` 文件中设置 `@classLoaderId = 加载器标识`。标识仅对当前 JVM 有效，失效时会报错，不会随意选择另一份同名类。

`classLoaderId` 对应原请求类型的定义加载器，候选行中的 `className` 则展示真实实现类。发起调用时保留原请求的 `className`，并携带所选行的三个选择字段，避免将父类的加载器标识误用于子类。

调用和参数转换期间使用选中 Bean 真实目标类的加载器，结束后恢复原线程上下文；异步方法也会切换到相同的应用加载器。因此父类来自共享加载器、子类来自应用加载器时，父类方法仍可读取子应用资源。Bean 查询和代理识别通过应用自己的 Spring 接口完成，不要求 Agent 与应用共享同一份 Spring 类。

使用此功能时需要一起更新插件及其附带的 common、attach、core 包；HTTP 模式的应用依赖也需要更新。
目标 JVM 如果已经加载了旧版 Agent，需要重启目标进程后再使用新版本，避免继续使用驻留的旧类。


### 如何调试 agent 以及进程报错如何调试
导入attach 和 core 两个模块包到你自己的项目，在上面说明的重要入口打断点即可调试
```xml
<dependencies>
  <dependency>
    <groupId>io.github.lgp547</groupId>
    <artifactId>any-door-attach</artifactId>
    <version>${填写当前插件版本}</version>
  </dependency>
  <dependency>
    <groupId>io.github.lgp547</groupId>
    <artifactId>any-door-core</artifactId>
    <version>${填写当前插件版本}</version>
  </dependency>
</dependencies>
```



## 感谢
Tips：若觉得不错或支持项目，可以点个Star(✨✨)或者[评价](https://plugins.jetbrains.com/plugin/20385-anydoor/reviews/new)


> 感谢以下项目支持
> - [arthas](https://github.com/alibaba/arthas)
> - [jackson](https://github.com/FasterXML/jackson)
> - [lambda-from-string](https://github.com/greenjoe/lambdaFromString)

