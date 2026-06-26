# 交易系统管理业务接口契约

本文档定义交易系统管理业务对其他小组提供的 HTTP JSON 接口。交易系统管理业务面向交易所内部管理员，同时为交易客户端提供委托审查和黑名单查询能力。

## 1. 基本约定

- 协议：HTTP
- 数据格式：JSON UTF-8
- 默认服务地址：`http://localhost:8081`
- 时间格式：ISO 8601，例如 `2026-06-15T10:00:00+08:00`
- 通用成功响应：

```json
{
  "success": true,
  "data": {}
}
```

- 通用失败响应：

```json
{
  "success": false,
  "message": "错误原因"
}
```

## 2. 委托审查接口

交易客户端发起买入/卖出委托后，先调用交易系统管理业务进行审查；只有审查通过，交易客户端才继续冻结资金/股票并提交中央交易系统。

交易系统管理业务只负责审查，不负责冻结资金、不负责撮合成交、不负责写入中央交易委托。

### 2.1 请求

```http
POST /api/trade-management/orders/review
Content-Type: application/json
```

```json
{
  "reviewId": "R1710000000000",
  "orderId": "C1710000000000",
  "accountId": "6222026000000001",
  "fundAccountNo": "6222026000000001",
  "securityAccountNo": "A000001",
  "userName": "张三",
  "stockCode": "600519",
  "stockName": "贵州茅台",
  "side": "BUY",
  "price": 1688.35,
  "quantity": 100,
  "amount": 168835.00,
  "clientTime": "2026-06-15T10:00:00+08:00"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `reviewId` | string | 是 | 本次审查请求 ID，由交易客户端生成 |
| `orderId` | string | 是 | 委托号，由交易客户端生成 |
| `accountId` | string | 是 | 兼容字段，默认等同于资金账户号 |
| `fundAccountNo` | string | 是 | 资金账户号 |
| `securityAccountNo` | string | 是 | 证券账户号 |
| `idCardNo` | string | 否 | 证件号码，最长 50 字符，用于审计关联；委托黑名单校验不使用该字段 |
| `userName` | string | 是 | 投资者真实姓名，委托审查按该字段精确匹配黑名单；不是管理员用户名 |
| `stockCode` | string | 是 | 6 位股票代码 |
| `stockName` | string | 建议 | 股票名称，用于审核记录和管理端展示 |
| `side` | string | 是 | `BUY` 或 `SELL` |
| `price` | number | 是 | 委托价格，最多两位小数 |
| `quantity` | number | 是 | 委托数量，单位为股 |
| `amount` | number | 是 | 委托金额，等于 `price * quantity` |
| `clientTime` | string | 是 | 客户端提交审查时间 |

### 2.2 审查通过响应

```json
{
  "success": true,
  "data": {
    "reviewId": "R1710000000000",
    "orderId": "C1710000000000",
    "approved": true,
    "reviewStatus": "AUTO_APPROVED",
    "riskLevel": "LOW",
    "message": "审查通过"
  }
}
```

### 2.3 进入人工核验响应

大额委托或频繁委托不会直接放行，会进入人工核验队列。客户端收到该结果时不能继续发布交易，需要等待管理员人工核验。

```json
{
  "success": true,
  "data": {
    "reviewId": "R1710000000000",
    "orderId": "C1710000000000",
    "approved": false,
    "reviewStatus": "PENDING_MANUAL",
    "riskLevel": "MEDIUM",
    "rejectCode": "RISK_LIMIT_EXCEEDED",
    "reason": "单笔委托金额超过 100000 元，需要人工核验",
    "message": "进入人工核验"
  }
}
```

### 2.4 审查拒绝响应

审查拒绝属于正常业务结果，建议返回 HTTP 200。

```json
{
  "success": true,
  "data": {
    "reviewId": "R1710000000000",
    "orderId": "C1710000000000",
    "approved": false,
    "reviewStatus": "REJECTED",
    "riskLevel": "HIGH",
    "rejectCode": "ACCOUNT_RESTRICTED",
    "reason": "投资者在交易黑名单中"
  }
}
```

### 2.5 审查规则

当前版本实现以下审查规则：

| 规则 | 拒绝码 |
| --- | --- |
| 投资者姓名命中黑名单 | `ACCOUNT_RESTRICTED` |
| 委托价格小于等于 0，或价格超过两位小数 | `PRICE_ABNORMAL` |
| 委托数量小于等于 0 | `QUANTITY_ABNORMAL` |
| `amount` 与 `price * quantity` 不一致 | `OTHER` |
| 重复 `orderId` | `DUPLICATE_ORDER` |
| 单笔委托金额大于 100000 元 | `RISK_LIMIT_EXCEEDED`，进入人工核验 |
| 同一投资者当日已提交 5 笔委托，第 6 笔起 | `FREQUENT_TRADING`，进入人工核验 |

## 3. 黑名单查询接口

该接口供交易客户端或其他业务子系统查询某个投资者是否在黑名单中。

注意：`userName` 指资金账户/证券账户对应的投资者姓名，不是管理员用户名。若调用方只有 `fundAccountNo`、`securityAccountNo` 或 `accountId`，应先在账户子系统中解析出投资者姓名，或在联调版本中同时传入账户号以便管理系统记录审查上下文。

### 3.1 请求

```http
GET /api/trade-management/blacklist/check?idCardNo=330101199001010011
```

为兼容已有调用方，也保留姓名精确查询：

```http
GET /api/trade-management/blacklist/check?userName=张三
```

可选参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `idCardNo` | string | 是 | 投资者身份证号，黑名单主键 |
| `userName` | string | 否 | 旧版本兼容参数，不建议继续使用 |
| `fundAccountNo` | string | 否 | 资金账户号，便于后续扩展和日志追踪 |
| `securityAccountNo` | string | 否 | 证券账户号，便于后续扩展和日志追踪 |

### 3.2 响应

```json
{
  "success": true,
  "data": true
}
```

`data=true` 表示该投资者在黑名单中，`data=false` 表示不在黑名单中。

## 4. 管理员接口

管理员接口需要先登录，后续请求在请求头中携带：

```http
Authorization: Bearer <token>
```

### 4.1 管理员登录

```http
POST /api/admin/login
Content-Type: application/json
```

```json
{
  "username": "admin",
  "password": "admin123"
}
```

响应：

```json
{
  "success": true,
  "data": {
    "token": "token-value",
    "username": "admin",
    "role": "SUPER_ADMIN",
    "expiresAt": "2026-06-20T12:30:00Z"
  }
}
```

### 4.1.1 管理员注册

```http
POST /api/admin/register
Content-Type: application/json
```

```json
{
  "username": "new_admin",
  "password": "admin123",
  "confirmPassword": "admin123"
}
```

响应格式与登录接口一致。新注册管理员默认为 `ADMIN`，需要超级管理员分配可管理股票。

### 4.2 查询可管理股票列表

```http
GET /api/admin/stocks
Authorization: Bearer <token>
```

响应：

```json
{
  "success": true,
  "data": [
    {
      "stockCode": "600519",
      "stockName": "贵州茅台",
      "lastPrice": 1688.35,
      "lastQuantity": 100,
      "status": "TRADING",
      "currentLimitRate": 0.10,
      "nextLimitRate": 0.10
    }
  ]
}
```

### 4.3 查看股票实时交易情况

```http
GET /api/admin/stocks/{stockCode}/orders
Authorization: Bearer <token>
```

响应：

```json
{
  "success": true,
  "data": {
    "stockCode": "600519",
    "stockName": "贵州茅台",
    "lastPrice": 1688.35,
    "lastQuantity": 100,
    "buyOrders": [
      {
        "orderId": "B001",
        "price": 1688.35,
        "quantity": 100,
        "enteredAt": "2026-06-15T10:00:00+08:00"
      }
    ],
    "sellOrders": [
      {
        "orderId": "S001",
        "price": 1689.00,
        "quantity": 200,
        "enteredAt": "2026-06-15T10:01:00+08:00"
      }
    ]
  }
}
```

买指令按价格降序、同价按时间升序排列；卖指令按价格升序、同价按时间升序排列。

### 4.4 设置股票涨跌停限制

```http
POST /api/admin/stocks/{stockCode}/limit-rate
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "nextLimitRate": 0.10
}
```

说明：新限制第二个交易日生效。

### 4.7 查询黑名单

```http
GET /api/admin/blacklist
Authorization: Bearer <token>
```

### 4.8 新增黑名单

```http
POST /api/admin/blacklist
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "userName": "张三",
  "fundAccountNo": "6222026000000001",
  "securityAccountNo": "A000001",
  "reason": "违规交易风控限制"
}
```

### 4.9 移除黑名单

```http
DELETE /api/admin/blacklist/{id}
Authorization: Bearer <token>
```

### 4.10 修改管理员密码

```http
POST /api/admin/password
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "oldPassword": "admin123",
  "newPassword": "newPassword123"
}
```

### 4.11 查询管理员及权限

```http
GET /api/admin/users
Authorization: Bearer <SUPER_ADMIN token>
```

### 4.12 更新管理员权限

```http
POST /api/admin/users/{adminId}/permissions
Authorization: Bearer <SUPER_ADMIN token>
Content-Type: application/json
```

```json
{
  "role": "ADMIN",
  "stockCodes": ["600519", "000001"]
}
```

### 4.13 查询审计日志

```http
GET /api/admin/audit-logs
Authorization: Bearer <SUPER_ADMIN token>
```

### 4.14 查询审核状态

```http
GET /api/trade-management/reviews/{reviewId}
```

该接口公开供交易客户端每 3～5 秒轮询，不需要管理员 token。响应状态如下：

- `PENDING_MANUAL`：`approved=false`，客户端继续等待。
- `MANUAL_APPROVED`：`approved=true`，客户端停止轮询并继续提交委托。
- `MANUAL_REJECTED`：`approved=false`，客户端停止轮询并展示 `reason`。
- 审核记录不存在：HTTP 404，返回 `{"success":false,"message":"审核记录不存在"}`。

人工拒绝会保留委托进入人工核验时的 `rejectCode`，并优先返回管理员填写的拒绝原因。

相同 `orderId` 的重试返回第一次审核结果，不创建重复记录，也不返回 `DUPLICATE_ORDER`。

## 5. 枚举值

`side`：

```text
BUY, SELL
```

`riskLevel`：

```text
LOW, MEDIUM, HIGH
```

`rejectCode`：

```text
ACCOUNT_RESTRICTED
STOCK_RESTRICTED
PRICE_ABNORMAL
QUANTITY_ABNORMAL
RISK_LIMIT_EXCEEDED
FREQUENT_TRADING
DUPLICATE_ORDER
OTHER
```

`reviewStatus`：

```text
AUTO_APPROVED, PENDING_MANUAL, REJECTED, MANUAL_APPROVED, MANUAL_REJECTED
```

`stock.status`：

```text
TRADING
PAUSED
```
