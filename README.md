# NectarProxy

NectarProxy 是一个基于 Spring Boot + RSocket 的“云-边”反向代理网关方案：
- 网关（Gateway）统一暴露 HTTP 入口与 RSocket（WebSocket）通道。
- Agent 与网关建立长连接，网关将请求通过 RSocket 转发给 Agent，由 Agent 本地执行 HTTP 调用并回传响应。
- 路由/目标/ACL/超时等策略统一在网关配置，Agent 专注执行与回传。

## 模块结构
- `common/` 公共 DTO（`ProxyRequest`, `ProxyResponse`）。
- `gateway/` 网关服务（HTTP + RSocket，策略中心，ACL/超时/路由）。
- `agent/` 边缘代理（RSocket 客户端，执行本地 HTTP 并回传；支持断线重连/重注册）。

## 快速开始（开发）
1) 启动 Gateway
```
./gradlew :gateway:bootRun
```
默认端口：`9000`；RSocket WS 路径：`/rsocket`

2) 启动 Agent（非 Web 模式）
```
./gradlew :agent:bootRun
```
Agent 将根据 `agent.gateway-url` 连接网关，并注册 `client-id`。

3) 发起请求
```
curl "http://localhost:9000/proxy/<client-id>/ip"
```
网关将按配置为该 `client-id` 选择目标地址并下发给 Agent，Agent 执行并透传下游响应。

## 网关配置（示例）
文件：`gateway/src/main/resources/application.yaml` 或容器挂载的 `/config/application.yaml`
```yaml
server:
  port: 9000
spring:
  rsocket:
    server:
      transport: websocket
      mapping-path: /rsocket

logging:
  file:
    name: /logs/gateway.log
  level:
    io.github.halfhoney.gateway: info

gateway:
  proxy:
    default-timeout-ms: 30000
    default-target-base-url: https://httpbin.org
    clients:
      client-001:
        enabled: true
        default-target-base-url: http://127.0.0.1:9000
        routes:
          - prefix: /open
            target-base-url: http://127.0.0.1:7001

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

- 超时优先级：请求头 `X-Timeout-Ms` > `clients[clientId].timeout-ms` > `default-timeout-ms`
- 目标选择：`clients[clientId].routes` 命中 > `clients[clientId].default-target-base-url` > `default-target-base-url`
- ACL：可选的 `enabled/allowed-methods/allowed-path-prefixes`

## Agent 配置（示例）
文件：`agent/src/main/resources/application.yaml`
```yaml
spring:
  main:
    web-application-type: none
  threads:
    virtual:
      enabled: true

agent:
  client-id: client-001
  gateway-url: ws://localhost:9000/rsocket
  default-timeout-ms: 30000

logging:
  level:
    io.github.halfhoney.gateway.agent: info
```

- Agent 不做路由决策，仅使用网关下发的 `targetBaseUrl`。若缺失则返回 502。
- 已实现：断线指数退避重连 + 自动重注册。
- 已实现：下游响应透明透传（状态码/响应头/响应体）。

## Docker 部署（Gateway）
见 `gateway/docker/README.md`，包含：
- `Dockerfile` 与 `docker-compose.yml`
- `/config` 外挂配置与 `/logs` 日志目录
- 内存限制、JVM 参数、健康检查等建议

快速运行（示例）：
```
cd gateway/docker
# 构建镜像
docker compose build
# 后台启动
docker compose up -d
```

## 离线镜像导入/导出
- 导出：`docker save nectar-proxy-gateway:latest -o gateway-image.tar`
- 导入：`docker load -i gateway-image.tar`
详见 `gateway/docker/README.md`。

## 生产建议
- 外部进程守护（systemd/Docker/K8s）配合 `-XX:+ExitOnOutOfMemoryError`。
- 网关集中配置，Agent 仅执行，避免在 Agent 存放敏感路由。
- 指标与日志采集（Actuator/Prometheus/ELK）。
- 对重试加 jitter，必要时启用 RSocket Resumption（需服务端支持）。

## 开发注意
- 包名：`io.github.halfhoney.gateway.*`
- Java 21（Temurin JDK）
- 构建：`./gradlew build`，子模块 jar 位于各自 `build/libs/`

## License
TBD.
