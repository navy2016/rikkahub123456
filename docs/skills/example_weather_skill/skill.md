---
name: 天气查询
version: 1.0.0
author: RikkaHub
description: 让 AI 能够查询任意城市的实时天气信息，包括温度、湿度、风速等详细数据。
tags: [weather, api, utility, location]
tools:
  - name: get_weather
    description: 获取指定城市的当前天气信息
    parameters:
      city: 城市名称（中文或英文）
  - name: get_forecast
    description: 获取指定城市未来几天的天气预报
    parameters:
      city: 城市名称
      days: 预报天数（1-7天）
system_prompt: |
  你拥有查询实时天气信息的能力。当用户询问天气相关问题时：
  1. 使用 get_weather 工具获取当前天气
  2. 使用 get_forecast 工具获取天气预报
  3. 以自然、友好的方式向用户展示天气信息
  4. 可以主动提供穿衣建议或出行建议
injections:
  - name: 天气提示注入
    content: 记住，当用户询问天气时，使用天气查询工具获取最新信息。
    position: after_system_prompt
    priority: 10
---

# 天气查询技能

## 功能介绍

本技能让 AI 助手具备查询实时天气的能力，支持：

- 🌡️ 实时温度查询
- 💧 湿度信息
- 🌬️ 风速风向
- ☀️ 天气状况（晴、雨、雪等）
- 📅 未来7天天气预报

## 使用方法

直接向 AI 询问天气即可，例如：

- "北京今天天气怎么样？"
- "上海明天会下雨吗？"
- "广州未来三天的天气如何？"

## 数据来源

天气数据来自公开的天气 API，数据更新频率约为每分钟一次。

## 注意事项

1. 城市名称支持中文和英文
2. 天气预报最多支持7天
3. 部分小城市可能无法查询
