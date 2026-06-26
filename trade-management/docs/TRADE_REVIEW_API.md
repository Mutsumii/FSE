# 交易管理系统委托审查接口

本文档定义交易客户端在发布交易委托前调用交易管理系统进行风控审查的接口。

## 基本约定

- 协议：HTTP
- 数据格式：JSON UTF-8
- 默认服务地址：`http://localhost:8081`
- 交易客户端必须先调用本接口，审查通过后才能继续发布交易。

## 委托审查

```http
POST /api/trade-management/orders/review
Content-Type: application/json
```

请求示例：

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

## 审查结果

### 自动通过

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

### 进入人工核验

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

当 `reviewStatus` 为 `PENDING_MANUAL` 时，交易客户端不能继续发布交易，需要等待交易管理系统管理员人工核验。

### 直接拒绝

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

## 当前审查规则

| 规则 | 结果 |
| --- | --- |
| 投资者命中黑名单 | 直接拒绝 |
| 股票代码不存在 | 直接拒绝 |
| 委托价格小于等于 0，或超过两位小数 | 直接拒绝 |
| 委托数量小于等于 0 | 直接拒绝 |
| 委托金额与价格乘数量不一致 | 直接拒绝 |
| 单笔委托金额大于 100000 元 | 进入人工核验 |
| 同一投资者当日已提交 5 笔委托，第 6 笔起 | 进入人工核验 |
| 其他正常委托 | 自动通过 |

## 字段说明

| 字段 | 说明 |
| --- | --- |
| `approved` | 是否允许客户端继续发布交易 |
| `reviewStatus` | `AUTO_APPROVED`、`PENDING_MANUAL`、`REJECTED` |
| `riskLevel` | `LOW`、`MEDIUM`、`HIGH` |
| `rejectCode` | 拒绝或进入人工核验的原因码 |
| `reason` | 中文原因说明 |

客户端判断时，建议以 `approved` 为主：

- `approved=true`：可以继续发布交易。
- `approved=false` 且 `reviewStatus=PENDING_MANUAL`：暂扣，等待人工核验。
- `approved=false` 且 `reviewStatus=REJECTED`：拒绝交易。

## 人工审核结果查询

进入人工核验后，客户端保存返回的 `reviewId` 并轮询：

```http
GET /api/trade-management/reviews/{reviewId}
```

该接口不需要管理员登录或 `Authorization` 请求头。建议每 3～5 秒查询一次：

- `PENDING_MANUAL`：继续轮询，`message` 为“等待管理员人工审核”。
- `MANUAL_APPROVED`：停止轮询，`approved=true`，继续向中央交易系统提交。
- `MANUAL_REJECTED`：停止轮询，`riskLevel=HIGH`，展示管理员填写的 `reason`。
- 审核记录不存在：返回 HTTP 404 和“审核记录不存在”。

本方案由交易客户端主动轮询，不使用服务端回调。

`userName` 为必填字段，服务端使用姓名精确匹配黑名单。缺失时返回 HTTP 400。

相同 `orderId` 重试时，服务端返回第一次审核结果，不返回 `DUPLICATE_ORDER`。
