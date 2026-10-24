## AnyDoor 2.7.1

- 整合原仓库 [lgp547/any-door](https://github.com/lgp547/any-door) 的 2.3.2 主线改进。
- Agent 依赖包改为构建时生成，减少 Run/Debug 启动卡顿，调用时无需写入插件安装目录。
- 使用不可变的方法调用快照，修复对话框回调中的 PSI 读访问异常。
- 改进默认参数模板、静态字段过滤及调试表达式编辑器的数据访问。
- 保留 2.7.0 的参数库、请求文件、Spring Bean 精确选择及类加载器隔离能力。

### 安装

下载下方 `any-door-plugin-2.7.1.zip`，在 IDEA 的 **Settings → Plugins → Install Plugin from Disk** 中选择该 ZIP，然后重启 IDEA。

如果目标 Java 进程已加载旧版 AnyDoor Agent，也需要重启目标进程后再调用。HTTP 模式请同步更新应用中的 AnyDoor 依赖。
