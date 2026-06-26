# 交易管理系统黑名单查询接口

本文档只定义交易管理系统当前对外提供的黑名单查询接口，用于其他子系统判断某个投资者是否被限制交易。

## 黑名单查询

### 请求

```http
GET /api/trade-management/blacklist/check?idCardNo=330101199001010011
```

兼容按投资者姓名精确查询：

```http
GET /api/trade-management/blacklist/check?userName=张三
```

身份证号与姓名都提供时优先按身份证号查询。委托审查流程仍使用请求中的 `userName` 精确匹配黑名单。

### 请求参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `idCardNo` | string | 是 | 投资者证件号码，最长 50 字符，也是黑名单数据主键 |
| `userName` | string | 否 | 旧版兼容参数，不建议新调用方继续使用 |

说明：

- 姓名可能重名，因此正式联调统一按身份证号查询。
- 如果调用方只有账户号，需要先在账户系统中查询对应身份证号。

### 成功响应

```json
{
  "success": true,
  "data": true
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 接口是否调用成功 |
| `data` | boolean | `true` 表示在黑名单中，`false` 表示不在黑名单中 |

### 示例

请求：

```http
GET http://localhost:8081/api/trade-management/blacklist/check?idCardNo=330101199001010011
```

响应：

```json
{
  "success": true,
  "data": true
}
```

请求：

```http
GET http://localhost:8081/api/trade-management/blacklist/check?idCardNo=330102199202020022
```

响应：

```json
{
  "success": true,
  "data": false
}
```

### 异常响应

缺少 `idCardNo` 参数：

```json
{
  "success": false,
  "data": null,
  "message": "缺少必填参数 idCardNo"
}
```

交易管理系统暂时不可用：

```json
{
  "success": false,
  "data": null,
  "message": "交易管理系统暂时不可用"
}
```
