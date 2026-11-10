# Lemon 刷题

考研政治 / 教育学刷题产品。一期先上 **Web 端（H5）+ 管理系统**，二期用同一套用户端代码编译**微信小程序**。完整规划见 [docs/项目规划.md](docs/项目规划.md)。

| 目录 | 说明 | 技术栈 | 本地地址 |
|---|---|---|---|
| `lemon-server` | 后端接口 | Java 17 · Spring Boot 4.1 · MyBatis-Plus · Sa-Token · Flyway · MySQL · Redis | http://localhost:8080（接口文档 `/swagger-ui.html`） |
| `lemon-admin` | 管理系统 | Vue 3 · Vite · Element Plus · Pinia | http://localhost:5173 |
| `lemon-app` | 用户端 | uni-app（Vue 3）：`build:h5` 出网站，`build:mp-weixin` 出小程序 | http://localhost:5174 |

## 部署

见 [docs/部署指南.md](docs/部署指南.md)：`deploy/` 下 `cp .env.example .env` 填好域名和密码、放好证书后 `docker compose up -d --build`。
部署或更新后用 `scripts/e2e/regression.py` 跑一遍端到端回归。

## 内容层级

```
模块（政治 / 教育学）
└── 题库
    ├── 科目型：题库 → 科目（马原、毛中特…）→ 章节 → 题目
    └── 试卷型：题库 → 章节（每章 = 一套模拟卷，可设时长/总分）→ 题目
```

## 本地启动

**环境**：JDK 17+、Node 20+、MySQL 8+、Redis 7+（没有本地 MySQL/Redis 可用根目录 `docker-compose.yml`）。Maven 不用装，后端自带 `mvnw`。

1. 后端（首次启动会自动建库 `lemon`、执行 Flyway 建表、初始化“政治 / 教育学”两个模块和管理员账号）

   ```bash
   cd lemon-server && ./mvnw spring-boot:run
   ```

   数据库 / Redis 连接通过环境变量覆盖：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`。

2. 管理系统（开发服务器把 `/api` 代理到 8080）

   ```bash
   cd lemon-admin && npm install && npm run dev
   ```

   初始账号 `admin` / `admin123`（由 `ADMIN_INIT_USERNAME` / `ADMIN_INIT_PASSWORD` 控制，仅在没有任何管理员时创建，**上线前务必修改**）。

3. 用户端 H5

   ```bash
   cd lemon-app && npm install && npm run dev:h5
   ```

   小程序：`npm run build:mp-weixin`，用微信开发者工具导入 `lemon-app/dist/build/mp-weixin`；联调前把 `.env.development` 的 `VITE_API_BASE` 改成后端完整地址。

## 后端约定

- 路由：用户端 `/api/app/**`，管理端 `/api/admin/**`，两套 Sa-Token 登录体系（`StpAppUtil` / `StpAdminUtil`）互不通用
- 用户端免登录接口：`/api/app/auth/**`、`/home`、`/modules/**`、`/banks/**`（目录浏览）；其余需登录
- 响应：HTTP 一律 200，`{ code, msg, data }`，`code=0` 成功、`401` 未登录、`400` 参数错误、`1000` 业务错误
- 表结构变更：新增 `src/main/resources/db/migration/V{n}__xxx.sql`，**不要修改已执行过的脚本**
- 主键自增 BIGINT；内容表逻辑删除（`deleted`），收藏 / 错题 / 答题记录不做逻辑删除
- 管理端角色：`super_admin` 全部权限；`editor`（录题员）只能管题库 / 科目 / 章节 / 题目，模块只读，不能管账号。启动时若没有启用的超级管理员，会自动把最早的管理员设为超级管理员
- 题目规则（`QuestionNormalizer`）：单选 2~8 个选项且只有 1 个答案；多选答案规范成去重升序（如 `ABD`，容忍 `a,b d` 写法）；判断题可不填选项（默认 A 正确 / B 错误），答案可写 对/错/√/×；主观题不能有选项、解析即参考答案必填
- 题量 `question_count` 为冗余字段：章节 = 上架题目数，题库 = 上架章节题量之和，题目或章节变动后由 `QuestionCountService` 重算
- 富文本：题干、解析存 HTML，保存前经 `HtmlUtil.sanitize`（jsoup 白名单）过滤，防止存储型 XSS；用户端可以直接渲染
- 上传：`POST /api/admin/files/upload` 只收 PNG / JPG / GIF / WEBP（按文件头判断，拒绝 SVG），开发期存 `lemon-server/uploads/`，经 `/uploads/**` 访问；上线换对象存储时新增 `FileStorage` 实现即可
- Excel 导入：先选题库（可再选章节）；全有或全无，任何一行出错都不导入，返回行号和原因；未选章节时按“科目 / 章节”列名称归类，不存在的自动创建。导出格式与模板一致，可直接改完再导入
- 做题会话（`PracticeService`）：同一章节同一模式有未完成会话时续做（`restart=true` 重新开始）；练习每题提交即判、同一会话每题只判一次；考试只存草稿、交卷统一判分，服务端控制时长（超时 60 秒宽限后任何访问都自动交卷）；背题不产生记录
- 作答副作用（`AnswerRecorder`）：写答题明细 → 错题集（答错加入、连对 N 次移出，N 为系统参数）→ 重算章节进度（已做 = 做过的不同题数，答对 = 最近一次作答正确的题数）
- 错题重练 / 收藏练习（模式 4 / 5）：按范围取仍上架的题（最多 200 题）新建会话，同模式未完成的旧会话标记放弃；判题与练习模式相同，错题重练中答对会累计连对次数
- 反馈：每人每天最多 20 条；录题员也可处理反馈（题目纠错可一键跳到题目编辑）
- 测试：`./mvnw test`

## 当前进度（骨架）

- [x] 后端：工程骨架、统一响应 / 异常、双登录体系、全部表结构、模块 / 题库 / 科目 / 章节管理接口（含层级校验）、用户端注册登录和题库目录接口
- [x] 管理系统：登录、布局、模块管理、题库管理（新建时选类型）、题库结构（科目 + 章节 / 试卷）
- [x] 用户端：首页、题库列表、科目 / 试卷列表、章节列表、登录注册、我的；收藏集 / 错题集 / 做题页为占位
- [x] M1：题目 CRUD + 批量上下架 / 移动 / 删除（按题型校验和规范化答案，自动维护章节 / 题库题量）；管理员角色（超级管理员 / 录题员）与账号管理
- [x] M2：题目管理页（富文本编辑、粘贴图片上传、批量上下架 / 移动 / 删除）、Excel 导入导出、图片上传、工作台、管理员 / 用户 / 公告 / 系统参数页面，菜单按角色显示
- [x] M3：做题页（练习即时判题 / 背题 / 限时考试、答题卡、左右滑动、收藏）、结果页与查看解析、续做、章节进度、试卷最近成绩、首页“继续上次练习”；答错自动进错题集
- [x] M4：收藏集 / 错题集（按模块 → 题库 → 章节分组、列表、取消收藏 / 移出、按范围练习）、我的统计（作答数、正确率、连续打卡）、意见反馈与题目纠错（管理端回复，用户可见）、首页 Banner 与管理
- [x] M5：生产部署方案（Docker Compose + Nginx，三个子域名、HTTPS、登录限流）、备份脚本、端到端回归脚本、部署指南；已在本地完整起过生产栈并跑通回归
