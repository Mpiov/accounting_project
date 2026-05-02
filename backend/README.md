# AutoBook Backend

这是当前项目的后端技术骨架，负责手机号登录、服务器权威会员状态、支付订单占位、训练样本上传、全局模型注册和模型下发。

## 本地或服务器环境变量

- `AUTOBOOK_DB_PATH`：SQLite 数据库路径，容器默认 `/data/autobook.sqlite3`。
- `AUTOBOOK_TOKEN_SECRET`：访问令牌签名密钥，生产环境必须覆盖。
- `AUTOBOOK_DEV_SMS_CODE`：开发阶段固定验证码，默认 `123456`。

## 当前接口

- `GET /health`
- `POST /v1/auth/sms-code`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `GET /v1/membership/status`
- `POST /v1/payments/membership-orders`
- `GET /v1/payments/membership-orders/{orderId}`
- `POST /v1/cloud-learning/training-samples`
- `GET /v1/cloud-learning/global-model`

会员默认是免费状态，只有服务器数据库把账号标记为有效会员后，机器学习模型接口才会放行。训练样本会进入服务器全局样本池，后续训练任务基于所有用户贡献的数据发布统一全局模型，不生成单用户定制模型。上传接口优先按 `userId + sampleLocalId` 更新同一条本地样本，用户后续纠错会覆盖服务器旧样本。

模型权重使用客户端稳定特征键，例如 `source.trusted_payment`、`amount.with_currency`、`semantic.transaction_scene` 和 `risk.asset_valuation`。客户端会保存下发的版本、权重和风险阈值，并在会员解析时优先使用服务器全局模型。

## 全局模型训练

训练任务运行在独立 `trainer` 容器中，默认北京时间每天凌晨 3 点检查一次。它不是每天强制发布模型，而是按以下规则执行：

- 新增有效标签样本少于 `AUTOBOOK_TRAINING_MIN_NEW_SAMPLES` 时跳过。
- 总有效标签样本少于 `AUTOBOOK_TRAINING_MIN_TOTAL_SAMPLES` 时跳过。
- 正负样本任一类别少于 `AUTOBOOK_TRAINING_MIN_PER_CLASS` 时跳过。
- 验证准确率低于 `AUTOBOOK_TRAINING_MIN_ACCURACY` 时跳过。
- 负样本误判率高于 `AUTOBOOK_TRAINING_MAX_FALSE_POSITIVE_RATE` 时跳过。

手动检查一次训练：

```bash
cd /opt/autobook
sudo docker compose exec -T api python -m app.admin_cli run-global-training
```

查看最近训练任务：

```bash
cd /opt/autobook
sudo docker compose exec -T api python -m app.admin_cli show-training-jobs
```

前期样本很少时可以在管理后台手动触发强制训练，但正负样本必须同时存在，否则逻辑回归无法训练。

## 临时会员管理

当前阶段不开放公网管理接口，只允许通过服务器 SSH 执行容器内命令，或通过 SSH 隧道访问本地管理后台。

```bash
cd /opt/autobook
sudo docker compose exec -T api python -m app.admin_cli grant-member --phone 13800138000 --days 30
sudo docker compose exec -T api python -m app.admin_cli show-member --phone 13800138000
sudo docker compose exec -T api python -m app.admin_cli revoke-member --phone 13800138000
```

`grant-member` 默认同时开启机器学习置信度和云端学习；如需只开会员身份，可以追加 `--disable-model-confidence` 或 `--disable-cloud-learning`。

查看当前全局模型：

```bash
cd /opt/autobook
sudo docker compose exec -T api python -m app.admin_cli show-global-model
```

## 训练环境自检

机器学习训练环境安装在后端 Docker 镜像内，不直接污染服务器宿主机。

```bash
cd /opt/autobook
sudo docker compose exec -T api python -m app.training_env_check
```

该命令会检查 `numpy`、`scipy`、`scikit-learn`、`joblib`、`jieba` 是否可导入，并执行一次最小逻辑回归训练冒烟。

## 本地管理后台

管理后台不通过公网 Nginx 暴露，Docker 只绑定服务器本机 `127.0.0.1:8080`。

```bash
ssh -i E:\accounting_project\.ssh\codex_tencent_lighthouse_nopass -L 8080:127.0.0.1:8080 ubuntu@122.152.220.19
```

然后在本机浏览器访问：

```text
http://127.0.0.1:8080/admin
```

后台密码来自服务器 `/opt/autobook/.env` 的 `AUTOBOOK_ADMIN_PASSWORD`。
