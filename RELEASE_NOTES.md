## AnyDoor 2.7.0

- 同类型存在多个 Spring Bean 时，调用前选择 Bean 名称、所属容器和代理类型，取消选择不会执行方法。
- 从抽象父类打开任意门，可选择具体实现 Bean；父类方法和子类重写方法均在所选实例上执行。
- 修复 Spring Boot 可执行 JAR、父子加载器和多个加载器加载同名类时的类型查找与执行上下文问题，保留 Arthas VmTool 查找真实对象。
- `.anydoor` 文件支持 `@beanName`、`@beanContextId`、`@classLoaderId`，失效选择会报错。

### 安装

下载下方 `any-door-plugin-2.7.0.zip`，在 IDEA 的 **Settings → Plugins → Install Plugin from Disk** 中选择该 ZIP，然后重启 IDEA。

如果目标 Java 进程已加载旧版 AnyDoor Agent，也需要重启目标进程后再调用。HTTP 模式请同步更新应用中的 AnyDoor 依赖。
