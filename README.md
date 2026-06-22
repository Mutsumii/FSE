# FSE 股票交易系统源码仓库

本仓库用于提交股票交易系统的源码版本，只保留前端、后端与数据库相关代码，不再保留测试脚本、启动脚本、演示材料和本地运行杂项文件。

## 1. 仓库定位

本仓库当前是一个“源码交付仓库”，目标是把各子系统的实现代码集中保存，便于继续开发、联调、阅读和后续整理。

仓库内保留的内容主要包括：

- 后端源码
- 前端源码
- 数据库建表与初始化脚本
- 最小必要的构建配置文件

仓库内已经移除的内容包括：

- 测试脚本
- 启动脚本、停服脚本、联调脚本
- 设计报告、测试报告、PPT、截图
- 本地 Kafka / Redis 运行文件
- 临时日志、构建产物、运行垃圾文件

## 2. 子系统概览

### `account-management`

账户管理子系统。

职责：

- 证券账户开户、挂失、补办、销户
- 资金账户开户、存款、取款、改密、挂失、补办、销户
- 投资者信息维护
- 工作人员登录与证书认证
- 向外提供账户查询、持仓查询、资金快照、交易落账、冻结审计等接口

代码组成：

- 后端：`src/main/java`
- 配置：`src/main/resources/application.yml`
- 前端：`frontend/src`
- 数据库脚本：`scripts`

配套文档：

- [账户系统 README](./account-management/README.md)
- [账户系统用户手册](./account-management/USER_MANUAL.md)

### `central-trading`

中央交易子系统。

职责：

- 撮合交易
- 委托、撤单、成交处理
- 行情与交易事件广播
- 与其他子系统进行交易层交互

代码组成：

- 后端：`src/main/java`
- 数据库脚本：`src/main/resources/schema.sql`

### `online-info-publish`

网上信息发布子系统。

职责：

- 面向投资者展示公开信息页面
- 承载发布端前后端代码

代码组成：

- 后端：`src`
- 前端：`publish-frontend`

### `trade-management`

交易管理子系统。

职责：

- 管理员侧交易审核、黑名单、审查记录等管理功能
- 与账户系统、中央交易系统进行管理联动

代码组成：

- 后端：`src/main/java`
- 前端：`web`
- 数据库脚本：`sql`

### `trading-client`

交易客户端子系统。

职责：

- 面向投资者进行登录、下单、撤单、查询、提醒等交互
- 对接账户系统和中央交易系统

代码组成：

- 前端页面：`index.html`、`styles.css`、`js`
- Node 服务端：`server`
- 数据库脚本：`database/schema.sql`

## 3. 目录结构

仓库根目录当前结构如下：

```text
FSE_upload_target/
├─ account-management/
├─ central-trading/
├─ online-info-publish/
├─ trade-management/
├─ trading-client/
├─ README.md
└─ USER_MANUAL.md
```

其中：

- `account-management` 是当前文档最完整、业务规则最明确的模块
- 其余四个子系统保留源码主体，作为整体系统的其他组成部分

## 4. 技术栈概览

### 后端

- Java 17
- Spring Boot 3.x
- Maven
- MySQL

### 前端

- React + Vite：账户管理前端
- Vue + Vite：网上信息发布前端
- 原生 HTML / CSS / JavaScript：交易客户端、部分管理页面

### 数据库

- MySQL 建表脚本散布在各子系统目录中
- 账户系统脚本最完整，位于 `account-management/scripts`

## 5. 数据库代码分布

当前仓库内和数据库相关的主要目录如下：

- `account-management/scripts`
- `central-trading/src/main/resources/schema.sql`
- `trade-management/sql`
- `trading-client/database/schema.sql`

其中账户系统数据库脚本包括：

- `01_create_tables.sql`：核心表定义
- `02_views.sql`：视图
- `04_optional_procedures.sql`：可选过程
- `mysql_schema_current.sql`：当前整合版 schema

## 6. 代码阅读建议

如果当前重点是继续补账户业务、对接口、查表结构，建议优先阅读：

1. `account-management/README.md`
2. `account-management/USER_MANUAL.md`
3. `account-management/src/main/java/account/controller`
4. `account-management/scripts/01_create_tables.sql`
5. `account-management/frontend/src/app/pages`

如果当前重点是看整个系统之间的关系，建议按下面顺序阅读：

1. 本 README
2. 根目录 `USER_MANUAL.md`
3. 账户系统 README
4. 交易客户端、交易管理、中央交易三个模块的入口代码

## 7. 说明

本仓库当前不再追求“一键启动”体验，重点是保留可读、可维护、可继续演进的源码主体。

如果后续还要进一步精简，可以继续删除：

- 各子系统中无实际作用的示例文件
- 前端未使用素材
- 冗余依赖与模板代码
- 过时的迁移脚本或备用脚本
