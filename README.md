# FSE

本仓库为股票交易系统的精简源码仓库，只保留前端、后端、数据库相关代码文件。

已移除内容：

- 测试代码
- 测试报告
- 联调文档
- 启动脚本
- 截图与演示素材
- Kafka / Redis 本地运行文件
- 日志、构建产物、依赖缓存

## 仓库结构

### `account-management`

账户管理子系统。

- 后端源码：`src/main/java`
- 后端配置：`src/main/resources`
- 前端源码：`frontend/src`
- 前端构建配置：`frontend/package.json`、`frontend/vite.config.ts`
- 数据库脚本：`scripts/01_create_tables.sql`、`scripts/02_views.sql`、`scripts/04_optional_procedures.sql`、`scripts/mysql_schema_current.sql`

### `central-trading`

中央交易子系统。

- 后端源码：`src/main/java`
- 后端配置与建表脚本：`src/main/resources`
- 数据库初始化脚本：`src/main/resources/schema.sql`

### `online-info-publish`

网上信息发布子系统。

- 后端源码：`src/main/java`
- 后端配置：`src/main/resources`
- 前端源码：`publish-frontend/src`
- 前端构建配置：`publish-frontend/package.json`、`publish-frontend/vite.config.js`

### `trade-management`

交易管理子系统。

- 后端源码：`src/main/java`
- 前端源码：`web`
- 数据库脚本：`sql/schema.sql`、`sql/seed.sql`、`sql/migration_*.sql`
- 运行配置模板：`config.properties`

### `trading-client`

交易客户端子系统。

- 前端页面与业务脚本：`index.html`、`styles.css`、`js`
- Node 服务端源码：`server`
- 数据库脚本：`database/schema.sql`
- 构建依赖配置：`package.json`

## 数据库相关

本仓库保留的数据库代码主要分布在：

- `account-management/scripts`
- `central-trading/src/main/resources/schema.sql`
- `trade-management/sql`
- `trading-client/database/schema.sql`

## 构建配置

本仓库保留了各子系统的最小构建文件：

- Java / Maven：`pom.xml`
- Node / npm：`package.json`
- Vite：`vite.config.ts`、`vite.config.js`

## 说明

这个仓库现在面向“源码交付”而不是“本地一键运行”。

如果后续还需要继续瘦身，可以再删掉：

- 各子系统内保留的子 README
- 前端无关素材文件
- 非核心 SQL 迁移脚本
