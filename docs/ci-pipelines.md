# CI 流水线经验（双端踩坑沉淀，内网专属）

> 内网专属文档，`sync-upstream-pr.sh` 会把它从 upstream-pr 剔除。
> 双端 CI 都只在 `studio-v*` tag 推送时触发，互相独立、互不依赖。
> 每条 = 症状 → 原因 → 修法，按报错关键词检索。

## GitLab（.gitlab-ci.yml）

1. **作业阻塞没有 runner**：作业一直排队报"没有可用 Runner" → 内网 runner 只认 `androidapk` 标签 → 每个作业显式写 `tags: [androidapk]`
2. **release 作业卡死**：作业加再长 timeout 也无用 → release-cli 镜像在 registry.gitlab.com，内网拉不到 → Release 创建改用 curl 调 Release API（`${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases`），zip 资产先用 JOB-TOKEN 上传到 Generic Package Registry 再挂链接
3. **JDK 17 工具链解析失败**：CI 机器没预装 JDK 17 → 主仓和 included build（locator-gradle-plugin）的 settings 都要加 foojay `toolchains-resolver` 插件；settings 里 plugins 块必须写在 pluginManagement 之后，否则语法报错
4. **tag 推了流水线静默不创建**：tag 存在、无报错、pipelines 列表为空 → GitLab 靠 tag 提交里的 CI 配置建流水线，fork 侧剔除内网文件的提交没有 `.gitlab-ci.yml` → 内网 tag 打在 main 提交上（先例：studio-v0.1.8 → 22eb184）；已推错的删远端 tag 改指 main 提交重推
5. **作业日志 401**：`jobs/<id>/trace` 接口报 401 → 匿名只能查 `pipelines`/`jobs` 列表 → 排障走网页或带 PRIVATE-TOKEN

## GitHub Actions（fork）

1. **tag 推了 Actions 不跑**：设置允许后 tag 事件已被丢弃 → fork 首次启用 Actions 时删除 tag 重推；带 workflow 文件时还要在 Actions 页点 "I understand my workflows, go ahead and enable them"
2. **runner 不支持**：`macos-14-arm64-us-default is not supported` → `macos-14` 已废弃 → 用 `macos-latest`
3. **Release 创建 403**：`gh release create` 报 403 → job 缺权限 → 加 `permissions: contents: write`
4. **子进程 gradlew 启动失败**：仓库自定义 gradlew.bat 只从 PATH 找 java（不读 JAVA_HOME）→ Exec 任务起子进程把 `java.home/bin` 前置进 PATH；wrapper 按 OS 选（`os.name` 含 Windows 才用 bat）——"bat 文件存在就优先"会害死 mac/linux，仓库里本来就有 gradlew.bat
5. **Gradle 9 隐式依赖失败**：Copy 任务读写其他任务的输出目录 → 必须显式 dependsOn；两个任务不能写同一输出目录（变体 jar 重命名输出独立目录 `compiler-plugin-variants`，不回写 publish 产物的 build/libs）
6. **变体互踩**：kotlin-2.2/2.4 两次独立 Gradle 进程输出同名 jar → 两次构建 mustRunAfter 串行
7. **打包校验报版本号缺失**：`Marketplace plugin descriptor is missing <version>x.y.z</version>` → `META-INF/plugin.xml` 的 `<version>` 没跟着项目版本升 → 发版改齐 4 处版本号：根 build.gradle.kts、locator-gradle-plugin、studio-plugin 的 build.gradle.kts、plugin.xml（踩过：0.1.8）

## 发版 tag 纪律

- 内网 tag 打在 main 提交上；**fork 的 release tag 绝不打在内网 main 提交上**——从 `upstream-pr` 建临时分支、检出 main 的 `.github/workflows/publish.yml`、提交后打 tag（两 tag 同名不同提交）
- fork 只推 `upstream-pr` 分支和干净 tag，**绝不推 fork main**（误推过两次内网 main）
- 打 tag 前 `git show --stat <tag>^{tree}` 确认树里不含内网文件
- 先跑 `bash sync-upstream-pr.sh` 再建 fork tag：upstream-pr 落后 main 时 tag 里跑的是旧代码（踩过：main 已修 wrapper 选择逻辑，tag 里还是旧版导致 mac CI 失败）
