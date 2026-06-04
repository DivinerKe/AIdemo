# Agnes AI Demo

调用 [Agnes AI](https://agnes-ai.com/) 开放 API 的示例项目（OpenAI 兼容格式）。

## 获取 API Key

1. 打开 [Agnes AI 平台](https://platform.agnes-ai.com)
2. 注册并登录
3. 创建 API Key 并保存

### 本地配置（推荐）

在项目根目录 `local.properties` 中添加（该文件已在 `.gitignore` 中，不会提交）：

```properties
agnes.api.key=sk-你的密钥
```

重新编译后，App 会自动填入 API Key。

## API 说明

| 项目 | 值 |
|------|-----|
| Base URL | `https://apihub.agnes-ai.com/v1` |
| 对话接口 | `POST /chat/completions` |
| 默认模型 | `agnes-2.0-flash` |
| 认证 | `Authorization: Bearer <API_KEY>` |

## Android App

1. 用 Android Studio 打开本目录
2. 运行 `app` 模块
3. 点击「授权悬浮窗」→「显示悬浮窗」
4. 返回桌面，点击紫色 **AI** 圆球打开对话面板（可拖动）
5. App 内也保留全屏对话区域

### 悬浮窗操作

| 操作 | 说明 |
|------|------|
| 点击圆球 | 展开对话面板 |
| 拖动标题栏 / 圆球 | 移动位置 |
| 点 ✕ | 收起为圆球 |
| 通知栏 | 可回到 App 或关闭悬浮窗 |

## Python 命令行（可选）

```bash
cd scripts
set AGNES_API_KEY=你的密钥
python agnes_demo.py 介绍一下你自己
```

## 项目结构

```
AIdemo/
├── app/                          # Android 示例
│   └── src/main/java/com/moke/aidemo/
│       ├── MainActivity.java     # 界面
│       └── AgnesApiClient.java   # API 封装
├── scripts/
│   └── agnes_demo.py             # Python 示例
└── README.md
```
