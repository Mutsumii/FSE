# 交易系统管理业务

浙江大学软件工程基础课程大作业，股票交易系统的交易系统管理模块。

## 功能说明

- 管理员注册、登录、修改密码
- 查看授权股票列表
- 查看股票买卖委托队列
- 维护交易黑名单
- 黑名单以证件号码为主键，字段类型与账户管理系统统一为 `VARCHAR(50)`
- 对外提供黑名单查询接口
- 对交易客户端提供委托审查接口
- 大额委托、频繁委托进入人工核验队列
- 管理员人工通过或拒绝待核验委托
- 可选接入中央交易系统真实股票和委托簿接口
- 普通管理员按股票授权，超级管理员维护权限
- PBKDF2 随机盐密码哈希、登录 token 过期、管理员审计日志
- 兼容交易客户端当前传参：`direction` 可替代 `side`，缺省 `reviewId/orderId/amount` 会自动生成或计算
- 红、白、黑配色的管理员前端页面

## 技术栈

- Java 17+
- JDK 内置 `HttpServer`
- JDBC
- MySQL
- Maven
- HTML/CSS/JavaScript

## 目录结构

```text
API_CONTRACT.md                 完整接口契约
config.properties.example       配置文件模板，不包含个人密码
pom.xml                         Maven 配置
sql/
  schema.sql                    建库建表脚本
  seed.sql                      初始数据
  migration_20260615_manual_review.sql
                                旧库升级到人工核验功能的迁移脚本
  migration_20260620_security_audit.sql
                                旧库升级密码字段和审计日志的迁移脚本
  migration_20260620_blacklist_id_card.sql
                                旧黑名单升级为身份证主键的迁移脚本
  migration_20260620_review_contract.sql
                                升级证件号码长度并增加委托股票名称的迁移脚本
src/main/java/                  Java 后端源码
web/                            管理员前端页面
```

注意：本地运行时需要自己创建 `config.properties`。不要把 `config.properties` 发给别人，因为里面会写本机 MySQL 密码。

## 1. 启动 MySQL

Windows 上如果 MySQL 服务没有启动，可以用管理员 PowerShell 执行：

```powershell
Start-Service MySQL80
```

检查状态：

```powershell
Get-Service MySQL80
```

看到 `Running` 即可。

如果你的 MySQL 服务名不是 `MySQL80`，可以查找：

```powershell
Get-Service | Where-Object { $_.Name -like "*mysql*" -or $_.DisplayName -like "*mysql*" }
```

## 2. 初始化数据库

进入 MySQL：

```powershell
mysql -u root -p
```

如果提示找不到 `mysql` 命令，需要使用 `mysql.exe` 的完整路径，或把 MySQL 的 `bin` 目录加入 PATH。

在 MySQL 中执行：

```sql
SET NAMES utf8mb4;
SOURCE ./sql/schema.sql;
SOURCE ./sql/seed.sql;
```

如果你的 MySQL 客户端不支持相对路径，可以改成脚本的绝对路径，或者打开 `.sql` 文件复制内容执行。

如果之前已经执行过旧版 `schema.sql`，需要额外执行一次迁移脚本：

```sql
SET NAMES utf8mb4;
SOURCE ./sql/migration_20260615_manual_review.sql;
SOURCE ./sql/migration_20260620_security_audit.sql;
SOURCE ./sql/migration_20260620_blacklist_id_card.sql;
SOURCE ./sql/migration_20260620_review_contract.sql;
```

默认数据库名：

```text
stock_trade_management
```

## 3. 配置数据库连接

复制配置模板：

```powershell
Copy-Item config.properties.example config.properties
```

修改 `config.properties`：

```properties
server.port=8081
db.url=jdbc:mysql://localhost:3306/stock_trade_management?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
db.user=root
db.password=你的MySQL密码

# 单模块演示保持 false；与中央交易系统联调时改为 true
central.enabled=true
central.api-base=http://localhost:8082

auth.token-minutes=30

# 与账户系统联调冻结/解冻时开启
account.enabled=true
account.api-base=http://localhost:8080
account.staff-username=账户系统工作人员用户名
account.staff-password=账户系统工作人员密码
```

也可以用环境变量覆盖：

```text
SERVER_PORT
DB_URL
DB_USER
DB_PASSWORD
CENTRAL_ENABLED
CENTRAL_API_BASE
AUTH_TOKEN_MINUTES
ACCOUNT_ENABLED
ACCOUNT_API_BASE
ACCOUNT_STAFF_USERNAME
ACCOUNT_STAFF_PASSWORD
```

## 4. 编译和启动

在项目根目录执行：

```powershell
mvn package
java -jar target/stock-trade-management-1.0.0.jar
```

启动成功后会看到：

```text
Trade management server started at http://localhost:8081
```

浏览器访问：

```text
http://localhost:8081
```

默认管理员：

```text
用户名：admin
密码：admin123
```

也可以在登录页切换到“注册”，创建新的管理员账号。新注册账号默认为普通 `ADMIN`，需要超级管理员在“管理员权限”区域授权股票后才能进行股票管理操作。

## 5. 中央交易系统联调

先启动中央交易系统并确认：

```text
http://localhost:8082/api/central-trading/stocks
```

然后在本模块 `config.properties` 中设置：

```properties
central.enabled=true
central.api-base=http://localhost:8082
```

开启后以下功能不再使用本地模拟数据，而是实时调用中央交易系统：

- 股票列表和最新价格
- 买卖委托簿
- 股票列表与最新价格
- 实时买卖委托簿

中央交易系统未启动时，这些管理操作会返回调用失败。需要单模块演示时将 `central.enabled` 改回 `false`。

## 6. 黑名单接口测试

测试命中黑名单：

```powershell
Invoke-RestMethod "http://localhost:8081/api/trade-management/blacklist/check?idCardNo=330101199001010011"
```

预期：

```json
{
  "success": true,
  "data": true,
  "message": null
}
```

测试未命中黑名单：

```powershell
Invoke-RestMethod "http://localhost:8081/api/trade-management/blacklist/check?idCardNo=330102199202020022"
```

预期 `data=false`。

## 7. 委托审查接口测试

### 6.1 正常委托自动通过

```powershell
$id = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$body = @{
  reviewId = "R-normal-$id"
  orderId = "O-normal-$id"
  accountId = "6222026000000098"
  fundAccountNo = "6222026000000098"
  securityAccountNo = "A000098"
  idCardNo = "330103199303030033"
  userName = "王五"
  stockCode = "600519"
  side = "BUY"
  price = 100.00
  quantity = 10
  amount = 1000.00
  clientTime = "2026-06-15T10:00:00+08:00"
} | ConvertTo-Json

$result = Invoke-RestMethod `
  -Uri "http://localhost:8081/api/trade-management/orders/review" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body

$result.data | ConvertTo-Json -Depth 5
```

预期：

```json
{
  "approved": true,
  "reviewStatus": "AUTO_APPROVED"
}
```

### 6.2 大额委托进入人工核验

```powershell
$id = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
$body = @{
  reviewId = "R-large-$id"
  orderId = "O-large-$id"
  accountId = "6222026000000099"
  fundAccountNo = "6222026000000099"
  securityAccountNo = "A000099"
  userName = "李四"
  stockCode = "600519"
  side = "BUY"
  price = 1001.00
  quantity = 100
  amount = 100100.00
  clientTime = "2026-06-15T10:00:00+08:00"
} | ConvertTo-Json

$result = Invoke-RestMethod `
  -Uri "http://localhost:8081/api/trade-management/orders/review" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body

$result.data | ConvertTo-Json -Depth 5
```

预期：

```json
{
  "approved": false,
  "reviewStatus": "PENDING_MANUAL",
  "rejectCode": "RISK_LIMIT_EXCEEDED"
}
```

刷新管理员页面的“待人工核验”区域，可以看到该委托。

### 6.3 频繁委托进入人工核验

```powershell
$id = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
1..6 | ForEach-Object {
  $i = $_
  $body = @{
    reviewId = "R-frequent-$id-$i"
    orderId = "O-frequent-$id-$i"
    accountId = "6222026000000088"
    fundAccountNo = "6222026000000088"
    securityAccountNo = "A000088"
    userName = "赵六"
    stockCode = "600519"
    side = "BUY"
    price = 10.00
    quantity = 10
    amount = 100.00
    clientTime = "2026-06-15T10:0${i}:00+08:00"
  } | ConvertTo-Json

  $result = Invoke-RestMethod `
    -Uri "http://localhost:8081/api/trade-management/orders/review" `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $body

  [PSCustomObject]@{
    index = $i
    approved = $result.data.approved
    reviewStatus = $result.data.reviewStatus
    reason = $result.data.reason
  }
}
```

预期：前 5 笔 `AUTO_APPROVED`，第 6 笔 `PENDING_MANUAL`。

## 8. 管理员人工核验

1. 浏览器打开 `http://localhost:8081`。
2. 使用 `admin/admin123` 登录，或注册新管理员。
3. 在“待人工核验”区域查看大额或频繁交易委托。
4. 点击“通过”或“拒绝”，填写说明。
5. 处理后该记录会从待核验列表中移除。

## 9. 管理员权限与审计

超级管理员登录后可以看到：

- “管理员权限”：设置账号角色，并为普通管理员分配可管理股票代码。
- “审计日志”：查看暂停、重启、涨跌停、黑名单、人工核验、权限修改和密码修改记录。

默认 `admin/admin123` 是超级管理员。旧版 SHA-256 密码在第一次成功登录后会自动升级为 PBKDF2 随机盐哈希。登录 token 默认 30 分钟过期。暂停、重启和涨跌幅设置当前不在管理前端展示。

## 10. 给交易客户端的地址配置

交易客户端前端可配置：

```js
localStorage.setItem("managementApiBase", "http://localhost:8081");
location.reload();
```

## 11. 交易客户端如何发送审核

交易客户端在提交中央交易系统之前，通过 HTTP 调用：

```http
POST http://<交易管理系统IP>:8081/api/trade-management/orders/review
```

自动通过或拒绝会在该 HTTP 响应中立即返回。进入人工核验时，客户端保存响应中的 `reviewId`，并轮询：

```http
GET /api/trade-management/reviews/{reviewId}
```

直到 `reviewStatus` 变为 `MANUAL_APPROVED` 或 `MANUAL_REJECTED`。

相同 `orderId` 的网络重试会返回第一次审核结果，不会创建重复审核记录。

## 12. 模块边界

交易管理系统负责委托审查、人工核验、股票交易状态管理、管理员权限和审计。

账户冻结和解冻功能因账户系统接口暂不可用而暂时撤下。
