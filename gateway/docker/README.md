# NectarProxy Gateway Docker 部署

目录结构：
```
 gateway/
 └─ docker/
    ├─ Dockerfile
    ├─ docker-compose.yml
    ├─ config/
    │  └─ application.yaml   # 挂载到容器 /config
    └─ logs/                 # 挂载到容器 /logs
```

## 1) 构建 Jar
在项目根目录执行：
```bash
./gradlew :gateway:bootJar
```
产物：`gateway/build/libs/gateway-*.jar`

## 2) 构建镜像并启动
```bash
cd gateway/docker
# 构建镜像（镜像名：nectar-proxy-gateway:latest）
docker compose build
# 后台启动
docker compose up -d
```

## 3) 配置挂载
- 将你的 `application.yaml` 放在 `gateway/docker/config/`，容器内会以 `/config/application.yaml` 加载。
- 建议开启日志文件：
```yaml
logging:
  file:
    name: /logs/gateway.log
```

## 4) 资源限制
`docker-compose.yml` 已示例：
```yaml
deploy:
  resources:
    limits:
      memory: 512M
    reservations:
      memory: 256M
```
可按需调整，并结合 `JAVA_TOOL_OPTIONS`: `-XX:+ExitOnOutOfMemoryError -XX:MaxRAMPercentage=75`。

## 5) 端口与路径
- HTTP 端口：`9000`
- RSocket over WebSocket：`/rsocket`

## 6) 验证
```bash
curl http://localhost:9000/actuator/health
```

## 7) 离线导出/导入
```bash
# 导出
docker save nectar-proxy-gateway:latest -o gateway-image.tar
# 导入
docker load -i gateway-image.tar
```

## 8) 常见问题
- 端口占用：修改 `docker-compose.yml` 的 `ports` 映射。
- 配置未生效：检查挂载目录与 `SPRING_CONFIG_ADDITIONAL_LOCATION`。
- Agent 连接失败：确认网关在 `9000` 端口、RSOCKET 路径 `/rsocket`，并查看网关注册日志。
