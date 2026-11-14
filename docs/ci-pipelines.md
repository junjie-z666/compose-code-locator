# CI 流水线经验（双端踩坑沉淀，内网专属）

> 内网专属文档，`sync-upstream-pr.sh` 会把它从 upstream-pr 剔除。
> 双端 CI 都只在 `studio-v*` tag 推送时触发，互相独立、互不依赖。

## GitLab（内网 .gitlab-ci.yml）

1. **runner 标签**：内网 runner 只认 `androidapk` 标签，每个作业都要显式写 `tags: [androidapk]`——漏写的作业会报"此作业已阻塞，因为该项目没有分配任何可用 Runner"
2. **不能用 release-cli**：它的镜像在 registry.gitlab.com，内网拉不到会一直卡死（作业加再长 timeout 也无用）。Release 创建改用 curl 调 Release API：`${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases`，zip 资产先用 JOB-TOKEN 上传到 Generic Package Registry 再挂链接
3. **JDK 工具链**：CI 机器没有预装 JDK 17，主仓和 included build（locator-gradle-plugin）的 settings 都要加 foojay `toolchains-resolver` 插件；且 settings 脚本里 plugins 块必须写在 pluginManagement 之后，否则语法报错
4. **tag 提交必须含 .gitlab-ci.yml**：GitLab 靠 tag 提交里的 CI 配置创建流水线，把 fork 侧剔除内网文件后的提交打 tag 推到 origin 时，流水线静默不创建（tag 存在但 pipelines 为空，无任何报错）。内网 tag 要打在 main 提交上（先例：studio-v0.1.8 → 22eb184）；万一 tag 推了没建流水线，删掉远端 tag 改指 main 提交重推即可
5. **读流水线/作业日志需要认证**：匿名只能查 `pipelines`/`jobs` 列表接口，`jobs/<id>/trace` 报 401，排障要么网页上看要么带 PRIVATE-TOKEN

## GitHub Actions（fork）

1. **fork 首次启用**：Actions 默认禁用；设置里允许后，之前推的 tag 事件已被丢弃，需删掉 tag 重推才触发；fork 自带 workflow 文件时还要在 Actions 页点绿色 "I understand my workflows, go ahead and enable them"
2. **runner 标签**：`macos-14` 已废弃（报 `macos-14-arm64-us-default is not supported`），用 `macos-latest`
3. **创建 Release 权限**：`gh release create` 报 403 时，给对应 job 加 `permissions: contents: write`
4. **子进程 gradlew**：仓库自定义 gradlew.bat 只从 PATH 找 java（不读 JAVA_HOME），Exec 任务起子进程要把 `java.home/bin` 前置进 PATH；选 wrapper 按 OS 判断（`os.name` 含 Windows 才用 bat），不能用"bat 文件存在就优先"——仓库里本来就有 gradlew.bat，mac/linux 会照样选中导致启动失败
5. **Gradle 9 隐式依赖**：Copy 任务读写其他任务的输出目录必须显式 dependsOn；两个任务不能写同一输出目录（变体 jar 重命名输出到独立目录 `compiler-plugin-variants`，不能回写 publish 产物的 build/libs）
6. **变体串行**：kotlin-2.2/2.4 两次独立 Gradle 进程构建输出同名 jar，必须 mustRunAfter 串行，否则互踩
7. **plugin.xml 版本号**：`verifyStudioPluginPackaging` 要求 `META-INF/plugin.xml` 的 `<version>` 与项目版本一致，升版本漏改它 GitHub 端 CI 会报 "Marketplace plugin descriptor is missing <version>x.y.z</version>"（踩过：0.1.8）。发版版本号要改齐 4 处：根 build.gradle.kts、locator-gradle-plugin、studio-plugin 的 build.gradle.kts、plugin.xml

## 发版 tag 纪律

- 内网 tag 直接打在 main 提交上即可；**GitHub fork 的 release tag 绝不能打在内网 main 提交上**——必须从 `upstream-pr` 建临时分支、检出 main 的 `.github/workflows/publish.yml`、提交后打 tag
- fork 只推 `upstream-pr` 分支和干净 tag，**绝不推 fork main**（历史上误推过两次内网 main）
- 打 tag 前先 `git show --stat <tag>^{tree}` 确认树里不含内网文件
- 同步 main 后记得先跑 `bash sync-upstream-pr.sh` 再建干净 tag：fork tag 是从 upstream-pr 构建的，upstream-pr 落后 main 时 tag 里跑的是旧代码（踩过：main 已修 wrapper 选择逻辑，tag 里还是旧版导致 mac CI 失败）
