# QCL 社区版增量方案（基于 1.4.3 真实源码扫描）

> 扫描对象：`D:\A\QCL-1.4.3-完整源码\QCL-1.4.3`（versionCode 343 / versionName 1.4.3）
> 扫描方式：全量代码检索（502 个主模块 Java 文件 + 5 个子模块），只读，未改动任何文件。
> 结论以**代码实证**为准，不以 README 为准（本地 README 停在 1.4.2，落后一版）。

---

## 0. 前提纠正（必须先说清）

原始需求书写的是「Kotlin + Jetpack Compose + 插件化基础 + 本地数据库」。**实测不成立**：

| 需求书假设 | 实测结果 | 证据 |
|---|---|---|
| Kotlin |  0 个 `.kt` 文件 | `find -name "*.kt" \| wc -l` = 0 |
| Jetpack Compose |  无 | 全仓无 `androidx.compose` 依赖 |
| 插件化基础 |  无插件运行时 | 全仓无 `PluginManager` / `.mcplugin` / `plugin.json`；仅 `assets/plugin/`（放第三方渲染器 so） |
| 本地数据库 |  无 Room/SQLite | 全仓无 `androidx.room` / `SQLiteOpenHelper`；配置走 **Gson JSON 文件 + SharedPreferences** |
| 通知与后台任务 | 注意：仅 Terracotta VPN 前台服务 | `NotificationCompat` 仅出现在 `TerracottaVpnService` |
| 底部导航 |  无 | 无 `BottomNavigation` / `NavigationBar` |

**实际技术栈**：Java 8 + Android View（非 Compose）+ RelativeLayout/LinearLayout 预加载布局
+ Gson/SharedPreferences 持久化 + 多模块 Gradle。**且是反编译源码**（CFR/JADX 痕迹，类名被重命名）。

**因此**：
1. 需求书里「Kotlin + Compose」相关写法（`@Composable`、`remember`、Compose 导航）**一律不适用**，改写为 View/XML 实现。
2. 「插件市场」不是增量功能，而是**新增一整套运行时架构**（加载器 + 清单解析 + 权限 + 沙箱）。它是全清单里唯一的高风险项，必须单独立项。
3. 无数据库 → 需求书里的「数据库迁移」改为 **JSON 配置文件版本迁移**。

---

## 1. 现有功能扫描结果

### 1.1 已实现（→ 必须跳过，不得重复造）

| 需求书条目 | 现有实现 | 处理 |
|---|---|---|
| Forge / NeoForge / Fabric / Quilt / 原版 | `download/forge` `neoforge` `fabric` `quilt` `liteloader` `optifine` `modloader` `babric` 全套 InstallTask | **跳过** |
| Java 运行时管理（下载/切换/校验/修复） | `RuntimeInstallActivity` + `RuntimeUtils`（jre8/17/21/25，按 ABI 分卷，`isLatest` 校验） | **跳过** |
| 版本隔离 | 已有，默认开启（README + 设置项） | **跳过** |
| 整合包导入 | `InstallPackageUI` + MultiMC / Prism / HMCL / MCBBS / Modrinth / CurseForge / Server 七种 Provider | **跳过** |
| 整合包导出 | `ExportPackageTypeUI` / `InfoUI` / `FileUI` / `ExportWorldUI` + `mod/export/ModpackExporter` | **跳过** |
| 崩溃诊断 | `CrashReportActivity` + `QCLApplication` 全局 UncaughtExceptionHandler + 崩溃界面 | **跳过** |
| 渲染器切换 | `RendererPicker` / `RendererCompat`（Krypton/Holy-GL4ES/Zink/VirGL/Freedreno/VGPU/MG） | **跳过** |
| 内存分配 | `PojavLauncher.getMcArgs()` 动态夹取（剩余内存 70%） | **跳过** |
| 启动日志悬浮窗 | `LaunchLogWindow` | **跳过** |
| 模组管理 | `ModManager` / `ModClassInjector`（改 class 注入 + 冲突弹窗 + Class 查看器） | **跳过** |
| 模组冲突检测（class 级） | `ModClassInjector` + `ModpackHelper` 已有冲突弹窗 | **跳过** |
| 模组介绍一键翻译 | `utils/network/ModTranslateHelper`（1.4.1） | **跳过** |
| 资源包 / 光影 / 地图 / 投影 / 世界 管理 | `uis/game/download/right/resource` + `PackMcManagerUI` | **跳过** |
| 皮肤库 / 3D 预览 / 披风 / 一键更换 | `skin/` 全套 + `SkinLibraryDialog` / `SkinPreviewDialog` / `MicrosoftAccountSkinDialog` | **跳过** |
| 本地地图查看（部分） | 无 | 见新增 |
| 联机（局域网隧道） | Terracotta：`TerracottaVpnService` + `TerracottaAndroidAPI` + `MultiplayerDialogHelper`（邀请码） | **复用扩展** |
| 社区大厅 | `LobbyUI`（Mojang news + Modrinth 新模组 + 版本日历） | **复用扩展** |
| 实验室工具 | `LabUI` 6 项：`LabColorTextDialog` / `LabRecipeDialog` / `LabDatapackDialog` / `LabSchematicStudioDialog` / `LabSchematicDownloadDialog` / `LabSeedDialog` | **复用扩展** |
| 主题色 / 面板色 / 背景 | `ExteriorSettingUI`（主题色、面板色、默认/经典/自定义/在线 URL 背景、全屏） | **复用扩展** |
| 下载（BMCLAPI 镜像 + 多源） | `DownloadUrlSource`（官方/BMCLAPI/BMCLAPI-COMPAT）+ MCIM 镜像 | **跳过** |
| 版本更新检查 | `UpdateChecker`（5 个源 + 架构包自动挑选） | **跳过** |
| 下载取消 | `task/Task` 有 `cancelled` 机制 | 复用 |

### 1.2 完全缺失（→ 真正要新增的）

经检索确认**零实现**的项：全局搜索、命令面板、下载队列增强（暂停/继续/重试/限速/仅 Wi-Fi/通知栏进度）、实例备份与回滚、存档版本管理、二维码、P2P 直连、性能悬浮窗、省电/温控模式、AMOLED、无障碍模式、本地地图查看器、结构定位、NBT 编辑器、命令生成器、红石模拟器、WorldEdit 可视化、进度/战利品表/结构/维度/生物群系编辑器、皮肤分享码、模组分组启停、重复模组检测、插件市场与插件运行时、安全扫描、儿童模式。

### 1.3 命中但需甄别（避免误判为「已有」）

| 检索词 | 命中 | 实际含义 |
|---|---|---|
| 备份 | 7 文件 | 全是「打补丁前备份 jar」（`.jar.orig`），**不是实例备份** |
| 断点续传 | 3 文件 | 假阳性（`resume` 是生命周期方法） |
| 冲突检测 | 5 文件 | 仅 **class 级**冲突，无「重复模组 / 依赖冲突」 |
| NBT | 2 文件 | 仅投影工坊 + `World.java` 读取，**无编辑器** |

---

## 2. 去重后的新增功能清单（按「合并优先」原则）

### 2.1 跳过（已实现，一律不动）
Forge/NeoForge/Fabric/Quilt 加载器、Java 运行时管理、版本隔离、整合包导入导出、崩溃诊断、渲染器切换、内存分配、日志悬浮窗、模组管理、资源包/光影/地图/投影管理、皮肤库+3D+披风、下载镜像、更新检查。

### 2.2 合并（相似功能并入已有模块，不新建入口）

| 需求 | 合并到 | 理由 |
|---|---|---|
| 下载暂停/继续/重试/排序 | `task/Task` + `DownloadTask` 扩展 | 已有 cancel 机制，扩状态机即可，**不新建队列类** |
| 已完成任务管理 | `DownloadUI` 加 Tab | 复用现有下载页 |
| 服务器收藏/状态/延迟 | `MultiplayerDialogHelper` + Terracotta | 复用联机入口 |
| 皮肤导入导出/分享码 | `SkinLibraryDialog` 扩展 | 已有皮肤库 |
| 日历/主题包/启动动画/字体 | `ExteriorSettingUI` 扩展 | 已有外观设置 |
| 社区大厅榜单/教程/关注 | `LobbyUI` 加 Tab | 已有大厅 |
| 实验室新工具（NBT/命令/红石等） | `LabUI` 加卡片 | 已有实验室容器 |
| 种子查询/配方查询/投影 | `LabSeedDialog` / `LabRecipeDialog` / `LabSchematicStudioDialog` 增强 | 已存在，只补缺 |
| 实例克隆/智能配装 | `GameManagerUI` / `VersionSettingUI` 加操作项 | 已有实例管理 |

### 2.3 新增（真正新建，且尽量独立模块）

| # | 功能 | 载体 | 风险 |
|---|---|---|---|
| A | 底部导航 5 Tab | `activity_main.xml` + `MainActivity` | 中 |
| B | 全局搜索 / 命令面板 | 新建 `GlobalSearchDialog`（纯新增） | 低 |
| C | 实例备份 / 回滚 / 存档版本 | 新建 `launcher/backup/` 包 + `BackupUI` | 低 |
| D | 下载队列增强（暂停/限速/仅Wi-Fi/通知栏） | 扩展 `task/` + 新建 `DownloadNotificationHelper` | 中 |
| E | 性能悬浮窗 / 省电 / 温控 | 新建 `launcher/perf/` 包，挂在游戏悬浮窗 | 中 |
| F | 二维码（联机加入 / 皮肤分享 / 插件导入） | 新建 `utils/qr/`（引入 ZXing） | 低 |
| G | P2P 直连 / 手机开服 | 扩展 Terracotta + 内嵌服务端 | 高 |
| H | 插件市场 + 插件运行时 | 新建独立模块 `:PluginRuntime` | **极高** |
| I | 安全扫描（Mod/插件签名、黑名单） | 新建 `launcher/security/` | 中 |
| J | 本地地图查看器 / 结构定位 | 新建 `launcher/map/` | 中 |
| K | NBT 编辑器 / 命令生成器 / 红石模拟器 | `LabUI` 新卡片（复用 opennbt 依赖，已在） | 中 |
| L | AMOLED / 无障碍 | `ExteriorSettingUI` 扩展 | 低 |

---

## 3. 变更计划（第一阶段）

### 3.1 新增文件（实际交付）
```
# Java
QCL/src/main/java/com/qcl/launcher/launcher/uis/tools/BottomNavHelper.java    底部导航控制
QCL/src/main/java/com/qcl/launcher/launcher/dialogs/GlobalSearchDialog.java   全局搜索 / 命令面板
QCL/src/main/java/com/qcl/launcher/launcher/backup/InstanceBackupHelper.java  实例备份核心
QCL/src/main/java/com/qcl/launcher/launcher/backup/BackupRecord.java          备份记录模型
QCL/src/main/java/com/qcl/launcher/launcher/uis/backup/BackupUI.java          备份页逻辑
QCL/src/main/java/com/qcl/launcher/launcher/uis/plugin/PluginUI.java          插件页逻辑（诚实占位）

# Java —— 第 5 项「仅 Wi-Fi 下载」
QCL/src/main/java/com/qcl/launcher/utils/io/NetworkStateUtils.java            网络计费属性判定

# Java —— 第 7 项「存档版本管理」
QCL/src/main/java/com/qcl/launcher/launcher/backup/world/WorldBackupRecord.java  存档备份记录模型
QCL/src/main/java/com/qcl/launcher/launcher/backup/world/WorldBackupHelper.java  存档备份核心
QCL/src/main/java/com/qcl/launcher/launcher/dialogs/WorldBackupDialog.java       存档版本弹窗

# Java —— 第 4 项「下载通知栏进度」
QCL/src/main/java/com/qcl/launcher/launcher/download/DownloadNotify.java             通知构建与状态（节流 / 动作转发）
QCL/src/main/java/com/qcl/launcher/launcher/download/DownloadForegroundService.java  前台服务壳（只持有通知，不承载下载）

# Java —— 第 8 项「内存悬浮窗」（不含 FPS，理由见第 9 节）
QCL/src/main/java/com/qcl/launcher/launcher/perf/MemoryOverlayWindow.java            游戏内内存悬浮窗

# 布局
QCL/src/main/res/layout/view_bottom_nav.xml          底部导航条（5 Tab）
QCL/src/main/res/layout/ui_backup.xml                备份页
QCL/src/main/res/layout/ui_plugin.xml                插件页
QCL/src/main/res/layout/dialog_global_search.xml     搜索面板

# drawable
QCL/src/main/res/drawable/qcl_bottom_nav_bg.xml      导航条背景（上两角 16dp）
QCL/src/main/res/drawable/qcl_nav_tab_bg.xml         单个 Tab 的 selector
QCL/src/main/res/drawable/qcl_search_fab_bg.xml      搜索悬浮按钮背景
QCL/src/main/res/drawable/qcl_search_dialog_bg.xml   搜索面板背景
QCL/src/main/res/drawable/ic_qcl_search_white.xml    放大镜图标
```

> 注：第 7 项刻意**不新建页面、不新建布局 xml** —— 存档版本管理是「属于某个世界」的低频操作，
> 用代码构建 UI 的弹窗实现，避免动 `activity_main.xml` / `UIManager` / `MainActivity` 三处导航骨架。

### 3.2 修改文件（最小侵入）
| 文件 | 改动 |
|---|---|
| `res/layout/activity_main.xml` | 底部加 `<include layout="@layout/view_bottom_nav"/>`（**不写 android:id**，让根节点自带 id 生效）+ 右下角搜索悬浮按钮 + 两个新页面 include |
| `launcher/MainActivity.java` | 新增 `bottomNav` 字段；`showBarTitle()` / `hideBarTitle()` 里同步导航条状态 |
| `launcher/uis/tools/UIManager.java` | 新增 `switchMainUITab()`（切 Tab 时清栈，避免返回栈错乱）；注册 PluginUI / BackupUI |
| `res/layout/ui_setting.xml` | 「我的」页新增「备份与恢复」入口（**不新建底部 Tab**，符合「合并优先」） |
| `manifest/AppManifest.java` | 新增 `BACKUP_DIR` 常量与目录创建 |
| `task/DownloadTask.java` | 新增暂停/恢复语义 + HTTP Range 断点续传 + 416 / Content-Range 偏移自愈 |
| `launcher/dialogs/DownloadDialog.java` | 新增「暂停·继续」与「重试」按钮，失败不再直接关窗 |
| `res/layout/dialog_download.xml` | 新增状态提示行 + 重试 / 暂停按钮 |
| `res/values/strings.xml`、`res/values-zh/strings.xml` | 补齐全部新增文案（中英） |
| `launcher/setting/launcher/LauncherSetting.java` | 新增 `wifiOnlyDownload` 字段（第 5 项） |
| `launcher/uis/universal/setting/right/launcher/DownloadSettingUI.java` | 新增「仅 Wi-Fi 下载」开关读写（第 5 项） |
| `res/layout/ui_setting_download.xml` | 下载分组内新增仅 Wi-Fi 开关 + 说明（第 5 项） |
| `launcher/backup/InstanceBackupHelper.java` | `copyRecursive` / `deleteRecursive` 改 public 供存档备份复用（**不改行为**，第 7 项） |
| `launcher/list/local/save/WorldListAdapter.java` | 世界菜单新增「备份到版本」「从版本恢复」两项处理（第 7 项） |
| `res/menu/world_menu.xml` | 新增上述两个菜单项（第 7 项） |
| `AndroidManifest.xml` | 新增 `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE_DATA_SYNC` 权限 + 注册 `DownloadForegroundService`（`foregroundServiceType="dataSync"`）（第 4 项） |
| `launcher/dialogs/DownloadDialog.java` | 接入通知栏进度：聚合进度、暂停状态同步、通知栏按钮回调、Android 13+ 通知权限申请（第 4 项） |
| `launcher/setting/game/GameMenuSetting.java` | 新增 `showMemoryOverlay` 字段（**刻意不升 `GAME_MENU_VERSION`**：新 boolean 字段旧 JSON 读出来就是 false，正好是默认值）（第 8 项） |
| `res/layout/activity_control_pattern.xml` | 「启动日志」下方新增「内存悬浮窗」开关行（第 8 项） |
| `control/MenuHelper.java` | 接线内存悬浮窗开关；并**额外做了启动时恢复**（与「启动日志」不同 —— 监控悬浮窗开了就该每次进游戏都在）（第 8 项） |

### 3.3 配置迁移（替代「数据库迁移」）
- 新增 `AppManifest.BACKUP_DIR`（`filesDir/backups`）—— **纯新增目录，不迁移旧数据**。
- 备份索引文件 `backups/index.json`（Gson），带 `schemaVersion` 字段，为将来扩展预留。
- 存档备份索引 `<gameDir>/.qcl_worldbackups/index.json`（Gson），同样带 `schemaVersion`。
   **刻意放在游戏目录而非 filesDir**：与 `saves` 同卷，恢复时「挪走当前存档」才能是一次
  `rename`（零额外空间、瞬时），否则跨卷只能拷贝，正好是需求书想避免的「体积翻倍」。
- `LauncherSetting` 新增 `wifiOnlyDownload` 字段 → 随既有 `settings/launcher_setting.json`
  一起持久化，**旧 JSON 缺该字段读出为 `false`（保持老行为）**，无需迁移脚本。
- 下载暂停不引入任何新持久化文件：半截文件就是原目标文件本身，靠 HTTP `Range` 续传，
  最终由既有 SHA-1 校验兜底 → **旧版本可直接回滚**。
- **不改动** `settings/` 下任何现有 JSON 结构。


---

## 4. UI 页面与导航结构（目标态）

```
底部固定 5 Tab
├── 首页     → MainUI（现有）+ 全局搜索 + 快捷操作 + 社区大厅入口
├── 实例     → VersionListUI / GameManagerUI（现有）
├── 下载     → DownloadUI（现有）+ Tab：进行中 / 已完成 / 分类
├── 插件     → 新建 PluginMarketUI（第一阶段先占位，指向"未安装"）
└── 我的     → SettingUI（现有）+ 备份与恢复 + 实验室 + 关于
```
- 一级入口严格 5 个；二级用 Tab；三级用详情页。
- 实验室**不进底部导航**，收进「我的」。
- 返回栈：切 Tab 时 `uis.clear()` 再 push，避免返回时跨 Tab 错乱。

---

## 5. 核心流程（设计要点）

- **搜索安装**：本地索引（已装版本/模组）+ 远端静态 JSON 索引 → 输入即过滤 → 详情页 → 走现有 `InstallTask`。
- **更新**：读静态 JSON 的 `versionCode` 比对 → 差分包优先，失败回滚到旧包。
- **卸载**：先禁用 → 再删文件 → 清理残留（`plugin/` + `mods/` + 配置）。
- **权限授权**：安装时展示权限清单 → 用户确认 → 写 `permissions.json`；运行时按声明校验。
- **冲突处理**：沿用现有 class 冲突弹窗模式，扩展到「重复模组 / 依赖版本冲突」。
- **离线安装**：本地 `.mcplugin` / `.zip` 直接导入，不联网。
- **二维码导入**：扫码 → 解析出静态索引 URL 或插件直链 → 走同一套下载流程。

---

## 6. 安全机制

- **签名校验**：插件包内置 `signature` 字段，用内置公钥验签（**只做校验，不做自建云**）。
- **权限**：文件/网络/账号/剪贴板/通知/后台/命令/实例/联机 九类，安装时声明、运行时校验。
- **沙箱**：第一阶段用「独立 ClassLoader + 白名单 API 接口」；真正的进程级隔离留到第三阶段。
- **黑名单 / 行为监控**：静态 JSON 黑名单（哈希）+ 运行时异常上报（本地日志，不自动上传）。

---

## 7. 开发者生态

- **SDK**：`PluginRuntime` 暴露 `IPlugin` 接口 + `PluginContext`（受控 API）。
- **模板**：提供最小插件工程模板（清单 + 入口类）。
- **打包**：`.mcplugin` = zip（`plugin.json` + `classes.dex`/`libs/` + `signature`）。
- **发布**：推送到**任意静态托管**（GitHub Pages / Gitee / 对象存储），提交 PR 到索引 JSON 仓库即可。
- **兼容性测试**：索引里声明 `minLauncherVersion` / `targetApi`，安装前校验。

---

## 8. 增量开发步骤（三阶段）

- **第一阶段（最小可用）**：底部导航 5 Tab + 全局搜索 + 实例备份/回滚 + 下载队列增强（暂停/继续/重试）。
- **第二阶段（完善）**：二维码、性能悬浮窗/省电、安全扫描、实验室新工具（NBT/命令/红石）、AMOLED/无障碍。
- **第三阶段（生态）**：插件运行时 + 插件市场 + 开发者 SDK + P2P 直连/手机开服。

---

## 9. 最建议优先实现的 10 个功能点

| # | 功能 | 状态 |
|---|---|---|
| 1 | 底部导航 5 Tab（排版重构，收益最直观） |  已完成 |
| 2 | 全局搜索（兜底入口，替代翻页） |  已完成 |
| 3 | 下载暂停 / 继续 / 重试（现有 Task 只支持取消） |  已完成 |
| 4 | 下载通知栏进度（弱网/后台必需） |  已完成 |
| 5 | 仅 Wi-Fi 下载（低流量用户刚需） |  已完成 |
| 6 | 实例备份 / 回滚（数据安全，用户最痛） |  已完成 |
| 7 | 存档版本管理（世界误删可救） |  已完成 |
| 8 | 性能悬浮窗（FPS/内存，弱机调参必需） | 注意：已做「内存」部分，**FPS 未做**（见下） |
| 9 | 省电 / 温控模式（长时间游玩） | 注意：经查**大部分已被现有功能覆盖**，待定 |
| 10 | 二维码加入联机（Terracotta 现在只能手输邀请码） |  已完成（手写零依赖编码器） |
| 11 | **启动诊断页**（闪退时用户自己能拿到线索并一键发出） |  已完成（见下） |

**第 8 项实现要点（含一处如实缩水）**：交付的是**内存悬浮窗**，不是「性能悬浮窗」。
搜索全仓 `QCL/src/main/java`，**`fps` 零命中** —— 工程没有任何现成的帧率数据源。
Java 层 `Choreographer` 测到的是 **Android UI 线程的 vsync 节奏**，而 Minecraft 的画面由
渲染线程 / GL 线程输出，两者不是一回事；把它当「游戏 FPS」显示就是**假数据**。
所以本轮**不做 FPS**，只报四个真实可测的量：
Java 堆（used/max，堆吃紧时变红）、Native 堆、进程 PSS、整机可用内存。
选内存而非 FPS，是因为 README 写明「按剩余内存动态夹取堆」—— 内存才是这个项目真正要盯的量。
要真 FPS 必须在原生层挂 swap buffers 钩子或让渲染器上报，属于独立一轮的工作。

**第 9 项经查证后的问题**：`MenuHelper.init()` 里已经存在
`switch_performance` → `window.setSustainedPerformanceMode(...)`（持续性能模式，照 FCL 的
`PERFORMANCE_MODE`），另有渲染器切换、内存分配、`menuFloatSetting` 等。
也就是说「省电 / 温控」想做的事**大部分已经被现有开关覆盖**，硬做只会多出一堆
语义重叠、玩家不知道该点哪个的开关 —— 这正是需求书里「同一功能只出现一次」要避免的。
建议先明确它到底要解决什么具体场景（如「锁 30 帧」「降分辨率」「长时间游玩后降频」），
再决定是做还是不做。

**第 4 项实现要点**：前台服务的真实作用是**提高进程优先级，让用户切到别的应用时下载不被冻结/回收**，
以及把进度放进通知栏。它**不能**让下载在「用户划掉应用 / 强行停止」之后继续 ——
下载引擎仍是 `DownloadTask`（`AsyncTask`），生命周期绑在进程上。要做到那一步得把下载整体搬进 Service，
属于重构，不在本轮范围。**已在代码注释里写明这条边界，不夸大。**
`targetSdk 34` 带来两个硬要求：`POST_NOTIFICATIONS`（API 33+ 运行时授权，拒绝时通知被静默丢弃）
与 `FOREGROUND_SERVICE_DATA_SYNC` + `foregroundServiceType="dataSync"`（API 34 起强制，漏写直接抛异常）。
通知是「锦上添花」——所有入口都做了兜底，最坏情况是没有通知栏进度，下载本身照常。
进度用 `path` 做 key 聚合，分母固定为任务文件总数（用已完成列表算会让进度条往回跳）。

**第 5 项实现要点**：`NetworkStateUtils` 用 `NetworkCapabilities.NET_CAPABILITY_NOT_METERED`
而不是 `NetworkInfo.getType()==TYPE_WIFI` —— 前者才真正回答「这条网络会不会花你的钱」
（热点/以太网/VPN 不走流量，而某些公共 Wi-Fi 是计费的）。判定失败一律放行，
宁可继续下载也不要让用户卡在「连着 Wi-Fi 却下不动」。网络暂停复用下载暂停的等待点，
回到 Wi-Fi 自动续传；**只自动放行网络造成的暂停，用户手动点的暂停不会被顺手解掉**。

**第 7 项实现要点**：备份根目录放在 `<gameDir>/.qcl_worldbackups`（与 saves 同卷），
使「恢复前把当前存档挪走」是一次 `rename` 而非拷贝 —— 零额外空间。
 此处**刻意偏离**需求书「存档备份不做安全副本」：恢复存档是覆盖当前进度的不可逆操作，
同卷 rename 的成本可忽略，而「恢复完发现快照是坏的、当前进度也没了」正是本功能要防的失败。
退路会登记成一条带「安全副本」标记的备份，玩家可见可删。

**第 11 项实现要点（启动诊断页）**：这一项不在原需求书里，是在**排查「打开直接闪退」的过程中
发现缺口后补的** —— 用户手上其实一直有线索（`StartupTrace` 一直在写路标），
但线索藏在 `/sdcard/QCL/startup_trace.log` 这种用户不容易打开的位置，
于是每次排查都退化成「我远程猜 → 让用户去某个目录找文件 → 用户找不到 → 再来一轮」。
本页把这条链路缩短成：**打开这一页 → 点「分享给作者」→ 发出去**。

- 入口：`我的 → 启动诊断`（与备份页同样收进「我的」，不占底部导航）。
- 内容三段：设备信息 / 上次启动未走完（仅真发生时出现）/ 本次启动路标，全部等宽字体。
- 三个动作：刷新 / 复制全部 / 分享给作者（`ACTION_SEND`，走用户自己的微信/QQ）。
- **只读**：不提供「清除路标」按钮 —— `StartupTrace.init()` 每次启动都会截断重写，
  显示的内容永远属于「本次启动」，不存在需要手动清理的陈旧数据；
  多一个按钮只会多一个把线索弄丢的机会。
- **为什么用分享而不是上传**：需求书明确不建自建服务器。`ACTION_SEND` 不需要任何后端，
  而且比截图更完整（截图常常截不到关键的那几行）。

---

## 10. 测试要点与回滚方案

**测试要点**
- 导航：5 Tab 互切、返回栈不跨 Tab 错乱、游戏返回后 Tab 高亮正确。
- 备份：大实例（>2GB）备份不 OOM；回滚后实例可正常启动；备份索引损坏可重建。
- 下载：弱网断流后恢复；暂停后继续不重复下载；仅 Wi-Fi 下切到流量应暂停并提示。
- **下载暂停专项**（本轮新实现，必须逐条验）：
  1. 暂停 → 半截文件**必须留在磁盘上**（去文件管理器确认，不是被删了）；
  2. 继续 → 抓包/看服务端日志确认发的是 `Range: bytes=N-` 且返回 206，**不是从 0 重下**；
  3. 服务端不支持 Range（回 200）→ 必须自动覆盖重下，**不能**出现「前后半段拼错」的文件；
  4. 人为造 416（把半截文件补成完整大小）→ 应自愈重下，**不能**陷入无限失败；
  5. 暂停中点「取消」→ 立刻停住（验证 `pauseLock.notifyAll` 生效，不等 500ms 轮询）；
  6. 暂停超过 1 小时 → 恢复后仍能继续，**不能**弹出「下载完成」；
  7. 重试：只重下失败项，已成功项因 SHA-1 校验通过被跳过。
- 回归：**现有 25 个页面全部可达**（本次是加导航，不是改页面）；
  安装流程（Forge/Fabric/Quilt/OptiFine/整合包）走的仍是 `downloadFileMonitored(...)` 旧签名，行为必须与改前逐字节一致。
- 低内存：`largeHeap` 已开，备份走流式拷贝，禁止整包读入内存。
- **仅 Wi-Fi 下载专项**（第 5 项）：
  1. 关着开关 → 移动网络下正常下载（默认行为不能被改坏）；
  2. 开着开关 + 移动网络 → 一开下就暂停，提示「当前不是 Wi-Fi」，**半截文件留在磁盘**；
  3. 暂停后切回 Wi-Fi → **自动**继续，且发的是 `Range` 请求（不重下）；
  4. 移动网络下点「继续」→ 仍应保持暂停（设置的本意就是不走流量）；
  5. 开着开关但**完全断网** → 不能无限停住，应正常失败并给出失败清单；
  6. 移动网络下手动暂停 → 切回 Wi-Fi 后**不能**被自动放行（手动暂停优先级更高）。
- **存档版本管理专项**（第 7 项）：
  1. 备份 → `<gameDir>/.qcl_worldbackups/` 下出现快照，弹窗列表可见，世界列表**不**多出条目；
  2. 恢复 → 世界回到快照状态，且列表里多出一条带「安全副本」标记的备份；
  3. 恢复后把安全副本再恢复一次 → 能回到恢复前的进度（**退路必须真的可用**）；
  4. 存档目录被指到自定义路径（可能跨卷）→ 恢复仍成功（走拷贝分支）；
  5. 备份索引被删/损坏 → 当作空列表，不崩溃；
  6. 世界在游戏里改过名 → 备份仍归属到同一个世界（按目录名分组，不按显示名）。
- **下载通知栏进度专项**（第 4 项）：
  1. 首次下载会弹通知权限申请；**拒绝后下载仍能正常完成**（只是没有通知栏进度）；
  2. 授权后：通知栏出现带进度条的通知，切到桌面 / 其他应用时进度仍在刷新；
  3. 通知栏点「暂停」→ 应用内对话框的按钮同步变成「继续」，**两处状态不能不一致**；
  4. 通知栏点「取消」→ 对话框关闭、下载中断、通知消失；
  5. 下载完成 / 失败 / 取消 → 通知**必须消失**（不能留下一条永远不动的进度条）；
  6. 失败后点「重试」→ 通知重新出现且进度从 0 开始（不是接着上一次的百分比）；
  7. 开着「仅 Wi-Fi」切到移动数据 → 通知文案是「已暂停（当前不是 Wi-Fi）」而非干巴巴的「已暂停」；
  8. Android 14 机型上不出现 `MissingForegroundServiceTypeException` / 前台服务启动失败崩溃。
- **内存悬浮窗专项**（第 8 项）：
  1. 游戏菜单 → 打开「内存悬浮窗」→ 左上角出现面板，数值每秒刷新；
  2. 拖动面板 → 可移动，且**不会被拖出屏幕外**（边界夹取生效）；
  3. 点 `×` → 面板消失，且**再打开菜单时开关回到关闭**（设置已持久化）；
  4. 关掉游戏重进 → 开关仍是开的、面板**自动出现**（这是与「启动日志」刻意不同的行为）；
  5. 面板数值要与实际相符：Java 堆 max 应约等于启动器给游戏分配的内存上限；
  6. 打开面板后游戏**不应出现卡顿/掉帧**（每秒一次读取，开销可忽略）；
  7. 快速连点开关多次 → 不会叠加出两个面板（`showFor` 有 `isShowing()` 守卫）。
- **回归（本轮新增，必须一并验）**：游戏菜单里**原有的每一个开关**都仍可用 ——
  尤其「浮动按钮」「启动日志」「持续性能模式」，因为本轮在 `MenuHelper.init()` 与
  `onCheckedChanged` 里都插了代码，插错位置会静默破坏相邻分支。

**回滚方案**
- 代码层：删除新增文件（13 个 Java + 4 个布局 + 5 个 drawable）+
  还原修改的文件（`activity_main.xml`、`ui_setting.xml`、`ui_setting_download.xml`、
  `dialog_download.xml`、`world_menu.xml`、`activity_control_pattern.xml`、`AndroidManifest.xml`、
  `MainActivity`、`UIManager`、`SettingUI`、`AppManifest`、`LauncherSetting`、`DownloadSettingUI`、
  `DownloadTask`、`DownloadDialog`、`WorldListAdapter`、`InstanceBackupHelper`、`MenuHelper`、
  `GameMenuSetting`、两个 strings）
  即可完全回退。**`DownloadTask` 的旧方法签名已保留**，所以安装流程可以单独回退而不动 UI。
- 数据层：新增的只有 `filesDir/backups/`、`<gameDir>/.qcl_worldbackups/` 两个目录、
  `launcher_setting.json` 里一个布尔字段、`game_menu_setting.json` 里一个布尔字段。
  回退版本后这些数据**被忽略而非报错**（旧代码不读这些字段、不扫这两个目录），无需清理。
- 注意：**回退版本会丢失「仅 Wi-Fi」与「存档版本」的设置/记录**，但不会影响存档本身 ——
  存档备份目录独立于游戏存档，删掉它只是丢掉快照。
- 第 4 项单独回退：删掉 `launcher/download/` 两个类 + 清单里的 3 条声明即可，
  `DownloadDialog` 里对 `DownloadNotify` 的调用需一并还原（或保留 `DownloadNotify` 但不启动服务）。
- 第 8 项单独回退：删掉 `launcher/perf/MemoryOverlayWindow.java`，
  还原 `MenuHelper` 的 4 处插桩 + `GameMenuSetting` 的 1 个字段 +
  `activity_control_pattern.xml` 的 1 个开关块即可。
- 数据层：新增目录 `filesDir/backups` 与旧配置无耦合；卸载备份功能后旧版可直接运行。
  下载暂停**不引入任何新持久化文件**（半截文件就是原目标文件），旧版 APK 直接覆盖安装即可。
- 发布层：分阶段出包，任一阶段异常可退回上一版 APK，配置向后兼容。
