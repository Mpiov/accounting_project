# AutoBook 后端服务镜像。
# 使用 slim Python 镜像，适合当前轻量服务器部署。
FROM python:3.12-slim

# 设置工作目录，后续命令都在应用目录下执行。
WORKDIR /app

# 先复制依赖清单，利用 Docker 层缓存减少重复安装时间。
COPY requirements.txt .
# 腾讯云服务器访问官方 PyPI 偶发超时，部署镜像内使用腾讯云镜像源提高稳定性。
RUN pip install --no-cache-dir --timeout 120 \
    -i https://mirrors.cloud.tencent.com/pypi/simple \
    --trusted-host mirrors.cloud.tencent.com \
    -r requirements.txt

# 复制应用代码，保持镜像内路径简单清晰。
COPY app ./app

# 暴露容器内 API 端口，宿主机通过 Nginx 反向代理访问。
EXPOSE 8000

# 启动 FastAPI 服务。
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
