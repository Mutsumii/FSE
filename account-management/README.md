# 账户管理子系统 README

本目录是股票交易系统中的账户管理子系统源码，负责证券账户、资金账户、投资者资料、工作人员认证、对外账户接口、管理员冻结与审计接口等业务。

本文档基于当前代码实际结构编写，重点说明：

1. 代码结构
2. 数据库表结构与关系
3. 内部接口
4. 外部接口
5. 关键业务规则
6. 错误码

## 1. 子系统职责

账户管理子系统当前承担的核心职责包括：

- 工作人员登录与首次证书认证
- 证券账户开户、挂失、补办、销户
- 资金账户开户、存取款、改密、挂失、补办、销户
- 投资者信息修改
- 证券账户与资金账户绑定、解绑
- 资金流水与持仓变动记录
- Dashboard 统计与操作日志查询
- 向交易客户端提供资金账户登录、资金快照、持仓快照接口
- 向中央交易系统提供资金变更、持仓变更落账接口
- 提供管理员冻结、解冻、强制销户、年结息、审计查询接口

## 2. 技术栈

### 后端

- Java 17
- Spring Boot 3.2.5
- Spring Web
- Spring Validation
- MySQL
- Lombok

后端构建文件：

- `pom.xml`

后端配置入口：

- `src/main/resources/application.yml`

### 前端

- React
- Vite
- React Router
- Tailwind 风格组件

前端构建文件：

- `frontend/package.json`

前端路由入口：

- `frontend/src/app/routes.tsx`

## 3. 目录结构

当前目录结构如下：

```text
account-management/
├─ frontend/
├─ scripts/
├─ src/
├─ pom.xml
└─ README.md
```

### 3.1 `src/main/java/account`

后端主代码目录。

#### `common`

通用基础类。

主要包含：

- `Result.java`：统一返回体
- `ErrorCode.java`：错误码定义
- `AuthHeaders.java`：认证请求头常量
- `ResultPayloadMapper.java`：将业务对象平铺到统一返回结构

#### `config`

Spring 配置相关代码。

#### `controller`

控制器层，直接定义 HTTP 接口。

分为两个子目录：

- `controller/internal`：内部工作人员接口
- `controller/external`：外部联调接口

内部控制器：

- `StaffController.java`：工作人员登录、证书认证、停用工作人员
- `DashboardController.java`：统计信息、最近日志、日志查询
- `SecurityAccountController.java`：证券账户内部业务
- `FundAccountController.java`：资金账户内部业务

外部控制器：

- `ExternalFundController.java`：投资者资金账户登录、快照、改密
- `ExternalSecurityController.java`：投资者证券持仓快照
- `ExternalTradeController.java`：中央交易系统回写资金与持仓变化
- `AdminController.java`：冻结、解冻、年结息、强制销户
- `AuditController.java`：审计日志查询

#### `dao`

数据访问层，直接和数据库交互。

主要文件：

- `DaoRegistry.java`：DAO 聚合入口
- `BaseJdbcDao.java`：JDBC 基础能力
- `InvestorDao.java`：投资者表
- `StaffDao.java`：工作人员表
- `SecurityAccountDao.java`：证券账户表
- `FundAccountDao.java`：资金账户表
- `FundTransactionLogDao.java`：资金流水表
- `HoldingDao.java`：持仓表
- `HoldingChangeLogDao.java`：持仓变化表
- `OperationLogDao.java`：操作日志表
- `LoginCertificateStateDao.java`：首次证书认证状态表

子目录：

- `dao/core`：连接、事务、RowMapper 等底层 JDBC 抽象
- `dao/model`：DAO 层使用的领域模型与枚举

#### `dto`

请求对象和响应对象定义。

这些类主要负责：

- 接收控制器请求参数
- 输出标准化业务响应
- 约束字段名与 JSON 结构

按业务大致可分为：

- 工作人员认证 DTO
- 证券账户 DTO
- 资金账户 DTO
- Dashboard / 日志 DTO
- 外部联调 DTO
- 管理员 / 审计 DTO

#### `enums`

应用层枚举定义，例如账户类型、冻结类型、账户状态等。

#### `exception`

异常处理。

主要文件：

- `GlobalExceptionHandler.java`：统一异常转错误响应

#### `integration`

与外部系统对接的相关代码。

#### `service`

业务实现层，负责完整业务流程、事务、校验、调用 DAO。

主要文件：

- `StaffServiceImpl.java`：工作人员登录、证书认证、停用
- `SecurityAccountServiceImpl.java`：证券账户主业务
- `FundAccountServiceImpl.java`：资金账户主业务
- `AdminServiceImpl.java`：管理员冻结、解冻、结息、强制销户
- `AuditServiceImpl.java`：审计查询
- `OperationLogViewMapper.java`：日志视图映射
- `AccountNumberGenerator.java`：账户号生成
- `PasswordUtil.java`：密码处理
- `AccountBlacklistSupport.java`：黑名单桥接支持
- `InMemoryStaffAuthTokenService.java`：工作人员 token 内存实现
- `InMemoryClientAuthTokenService.java`：投资者 token 内存实现

子目录：

- `service/api`：服务接口定义

包括：

- `StaffService`
- `StaffAuthTokenService`
- `ClientAuthTokenService`
- `SecurityAccountService`
- `FundAccountService`
- `AdminService`
- `AuditService`

### 3.2 `src/main/resources`

运行配置目录。

主要文件：

- `application.yml`

当前实际可见配置包括：

- 服务端口：`8080`
- 数据库连接：`account.datasource.*`
- 黑名单桥接地址：`account.blacklist.base-url`

数据库默认 URL 指向：

- `jdbc:mysql://localhost:3306/account_db`

数据库用户名和密码通过环境变量注入：

- `ACCOUNT_DB_USERNAME`
- `ACCOUNT_DB_PASSWORD`

### 3.3 `scripts`

数据库脚本目录。

主要文件：

- `01_create_tables.sql`：核心表定义
- `02_views.sql`：查询视图
- `04_optional_procedures.sql`：可选存储过程
- `mysql_schema_current.sql`：当前整合版 schema

说明：

- 当前后端长期实际使用的库名是 `account_db`
- `02_views.sql` 中出现的 `stock_account_system` 需要按实际部署库名调整

### 3.4 `frontend/src/app`

账户管理前端主代码目录。

#### `pages`

页面级组件。

主要页面：

- `Login.tsx`：工作人员登录页
- `CertificateAuth.tsx`：首次登录证书认证页
- `Dashboard.tsx`：总览页、快捷业务入口、日志查询
- `SecuritiesAccounts.tsx`：证券账户列表与业务办理页
- `FundAccounts.tsx`：资金账户列表与业务办理页

#### `components`

通用页面组件与 UI 组件。

主要包括：

- `Layout.tsx`：整体布局
- `RequireAuth.tsx`：登录态校验
- `components/ui/*`：通用 UI 组件

#### `lib`

前端接口访问封装。

主要文件：

- `api.ts`

负责：

- 保存工作人员会话
- 保存首次证书认证的待完成状态
- 调用内部接口
- 调用 Dashboard 接口
- 调用黑名单检查接口

## 4. 统一返回格式

后端统一返回格式由 `Result.java` 定义。

基础结构如下：

```json
{
  "code": 0,
  "message": "成功",
  "data": {},
  "extra_field": "..."
}
```

说明：

- `code`：错误码，`0` 表示成功
- `message`：提示信息
- `data`：通用数据区
- 某些接口会把业务字段直接平铺到顶层，而不只放在 `data` 中

## 5. 数据库设计

## 5.1 表清单

当前核心表包括：

1. `investor`
2. `staff`
3. `security_account`
4. `fund_account`
5. `fund_transaction_log`
6. `holding`
7. `holding_change_log`
8. `operation_log`
9. `login_certificate_state`

## 5.2 表结构说明

### `investor`

投资者基础信息表。

主要字段：

- `investor_id INT`：主键
- `type ENUM('个人','法人')`：投资者类型
- `name VARCHAR(100)`：姓名或法人名称
- `gender VARCHAR(10)`：性别
- `id_type VARCHAR(20)`：证件类型
- `id_number VARCHAR(50)`：证件号码或法人代表身份证号
- `phone VARCHAR(20)`：联系电话
- `address VARCHAR(200)`：地址
- `work_unit VARCHAR(100)`：工作单位
- `occupation VARCHAR(50)`：职业
- `education VARCHAR(50)`：学历
- `legal_number VARCHAR(20)`：法人注册登记号
- `business_license VARCHAR(20)`：营业执照号
- `executor_name VARCHAR(50)`：法人授权执行人姓名
- `executor_id_number VARCHAR(50)`：法人授权执行人身份证号
- `executor_phone VARCHAR(20)`：法人授权执行人电话
- `executor_address VARCHAR(100)`：法人授权执行人地址
- `agent_name VARCHAR(100)`：个人代办人姓名
- `agent_id_number VARCHAR(50)`：个人代办人证件号
- `created_at DATETIME`：创建时间

约束：

- 主键：`investor_id`
- 唯一键：`id_number`

### `staff`

工作人员表。

主要字段：

- `staff_id INT`：主键
- `username VARCHAR(50)`：登录名
- `password_hash VARCHAR(128)`：密码哈希
- `status ENUM('正常','禁用')`：状态
- `created_at DATETIME`：创建时间

约束：

- 主键：`staff_id`
- 唯一键：`username`

### `security_account`

证券账户表。

主要字段：

- `sec_acc_no VARCHAR(20)`：证券账户号，主键
- `investor_id INT`：所属投资者
- `status ENUM(...)`：账户状态
- `open_date DATE`：开户日期
- `linked_fund_acc VARCHAR(20)`：绑定资金账户号

状态语义：

- `正常`
- `挂失冻结`
- `违规冻结`
- `无资金账户冻结`
- `预销户`
- `已销户`

约束：

- 主键：`sec_acc_no`
- 外键：`investor_id -> investor.investor_id`
- 外键：`linked_fund_acc -> fund_account.fund_acc_no`
- 唯一键：`linked_fund_acc`

### `fund_account`

资金账户表。

主要字段：

- `fund_acc_no VARCHAR(20)`：资金账户号，主键
- `sec_acc_no VARCHAR(20)`：对应证券账户
- `trade_password VARCHAR(128)`：交易密码哈希
- `withdraw_password VARCHAR(128)`：取款密码哈希
- `available_balance DECIMAL(15,2)`：可用余额
- `frozen_balance DECIMAL(15,2)`：冻结余额
- `currency CHAR(3)`：币种
- `status ENUM(...)`：账户状态
- `open_date DATE`：开户日期
- `last_interest_date DATE`：最近结息日期
- `annual_interest_rate DECIMAL(5,4)`：年利率

状态语义：

- `正常`
- `挂失冻结`
- `违规冻结`
- `已销户`

约束：

- 主键：`fund_acc_no`
- 外键：`sec_acc_no -> security_account.sec_acc_no`
- 唯一键：`sec_acc_no`

### `fund_transaction_log`

资金流水表。

主要字段：

- `log_id BIGINT`：主键
- `fund_acc_no VARCHAR(20)`：资金账户号
- `txn_type ENUM(...)`：流水类型
- `amount DECIMAL(15,2)`：金额
- `available_after DECIMAL(15,2)`：变更后可用余额
- `frozen_after DECIMAL(15,2)`：变更后冻结余额
- `ref_order_id VARCHAR(50)`：关联交易号
- `operator_id INT`：工作人员 ID，可为空
- `txn_time DATETIME`：流水时间

当前已覆盖的流水类型包括：

- 存款
- 取款
- 买入冻结
- 买入扣款
- 卖出回款
- 撤单解冻
- 结息

### `holding`

持仓表。

主要字段：

- `holding_id BIGINT`：主键
- `sec_acc_no VARCHAR(20)`：证券账户号
- `stock_code VARCHAR(10)`：股票代码
- `stock_name VARCHAR(100)`：股票名称
- `quantity INT`：持股数量
- `frozen_quantity INT`：冻结数量
- `avg_cost DECIMAL(15,4)`：平均成本
- `updated_at DATETIME`：更新时间

约束：

- 唯一键：`(sec_acc_no, stock_code)`

### `holding_change_log`

持仓变化日志表。

主要字段：

- `log_id BIGINT`：主键
- `sec_acc_no VARCHAR(20)`：证券账户号
- `stock_code VARCHAR(10)`：股票代码
- `stock_name VARCHAR(100)`：股票名称
- `ref_order_id VARCHAR(50)`：关联交易号
- `change_type VARCHAR(20)`：变更类型
- `quantity INT`：变更股数
- `price DECIMAL(15,4)`：成交价格
- `quantity_after INT`：变更后持股
- `frozen_quantity_after INT`：变更后冻结股数
- `avg_cost_after DECIMAL(15,4)`：变更后成本
- `txn_time DATETIME`：记录时间

### `operation_log`

工作人员操作日志表。

主要字段：

- `log_id BIGINT`：主键
- `staff_id INT`：工作人员 ID
- `operation_type VARCHAR(50)`：操作类型
- `target_type VARCHAR(50)`：目标对象类型
- `target_id VARCHAR(50)`：目标对象编号
- `detail VARCHAR(500)`：详情
- `operation_time DATETIME`：操作时间

### `login_certificate_state`

首次登录证书认证状态表。

主要字段：

- `state_id BIGINT`：主键
- `subject_type VARCHAR(50)`：主体类型，如 `STAFF`
- `subject_key VARCHAR(100)`：主体标识，如用户名
- `certificate_verified BOOLEAN`：是否已完成认证
- `verified_at DATETIME`：认证时间
- `created_at DATETIME`
- `updated_at DATETIME`

唯一键：

- `(subject_type, subject_key)`

## 5.3 关系模式

可以把核心关系简化理解为：

```text
investor (1) ---- (N) security_account
security_account (1) ---- (0..1) fund_account
fund_account (1) ---- (N) fund_transaction_log
security_account (1) ---- (N) holding
security_account (1) ---- (N) holding_change_log
staff (1) ---- (N) operation_log
staff (1) ---- (N) fund_transaction_log
```

同时：

- `security_account.linked_fund_acc` 指向 `fund_account.fund_acc_no`
- `fund_account.sec_acc_no` 指向 `security_account.sec_acc_no`

这两个字段共同表达当前绑定关系。

## 5.4 视图

当前视图脚本中定义了：

- `v_fund_account_simple`
- `v_holding_available`
- `v_investor_basic`

用途：

- 简化查询输出
- 支持账户、持仓、投资者基础信息读取

## 6. 内部接口

内部接口主要面向工作人员前端使用，请求头统一使用：

- `X-Staff-Auth-Token`

### 6.1 工作人员认证接口

#### `POST /api/internal/staff/login`

用途：

- 工作人员账号密码登录

请求体：

```json
{
  "username": "staff001",
  "password": "123456"
}
```

说明：

- 如果不是首次登录且认证已完成，直接返回 `auth_token`
- 如果需要首次证书认证，则返回 `requires_certificate`

#### `POST /api/internal/staff/complete-certificate`

用途：

- 完成首次登录证书认证

请求体：

```json
{
  "subject_type": "STAFF",
  "subject_key": "staff001",
  "certificate_code": "ABC123"
}
```

#### `POST /api/internal/staff/deactivate`

用途：

- 停用工作人员

请求体：

```json
{
  "target_staff_id": 2,
  "reason": "离职"
}
```

### 6.2 Dashboard 接口

#### `GET /api/internal/dashboard/stats`

用途：

- 获取总览统计

返回重点字段：

- `security_account_count`
- `fund_account_count`
- `today_new_accounts`
- `abnormal_account_count`

#### `GET /api/internal/dashboard/recent-logs?limit=10`

用途：

- 获取最近操作日志

#### `GET /api/internal/dashboard/logs/query`

用途：

- 按条件查询操作日志

查询参数：

- `time_from`
- `time_to`
- `operation_type`
- `account_no`

说明：

- `account_no` 支持证券账户号或资金账户号
- Dashboard 前端当前采用“时间范围 + 操作类型下拉 + 账户号输入”模式

### 6.3 证券账户内部接口

#### `POST /api/internal/security/accounts`

用途：

- 开立证券账户

请求体主要字段：

- `investor_type`
- `name`
- `gender`
- `id_type`
- `id_number`
- `phone`
- `address`
- `work_unit`
- `occupation`
- `education`
- `legal_number`
- `business_license`
- `executor_name`
- `executor_id_number`
- `executor_phone`
- `executor_address`
- `agent_name`
- `agent_id_number`

说明：

- 个人账户和法人账户共用一个开户接口
- 法人账户会使用法人相关字段
- 个人代办开户会使用 `agent_*` 字段

#### `POST /api/internal/security/accounts/loss`

用途：

- 证券账户挂失

请求体：

```json
{
  "sec_acc_no": "SA0001",
  "id_number": "3301...",
  "reason": "卡片遗失"
}
```

#### `POST /api/internal/security/accounts/reissue`

用途：

- 证券账户补办

请求体：

- 在开户字段基础上增加 `old_sec_acc_no`

#### `POST /api/internal/security/accounts/close`

用途：

- 证券账户销户

请求体：

```json
{
  "sec_acc_no": "SA0001",
  "id_number": "3301...",
  "reason": "主动销户"
}
```

业务限制：

- 当前要求证券账户无持仓后才能销户

#### `PUT /api/internal/security/investors`

用途：

- 修改投资者信息

请求体主要字段：

- `investor_id`
- `name`
- `gender`
- `id_type`
- `id_number`
- `phone`
- `address`
- `work_unit`
- `occupation`
- `education`
- `legal_number`
- `business_license`
- `executor_name`
- `executor_id_number`
- `executor_phone`
- `executor_address`
- `agent_name`
- `agent_id_number`

#### `GET /api/internal/security/accounts`

用途：

- 查询全部证券账户列表

前端用途：

- 证券账户列表页
- Dashboard 统计

### 6.4 资金账户内部接口

#### `POST /api/internal/fund/accounts`

用途：

- 开立资金账户

请求体：

```json
{
  "sec_acc_no": "SA0001",
  "id_number": "3301...",
  "currency": "CNY",
  "trade_password": "123456",
  "withdraw_password": "654321"
}
```

说明：

- 当前业务要求先开证券账户，再开资金账户
- 开立资金账户时会自动与已有证券账户绑定

#### `POST /api/internal/fund/deposit`

用途：

- 存款

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "amount": 1000.00
}
```

说明：

- 前端当前额外提供“银行卡号输入框”，但该字段不提交到后端

#### `POST /api/internal/fund/withdraw`

用途：

- 取款

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "amount": 500.00,
  "withdraw_password": "654321"
}
```

#### `PUT /api/internal/fund/password`

用途：

- 工作人员办理资金账户改密

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "old_password": "123456",
  "new_password": "654321",
  "password_type": "trade"
}
```

`password_type` 取值：

- `trade`
- `withdraw`

#### `POST /api/internal/fund/accounts/loss`

用途：

- 资金账户挂失

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "sec_acc_no": "SA0001",
  "id_number": "3301...",
  "reason": "卡片遗失"
}
```

当前业务语义：

- 资金账户挂失后，资金账户冻结
- 关联证券账户也联动冻结

#### `POST /api/internal/fund/accounts/reissue`

用途：

- 资金账户补办

请求体主要字段：

- `old_fund_acc_no`
- `sec_acc_no`
- `id_number`
- `currency`
- `new_trade_password`
- `new_withdraw_password`

#### `POST /api/internal/fund/accounts/close`

用途：

- 资金账户销户

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "id_number": "3301...",
  "reason": "主动销户"
}
```

当前业务语义：

- 资金账户销户后会与证券账户分离
- 没有资金账户挂接的证券账户会进入“无资金账户冻结”

#### `POST /api/internal/fund/accounts/bind`

用途：

- 绑定证券账户

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "sec_acc_no": "SA0001"
}
```

#### `POST /api/internal/fund/accounts/unbind`

用途：

- 解绑证券账户

请求体同上。

#### `GET /api/internal/fund/accounts`

用途：

- 查询资金账户详情

查询参数：

- `fund_acc_no`
- `id_number`
- `include_logs`

#### `GET /api/internal/fund/logs`

用途：

- 查询资金流水

查询参数：

- `fund_acc_no`
- `id_number`
- `limit`

说明：

- 当前返回不仅包含资金流水，也会在统一查询结果中附带交易相关的持仓变动信息字段
- 前端会在流水表格中显示股票名称、股票代码、股数变化、价格、关联单号

#### `GET /api/internal/fund/accounts/list`

用途：

- 查询全部资金账户列表

## 7. 外部接口

外部接口主要面向其他子系统调用。

## 7.1 投资者资金侧接口

### `POST /api/external/fund/login`

用途：

- 投资者使用资金账户号和交易密码登录

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "trade_password": "123456"
}
```

说明：

- 账户系统会自行签发投资者 `auth_token`

### `POST /api/external/fund/complete-certificate`

用途：

- 投资者首次登录时完成证书认证

### `GET /api/external/fund/snapshot`

用途：

- 查询资金账户快照

查询参数：

- `fund_acc_no`
- `auth_token`

### `PUT /api/external/fund/password`

用途：

- 投资者自行修改资金账户密码

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "auth_token": "token",
  "password_type": "trade",
  "old_password": "123456",
  "new_password": "654321"
}
```

## 7.2 投资者证券侧接口

### `GET /api/external/security/snapshot`

用途：

- 查询证券账户快照或指定股票持仓

查询参数：

- `sec_acc_no`
- `auth_token`
- `stock_code` 可选

## 7.3 中央交易回写接口

### `POST /api/external/trade/fund-balance`

用途：

- 中央交易系统回写资金变化

请求体：

```json
{
  "fund_acc_no": "FA0001",
  "ref_order_id": "ORD20260622001",
  "txn_type": "买入冻结",
  "amount": 1000.00
}
```

`txn_type` 当前支持：

- `买入冻结`
- `买入扣款`
- `卖出回款`
- `撤单解冻`

说明：

- 当前接口模型不是“直接传 delta_available / delta_frozen”
- 而是按“交易号 + 交易类型 + 金额”落库
- 这样可以把资金流水和交易记录自然关联起来

### `POST /api/external/trade/security-holding`

用途：

- 中央交易系统回写持仓变化

请求体：

```json
{
  "sec_acc_no": "SA0001",
  "stock_code": "600519",
  "stock_name": "贵州茅台",
  "ref_order_id": "ORD20260622001",
  "change_type": "买入增加",
  "quantity": 100,
  "price": 1500.00
}
```

`change_type` 当前支持：

- `买入增加`
- `卖出冻结`
- `卖出扣减`
- `撤单释放`

说明：

- `ref_order_id` 与资金流水表中的 `ref_order_id` 一起构成交易关联线索
- `holding_change_log` 会保留股票代码、股票名称、数量、价格、变更后结果

## 7.4 管理员接口

### `POST /api/admin/fund/settle-annual-interest`

用途：

- 对资金账户执行年度结息

请求体：

```json
{
  "year_rate": 0.0035
}
```

### `POST /api/admin/accounts/freeze`

用途：

- 管理员冻结账户

请求体：

```json
{
  "account_type": "FUND",
  "account_no": "FA0001",
  "freeze_type": "VIOLATION",
  "reason": "违规处理"
}
```

### `POST /api/admin/accounts/unfreeze`

用途：

- 管理员解冻账户

### `GET /api/admin/accounts/{account_no}`

用途：

- 查询账户详情

### `POST /api/admin/security/force-close`

用途：

- 管理员强制销户证券账户

## 7.5 审计接口

### `GET /api/admin/audit/operation-logs`

用途：

- 按时间范围、操作类型、目标对象查询操作日志

查询参数：

- `staff_id` 可选
- `time_from`
- `time_to`
- `operation_type` 可选
- `target_type` 可选
- `target_id` 可选

## 8. 关键业务规则

当前代码中已经明确实现或体现出的规则如下。

### 8.1 开户顺序

- 先开证券账户
- 后开资金账户
- 开立资金账户时自动与证券账户绑定

### 8.2 禁止开户规则

当前已明确落地的规则：

- 未成年人禁止开户
- 黑名单桥接校验保留

当前项目约定：

- 四类禁止开户人员不再在本系统内部完整建模
- 黑名单能力通过外部桥接处理
- 其他复杂资格条件本轮不再细化展开

### 8.3 证券销户规则

- 证券账户必须先清仓
- 有持仓时不能销户

### 8.4 资金挂失联动规则

- 资金账户挂失会冻结资金账户
- 同时联动冻结关联证券账户

### 8.5 资金销户联动规则

- 资金账户销户后与证券账户解绑
- 失去资金账户挂接的证券账户转为“无资金账户冻结”

### 8.6 登录认证规则

- 工作人员和投资者都支持首次登录证书认证
- 首次认证完成后，由账户系统签发登录 token
- 内部接口使用工作人员 token
- 投资者外部接口使用投资者 token

### 8.7 操作日志

- 工作人员办理账户业务会写入 `operation_log`
- Dashboard 和审计接口都基于该表进行查询

### 8.8 交易回写规则

- 中央交易系统不直接传完整交易记录主键给账户系统
- 账户系统按自身规则写入资金流水和持仓变化日志
- 通过 `ref_order_id` 把两类变化关联起来

## 9. 错误码

当前错误码定义位于：

- `src/main/java/account/common/ErrorCode.java`

## 9.1 通用错误码

- `0`：成功
- `4000`：参数校验失败
- `5000`：系统内部错误

## 9.2 业务错误码

- `1001`：余额不足
- `1002`：持仓不足
- `1003`：账户已冻结
- `1004`：密码错误
- `1005`：证券账户不存在
- `1006`：该投资者已拥有其他证券账户
- `1007`：资金账户尚有可用余额或冻结资金，当前操作不允许
- `1008`：证券账户未关联当前资金账户
- `1009`：工作人员认证失败
- `1010`：账户不存在
- `1011`：账户已经是请求的状态
- `1012`：投资者在黑名单中
- `1013`：证券账户持有人与投资者身份证不一致
- `1014`：账户绑定关系冲突
- `1015`：资金账户未绑定符合要求的证券账户
- `1016`：资金账户存在未成交委托单
- `1017`：资金账户处于冻结状态，当前操作不允许
- `1018`：认证令牌无效或已失效
- `1019`：开户资格不符合
- `1020`：证件类型或证件号码不合法
- `1021`：当前账户状态不允许执行该操作
- `1022`：证券账户仍有持仓，无法销户

## 10. 前端页面与后端对应关系

### `Login.tsx`

调用：

- `POST /api/internal/staff/login`

### `CertificateAuth.tsx`

调用：

- `POST /api/internal/staff/complete-certificate`

### `Dashboard.tsx`

调用：

- `GET /api/internal/dashboard/stats`
- `GET /api/internal/dashboard/recent-logs`
- `GET /api/internal/dashboard/logs/query`

### `SecuritiesAccounts.tsx`

调用：

- `GET /api/internal/security/accounts`
- `POST /api/internal/security/accounts`
- `POST /api/internal/security/accounts/loss`
- `POST /api/internal/security/accounts/reissue`
- `POST /api/internal/security/accounts/close`
- `PUT /api/internal/security/investors`

### `FundAccounts.tsx`

调用：

- `GET /api/internal/fund/accounts/list`
- `POST /api/internal/fund/accounts`
- `POST /api/internal/fund/deposit`
- `POST /api/internal/fund/withdraw`
- `PUT /api/internal/fund/password`
- `POST /api/internal/fund/accounts/loss`
- `POST /api/internal/fund/accounts/reissue`
- `POST /api/internal/fund/accounts/close`
- `POST /api/internal/fund/accounts/bind`
- `POST /api/internal/fund/accounts/unbind`
- `GET /api/internal/fund/accounts`
- `GET /api/internal/fund/logs`

## 11. 当前阅读建议

如果你要继续改业务，建议优先看：

1. `controller`
2. `service`
3. `dao`
4. `scripts/01_create_tables.sql`
5. `frontend/src/app/pages`

如果你要对外联调，建议优先看：

1. `controller/external`
2. `dto/UpdateFundBalanceRequest.java`
3. `dto/UpdateSecurityHoldingRequest.java`
4. `README` 中第 7 节外部接口
