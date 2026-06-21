# AB Download Manager CLI

## 概述

AB Download Manager 的命令行界面（CLI）模块，允许用户直接在终端中管理下载，无需启动 GUI。

## 安装

### 构建

```bash
# 构建可执行 JAR
./gradlew :cli:app:jar
# 构建完整可分发包（推荐）
./gradlew :cli:app:installDist
# 或直接运行
./gradlew :cli:app:run --args="--help"
```

### 运行

构建完成后，有多种运行方式：

```bash
# （推荐）使用 installDist 生成的应用脚本
cli/app/build/install/abdm/bin/abdm <command>

# 或使用项目根目录的快捷脚本
./abdm.bat <command>

# JAR 方式
java -jar cli/app/build/libs/app.jar <command>

# 或通过 Gradle 运行（较慢，需要每次编译）
./gradlew :cli:app:run --args="<args>"
```

> **提示：** `installDist` 方式（`abdm`）是最快的，因为不需要重新编译。`abdm.bat` 会自动帮你构建和调用。

### 环境要求

- JDK 17+（推荐 JDK 21）
- Gradle 会自动使用项目配置的 toolchain 版本

## 使用指南

### 添加下载

```bash
# 基本用法 - 添加到下载列表但不立即开始
abdm add https://example.com/file.zip

# 添加并立即开始下载
abdm add https://example.com/file.zip --start

# 指定输出目录和文件名
abdm add https://example.com/video.mp4 -o ~/Downloads -n myvideo.mp4 --start

# 多个 URL
abdm add https://example.com/file1.zip https://example.com/file2.zip --start

# 指定并发连接数（加速大文件下载）
abdm add https://example.com/largefile.iso -c 8 --start

# 添加到指定队列（不立即开始）
abdm add https://example.com/file.zip --queue 1

# 静默添加 - 不输出进度信息
abdm add https://example.com/file.zip --quiet --start
```

### 管理下载

```bash
# 列出活跃下载（排除已完成的）
abdm list

# 列出所有下载（包括已完成）
abdm list -a

# 查看下载详情
abdm info 1

# 暂停下载
abdm pause 1
abdm pause 1 2 3  # 批量暂停

# 恢复下载
abdm resume 1

# 删除下载（同时删除文件）
abdm remove 1

# 删除下载但保留文件
abdm remove 1 --keep-file
```

### 查看下载详情

`info` 命令显示单个下载的完整信息：

```bash
abdm info 0
```

输出示例：

```
Download #0
  Name:    test-1mb.dat
  URL:     https://proof.ovh.net/files/1Mb.dat
  Folder:  /tmp/downloads
  Status:  Completed
  Size:    1.00 MB
  Added:   2026-06-21 07:54:41
  Connections: 4
  Speed limit: 0 B/s
  Checksum: sha256:abc123...
```

显示的字段：
| 字段 | 说明 |
|---|---|
| Name | 文件名 |
| URL | 下载来源地址 |
| Folder | 文件保存目录 |
| Status | 状态（Downloading / Paused / Completed / Error / Queued） |
| Size | 文件大小（自动格式化 B/KB/MB/GB） |
| Added | 添加时间戳 |
| Connections | 并发连接数（仅当设置时显示） |
| Speed limit | 速度限制（仅当设置时显示） |
| Checksum | 文件校验值（仅当设置时显示） |

### 队列管理

```bash
# 列出所有队列
abdm queue list

# 启动队列
abdm queue start 0

# 停止队列
abdm queue stop 1
```

队列用于组织和管理下载任务。默认有一个主队列（ID: 0, Name: "Main"）。

### 分类管理

```bash
# 列出所有分类（当前为预览功能）
abdm category list
```

分类管理目前为预览功能，完整分类编辑需要桌面版应用支持。

### 监控

```bash
# 查看当前活跃下载
abdm monitor
```

显示当前正在下载或暂停中的任务列表。

### 全局参数

```bash
# 指定配置目录（默认 ~/.abdm-cli）
abdm --config-dir ~/my-config add https://example.com/file.zip

# 指定默认下载目录（默认当前目录）
abdm --download-dir ~/Downloads add https://example.com/file.zip
```

## 命令行参考

### 可用命令

| 命令 | 说明 |
|---|---|
| `add <URL>...` | 添加下载任务 |
| `list` | 列出下载 |
| `info <ID>` | 查看下载详情 |
| `pause <ID>...` | 暂停下载 |
| `resume <ID>...` | 恢复下载 |
| `remove <ID>...` | 删除下载 |
| `queue list/start/stop` | 队列管理 |
| `category list` | 分类管理 |
| `monitor` | 实时监控 |
| `daemon` | 后台守护进程 |

### 全局选项

| 选项 | 说明 |
|---|---|
| `--config-dir <dir>` | 指定配置目录 |
| `--download-dir, -d <dir>` | 指定默认下载目录 |
| `--help, -h` | 显示帮助信息 |

### add 命令选项

| 选项 | 简写 | 说明 |
|---|---|---|
| `--start` | `-s` | 立即开始下载；不加此选项只添加到列表 |
| `--output-dir` | `-o` | 自定义输出目录 |
| `--name` | `-n` | 自定义文件名 |
| `--connections` | `-c` | 并发连接数 |
| `--queue` | `-q` | 目标队列 ID |
| `--quiet` | | 静默模式，不输出信息 |

### list 命令选项

| 选项 | 简写 | 说明 |
|---|---|---|
| `--all` | `-a` | 显示所有下载（包括已完成） |

### remove 命令选项

| 选项 | 简写 | 说明 |
|---|---|---|
| `--keep-file` | `-k` | 删除记录但保留已下载的文件 |

## 架构

CLI 模块位于 `cli/app/`，由以下部分组成：

```
cli/app/src/main/kotlin/com/abdownloadmanager/cli/
├── CliMain.kt            # 入口和 CLI 参数解析（Clikt）
├── commands/             # 命令处理器
│   ├── AddCommand.kt     # 添加下载
│   ├── ListCommand.kt    # 列出下载
│   ├── ControlCommands.kt # 暂停/恢复/删除/详情
│   ├── QueueCommand.kt   # 队列管理
│   └── CategoryCommand.kt # 分类管理
├── tui/
│   └── MonitorCommand.kt # TUI 监控
├── di/
│   ├── CliDi.kt          # 依赖注入容器（Koin）
│   └── CliDownloadService.kt # CLI 下载服务
├── daemon/
│   └── DaemonCommand.kt  # 后台守护进程
└── utils/
    ├── CliPaths.kt       # 路径配置
    └── CliAppInfo.kt     # 应用信息
```

### 依赖注入

CLI 使用 Koin DI 容器（`CliDi.kt`），仅启动下载引擎的核心组件：
- `DownloadManager` - 核心下载管理器
- `QueueManager` - 队列管理
- `ManualDownloadQueue` - 手动下载队列控制
- `DownloaderRegistry` - 下载器注册（HTTP + HLS）

不包含任何 Compose/GUI 相关依赖。

### 关键类

| 类 | 文件 | 说明 |
|---|---|---|
| `CliDownloadService` | `di/CliDownloadService.kt` | 包装 DownloadManager 提供 CLI 友好接口 |
| `CliPaths` | `utils/CliPaths.kt` | 管理 CLI 数据目录结构 |
| `CliDi` | `di/CliDi.kt` | Koin 模块配置和引导 |
| `CliApp` | `CliMain.kt` | Clikt 命令入口 |

### 数据存储

CLI 使用独立的数据库文件，存储在 `~/.abdm-cli/` 目录下：
- `download_db/downloadlist/` - 下载列表
- `download_db/parts/` - 分片信息
- `download_db/queues/` - 队列配置
- `download_data/` - 下载中的临时数据

与桌面版使用不同的数据目录，互不干扰。

## 开发

### 添加新命令

1. 在 `commands/` 目录下创建新的 Command 类，继承 `CliktCommand`
2. 实现 `KoinComponent` 接口以使用依赖注入
3. 在 `CliMain.kt` 的 `CliApp` 中注册到命令树

### 测试

```bash
./gradlew :cli:app:test
```

测试使用 JUnit 4 + Kotlin 测试框架，在 `cli/app/src/test/` 目录下。

## 与桌面版的关系

CLI 模块与桌面版共享相同的下载引擎核心（`downloader/core`），但：
- 不启动任何 GUI 窗口
- 不加载 Compose 运行时（仅加载 `@Immutable` 注解所需的 runtime 依赖）
- 通过直接 API 调用管理下载
- 数据与桌面版隔离（使用独立的数据目录 `~/.abdm-cli`）
- 所有命令使用 Clikt 解析参数，Mordant 输出带颜色格式的终端文本