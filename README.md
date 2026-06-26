# FSE 股票交易系统

本仓库是一个可本地联调的多服务股票交易系统，不是单纯的“源码归档仓库”。当前代码实际包含 5 个子系统、多个前端入口、5 个可直接运行的后端，以及配套数据库脚本。

## 1. 系统总览

| 子系统 | 目录 | 运行时 | 默认端口 | 主要入口 |
| --- | --- | --- | --- | --- |
| 账户管理 | `account-management` | Spring Boot + React/Vite | 后端 `8080`，前端 `5173` | `http://localhost:5173/login` |
| 交易管理 | `trade-management` | Java 17 `HttpServer` | `8081` | `http://localhost:8081/` |
| 中央交易 | `central-trading` | Spring Boot | `8082` | `http://localhost:8082/` |
| 网上信息发布 | `online-info-publish` | Spring Boot + Vue/Vite | 后端 `8083`，前端 `3000` | `http://localhost:3000/` |
| 交易客户端 | `trading-client` | Node.js + Express + 原生前端 | `8090` | `http://localhost:8090/` |

## 3. 代码结构

```text
FSE/
├─ account-management/        # 账户管理后端、前端、数据库脚本
├─ central-trading/           # 中央撮合、行情、监控页
├─ online-info-publish/       # 网上信息发布后端与门户前端
├─ trade-management/          # 黑名单、人工审核、管理员后台
├─ trading-client/            # 投资者交易终端 + Node 代理/API
├─ README.md
└─ USER_MANUAL.md
```

## 4. 依赖与前提

### 4.1 必需环境

- JDK 17
- Maven 3.9+
- Node.js 18+ / npm
- MySQL 8.x

### 4.2 按模块附加依赖

- `online-info-publish` 后端还需要 Redis。
- `central-trading`、`trading-client` 的 Kafka 链路可关闭；本地无 Kafka 时可以先禁用。
- `trade-management` 与 `account-management`、`central-trading` 的联调默认是关闭的，需要在 `config.properties` 或环境变量里打开。

## 5. 数据库初始化

### 5.1 已提供脚本的库

建议在同一台 MySQL 上至少建立这 4 个库：

- `account_db`
- `central_trading`
- `stock_trade_management`
- `trading_client`

Windows PowerShell 示例：

```powershell
mysql -u root -p < .\account-management\scripts\01_create_tables.sql
mysql -u root -p account_db < .\account-management\scripts\02_views.sql
mysql -u root -p < .\central-trading\src\main\resources\schema.sql
mysql -u root -p < .\trade-management\sql\schema.sql
mysql -u root -p stock_trade_management < .\trade-management\sql\seed.sql
mysql -u root -p < .\trading-client\database\schema.sql
```

### 5.2 账户管理初始工作人员

仓库没有提供账户管理系统的默认工作人员数据。若要登录 `http://localhost:5173/login`，至少先插入一条工作人员记录。

可直接使用测试口令格式：

```sql
USE account_db;

INSERT INTO staff (username, password_hash, status)
VALUES ('staff001', 'sha256$demo$123456', '正常');
```

说明：

- 这样创建后，登录账号为 `staff001`
- 登录密码为 `123456`
- 首次证书认证码固定为 `CERT-123456`

### 5.3 `online-info-publish` 的数据库缺口

`online-info-publish` 后端默认连接 `stock_publish`，并使用这 3 张表：

- `sync_stock_info`
- `local_user_subscription`
- `kline_5m_data`

仓库内没有这部分建表 SQL。若本地没有现成库，这个后端虽然能编译，但运行到查表时会报缺表错误。重写后的文档按代码现状明确保留这一点，不再假装该模块可以零准备直接启动。

## 6. 推荐启动顺序

### 6.1 账户管理后端

```powershell
$env:ACCOUNT_DB_USERNAME='root'
$env:ACCOUNT_DB_PASSWORD='你的MySQL密码'
cd .\account-management
mvn spring-boot:run
```

默认地址：`http://localhost:8080`

### 6.2 中央交易后端

本地最小启动建议先关闭 Kafka，并保留账户系统 Mock：

```powershell
$env:DB_USER='root'
$env:DB_PASSWORD='你的MySQL密码'
$env:KAFKA_ENABLED='false'
$env:ACCOUNT_API_MOCK='true'
cd .\central-trading
mvn spring-boot:run
```

默认地址：`http://localhost:8082`

如要接入真实账户系统，再改为：

```powershell
$env:ACCOUNT_API_MOCK='false'
$env:ACCOUNT_API_BASE='http://localhost:8080'
```

### 6.3 交易管理后端

`trade-management` 读取当前目录下的 `config.properties`；没有该文件时使用默认值，且默认不联调中央交易和账户管理。

建议在 `trade-management\config.properties` 写入：

```properties
server.port=8081
db.url=jdbc:mysql://localhost:3306/stock_trade_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
db.user=root
db.password=你的MySQL密码
central.enabled=true
central.api-base=http://localhost:8082
account.enabled=true
account.api-base=http://localhost:8080
account.staff-username=staff001
account.staff-password=123456
```

启动：

```powershell
cd .\trade-management
mvn package
java -jar .\target\stock-trade-management-1.0.0.jar
```

默认地址：`http://localhost:8081`

### 6.4 交易客户端

本地最小启动可先关闭 Kafka，只保留 HTTP 代理与本地订单库。

```powershell
$env:DB_HOST='127.0.0.1'
$env:DB_PORT='3306'
$env:DB_USER='root'
$env:DB_PASSWORD='你的MySQL密码'
$env:DB_NAME='trading_client'
$env:ACCOUNT_BACKEND='http://localhost:8080'
$env:CENTRAL_BACKEND='http://localhost:8082'
$env:MGMT_BACKEND='http://localhost:8081'
$env:KAFKA_ENABLED='false'
cd .\trading-client
npm start
```

默认地址：`http://localhost:8090`

### 6.5 账户管理前端

```powershell
cd .\account-management\frontend
npm run dev
```

默认地址：`http://localhost:5173`

说明：

- 前端通过 Vite 代理把 `/api` 转发到 `http://localhost:8080`
- 页面入口实际是 `http://localhost:5173/login`

### 6.6 网上信息发布后端

启动前先确认：

- MySQL 中已有 `stock_publish`
- Redis 已启动
- `sync_stock_info`、`local_user_subscription`、`kline_5m_data` 三表已存在

启动：

```powershell
cd .\online-info-publish
mvn spring-boot:run
```

默认地址：`http://localhost:8083/api/publish`

### 6.7 网上信息发布前端

```powershell
cd .\online-info-publish\publish-frontend
npm run dev
```

默认地址：`http://localhost:3000`

说明：

- 根路径 `/` 是导航门户
- `/home` 是信息发布首页
- 前端通过 Vite 代理把 `/api/publish` 转发到 `http://localhost:8083`

## 7. 默认入口与测试账号

| 系统 | 地址 | 默认账号 |
| --- | --- | --- |
| 账户管理前端 | `http://localhost:5173/login` | 仓库无内置账号，需自行插入 `staff` |
| 交易管理 | `http://localhost:8081/` | `admin / admin123`，来自 `trade-management/sql/seed.sql` |
| 中央交易监控页 | `http://localhost:8082/` | `admin / 123456`，前端页面硬编码 |
| 网上信息发布门户 | `http://localhost:3000/` | 游客可直接访问 |
| 交易客户端 | `http://localhost:8090/` | 需先在账户管理中创建资金账户 |

账户管理与交易客户端首次登录证书认证码都固定为：

```text
CERT-123456
```

## 8. 服务间关系

- `account-management` 是账户、资金、持仓的主数据源。
- `central-trading` 负责撮合、行情快照、订单簿、监控页。
- `trade-management` 负责黑名单和人工审核；只有开启联调配置时，才会把冻结/解冻请求转给账户系统。
- `trading-client` 同时承担投资者前端和 Node 代理，向账户系统、中央交易系统、交易管理系统转发请求。
- `online-info-publish` 前端是整套系统的导航门户；后端会从中央交易系统同步股票和行情，并根据资金账号头部区分游客、普通用户、VIP。

## 9. 最小验证方法

### 9.1 后端联通性

```powershell
Invoke-RestMethod http://localhost:8090/api/client/health
Invoke-RestMethod http://localhost:8082/api/central-trading/stocks
Invoke-RestMethod http://localhost:8083/api/publish/user/me
```

预期：

- `8090` 返回 `ok: true`
- `8082` 返回股票列表
- `8083` 未登录时返回游客角色

### 9.2 页面检查

按顺序打开：

1. `http://localhost:3000/`
2. `http://localhost:5173/login`
3. `http://localhost:8090/`
4. `http://localhost:8081/`
5. `http://localhost:8082/`

### 9.3 基本联调流程

1. 在账户管理创建证券账户和资金账户，并为资金账户充值。
2. 用该资金账户登录交易客户端。
3. 在交易客户端查询股票、提交委托。
4. 在中央交易监控页查看订单和成交事件。
5. 在交易管理页查看黑名单、待人工审核记录。
6. 从网上信息发布门户跳转到各子系统，确认地址与角色联动正常。

