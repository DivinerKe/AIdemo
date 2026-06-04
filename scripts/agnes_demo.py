#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Agnes AI 命令行调用示例（OpenAI 兼容接口）

使用前设置环境变量:
  set AGNES_API_KEY=你的密钥

或在 platform.agnes-ai.com 后台创建 API Key:
  https://platform.agnes-ai.com
"""

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime

API_URL = "https://apihub.agnes-ai.com/v1/chat/completions"
DEFAULT_MODEL = "agnes-2.0-flash"


def build_system_prompt() -> str:
    now = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")
    return (
        "你是一个有帮助的 AI 助手。"
        f"用户设备的当前本地时间是：{now}。"
        "回答与日期、时间、星期相关的问题时，请以上述时间为准。"
    )


def chat(api_key: str, prompt: str, model: str = DEFAULT_MODEL) -> str:
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": build_system_prompt()},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.7,
        "max_tokens": 1024,
    }
    request = urllib.request.Request(
        API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["choices"][0]["message"]["content"]


def main():
    api_key = os.environ.get("AGNES_API_KEY", "").strip()
    if not api_key:
        print("请设置环境变量 AGNES_API_KEY")
        print("示例: set AGNES_API_KEY=sk-xxx")
        sys.exit(1)

    prompt = " ".join(sys.argv[1:]).strip() or "用一句话介绍 Agnes AI"
    print(f"模型: {DEFAULT_MODEL}")
    print(f"问题: {prompt}\n")

    try:
        reply = chat(api_key, prompt)
        print("回复:")
        print(reply)
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        print(f"HTTP {e.code}: {err}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"错误: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
