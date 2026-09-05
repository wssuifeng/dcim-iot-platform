from __future__ import annotations

import base64
import re
import shutil
import subprocess
import time
from pathlib import Path
from urllib.parse import quote

from lxml import etree, html
from pypdf import PdfReader, PdfWriter

ROOT = Path(r"D:\code\iot-platform-main - codex")
DOCS = ROOT / "docs"
PUB = DOCS / "PDF出版源"
SOURCE = DOCS / "项目架构设计与开发理解文档.md"
ENHANCED = PUB / "项目架构设计与开发理解文档-PDF增强版.md"
CSS_FILE = PUB / "技术白皮书.css"
HTML_FILE = PUB / "项目架构设计与开发理解文档-技术白皮书.html"
FIRST_PDF = PUB / "技术白皮书-目录定位稿.pdf"
PRINT_PDF = PUB / "技术白皮书-排版稿.pdf"
FINAL_PDF = DOCS / "项目架构设计与开发理解文档-技术白皮书.pdf"
PANDOC = Path(r"C:\Users\17841\AppData\Local\Pandoc\pandoc.exe")
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")
PROFILE = PUB / "edge-headless-profile"

EXPERIENCE_CHAPTER = r'''
# 八、工程经验专题

本章不引入新的项目事实，而是从当前实现提炼可迁移的工程方法。阅读时应把“当前实现”与“推荐演进”分开：前者可从代码直接验证，后者是基于现状的改进路径。

<div class="experience-overview">
<div>
<strong>适合重点阅读的人</strong><br>
维护认证、权限、缓存、实时通知、数据库和异步任务的开发者。
</div>
<div>
<strong>阅读目标</strong><br>
理解方案为什么成立、在哪些边界会失效，以及扩展前应先补哪项能力。
</div>
<div>
<strong>事实基线</strong><br>
Spring Security + JWT、Redis、MySQL、STOMP Simple Broker、进程内异步执行器。
</div>
<div>
<strong>判断原则</strong><br>
优先保证事实唯一、权限一致、事务边界明确和故障后可恢复。
</div>
</div>

## 1. Spring Security 与组织数据权限

<div class="design-card">
<span class="card-label">设计思考卡片</span>
<div class="card-row"><strong>背景</strong><span>系统同时存在平台角色、组织角色和机房级授权。</span></div>
<div class="card-row"><strong>问题</strong><span>单纯依赖 URL 角色或前端菜单，无法判断用户是否能操作某一条机房、设备或指标数据。</span></div>
<div class="card-row"><strong>方案</strong><span>使用 JWT 建立全局身份，OrganizationContextFilter 建立当前组织，Service 再执行资源数据范围校验。</span></div>
<div class="card-row"><strong>收益</strong><span>入口能快速拒绝明显越权，内部调用和异步任务也能复用同一业务权限规则。</span></div>
<div class="card-row"><strong>经验</strong><span>认证回答“你是谁”，组织上下文回答“你代表哪个组织”，数据范围回答“你能处理哪条数据”。三者不能合并成一个 role 字符串。</span></div>
</div>

### 设计思想

将粗粒度 RBAC 放在 Filter/Security 层，将资源级 ABAC 放在 Service 层。JWT 只携带变化较慢的全局身份，不固化组织角色，避免成员关系变化后 Token 长时间过期失真。

### 工程技巧

- Filter 顺序应固定：追踪和限流在前，JWT 建立身份，组织过滤器在身份之后；
- Service 使用 `ensureDeviceAccess`、`canAccessCloudRoom` 等语义化接口，不直接解释 ID 集合；
- 后台任务和异步线程没有 HTTP 请求上下文，必须显式建立或传播 CurrentOrganizationContext；
- 权限拒绝需要记录用户、组织、资源类型、资源 ID 和 traceId，但不要记录 Token。

### 常见问题

- 把组织角色写入 JWT，导致角色变更不能即时生效；
- 前端隐藏按钮后认为接口已经安全；
- `ORG_ALL` 用空机房集合表示，却被通用 `contains` 判断成无权限；
- 平台管理员是否允许代维没有统一策略，导致不同模块行为不一致。

### 优化方向

建立统一资源 Policy：分别提供组织、机房、设备的读写判断；为每类范围补参数化测试；把“无组织头”“成员无效”“资源越权”转换为不同稳定错误码。

## 2. Redis 缓存与短期安全状态

<div class="tech-card">
<span class="card-label">技术方案卡片</span>
<div class="card-row"><strong>技术</strong><span>Redis + TTL + Lua 原子脚本 + cache-aside。</span></div>
<div class="card-row"><strong>作用</strong><span>验证码、发送冷却、登录失败窗口、Token 黑名单、接口限流、热点缓存和互斥锁。</span></div>
<div class="card-row"><strong>使用方式</strong><span>短期状态写 Redis，长期业务事实写 MySQL；可回源缓存允许降级，安全计数按风险决定 fail-open 或 fail-closed。</span></div>
<div class="card-row"><strong>设计注意事项</strong><span>缓存键、TTL、原子性、空值策略和故障降级必须作为接口契约管理。</span></div>
</div>

### 设计思想

Redis 最适合保存“可过期、可重建或需要原子计数”的状态，不应成为组织、权限、告警状态等永久事实源。验证码只缓存 HMAC 摘要，体现了即使缓存泄露也减少明文凭证暴露的设计。

### 工程技巧

- cache-aside 写路径先更新数据库，再删除或更新缓存；
- 用空值占位抑制不存在 key 的重复回源；
- 登录失败和验证码配额使用 Lua，把判断和更新放入一个原子操作；
- 缓存键中包含组织、用户或范围版本，避免不同权限共享结果；
- 互斥锁只减少缓存击穿，不应承担业务唯一性。

### 常见问题

缓存与数据库短时不一致、热点 key、TTL 同时到期、Redis 故障策略不明确、锁未设置过期时间、把限流失败统一放行造成安全边界降低。

### 优化方向

建立缓存键注册表和指标监控；对安全相关 Redis 操作增加故障演练；为看板缓存加入随机抖动；必要时使用本地短缓存降低 Redis 网络依赖。

## 3. WebSocket 长连接与用户级通知

<div class="design-card">
<span class="card-label">设计思考卡片</span>
<div class="card-row"><strong>背景</strong><span>告警变化需要低延迟触达，但完整告警对象已经由 REST 提供。</span></div>
<div class="card-row"><strong>问题</strong><span>在 WebSocket 中复制完整业务 VO，会产生权限、版本、断线恢复和消息可靠性问题。</span></div>
<div class="card-row"><strong>方案</strong><span>持久化站内通知，WebSocket 只向目标用户发送摘要，客户端收到后 REST 回源。</span></div>
<div class="card-row"><strong>收益</strong><span>实时通道与数据契约低耦合，消息丢失后仍能从数据库恢复。</span></div>
<div class="card-row"><strong>经验</strong><span>实时消息可以是“缓存失效信号”，不必承担业务事实传输。</span></div>
</div>

### 设计思想

连接、Session 和消息摘要是瞬时状态，`alarm_event` 与 `in_app_notification` 才是事实。用户与 Session 是一对多关系，`convertAndSendToUser` 通过 Principal 将同一用户的多个标签页连接统一路由。

### 工程技巧

- 在 STOMP CONNECT 帧验证 JWT，不把握手放行误解为匿名访问；
- 限制客户端 SEND 目的地，只允许订阅受控用户队列；
- 使用心跳发现半开连接，重连后重新订阅；
- 对短时间内多个摘要做前端刷新合并；
- 事务提交后再推送，避免客户端读到未提交或回滚数据。

### 常见问题

单机 Session 无法跨实例、Simple Broker 不持久化、旧 `/topic/alerts` 与新 `/queue` 配置冲突、接收人解析漏掉组织管理员、断线期间消息无法重放。

### 优化方向

多实例部署前引入外部 Broker 或事件广播；保持站内通知落库；建立未读游标或增量同步接口；清理旧 topic 兼容代码；为接收人策略建立组织角色测试矩阵。

## 4. MySQL 数据模型、索引与查询

<div class="tech-card">
<span class="card-label">技术方案卡片</span>
<div class="card-row"><strong>技术</strong><span>MySQL + MyBatis-Plus + Flyway + 本地事务。</span></div>
<div class="card-row"><strong>作用</strong><span>保存账号、组织、资源、指标、告警、通知、仿真和审计的权威事实。</span></div>
<div class="card-row"><strong>使用方式</strong><span>管理类数据使用规范化关系模型；指标使用历史追加表和最新快照表；状态机使用当前状态加不可变历史。</span></div>
<div class="card-row"><strong>设计注意事项</strong><span>索引必须从真实过滤、排序和关联路径出发；无外键表必须由 Service 和一致性任务补偿。</span></div>
</div>

### 设计思想

单库本地事务适合当前规模，能够低成本保证审批、组织供应和告警状态历史一致。`metric_sample` 与 `metric_latest` 分离是典型的写放大换读性能：趋势查历史，状态页查快照。

### 工程技巧

- 联合索引按等值过滤列、范围列和排序列安排，不能只给每列单独建索引；
- 分页限制最大 size，避免无界查询；
- 审批和最后管理员保护使用 `FOR UPDATE` 或条件更新；
- 最新值使用带时间条件的原子 upsert，正确处理乱序数据；
- Flyway 迁移只追加，新修复写新版本，不修改已执行脚本；
- 高频列表将逐条补充查询改为批量加载或 join，避免 N+1。

### 常见问题

索引与 SQL 条件顺序不匹配、逻辑删除字段遗漏、`IN` 列表过大、读后写竞态、历史表无清理、删除元数据后留下孤儿指标。

### 优化方向

先实现 `metric_sample` 保留策略和原子 latest upsert；用慢查询与执行计划驱动索引调整；数据量确实超过 MySQL 适用范围后，再评估时序数据库，而不是提前拆分。

## 5. 事务提交后副作用与异步任务

<div class="experience-card">
<span class="card-label">经验总结区域</span>
<div class="card-row"><strong>可迁移经验</strong><span>数据库事务只保证本地事实；邮件、WebSocket 和异步执行属于外部副作用，应在提交后触发，并具备幂等、失败记录和恢复策略。</span></div>
<div class="card-row"><strong>项目实例</strong><span>旧规则评估在遥测提交后执行；告警通知在状态提交后发送；仿真任务在 Run 记录提交后进入线程池。</span></div>
</div>

### 设计思想

先持久化“发生了什么”，再执行“通知谁、调用谁”。这样外部失败不会抹掉业务事实，客户端也不会看到最终回滚的数据。

### 工程技巧

- 使用事务同步回调或 outbox 表表达提交后事件；
- 异步任务启动前保存快照和 PENDING 状态；
- 用 CAS 防止同一任务重复启动；
- 线程池有界，并传播 MDC、用户和组织上下文；
- 取消采用协作式检查，运行状态必须最终收敛；
- 外部副作用记录幂等键、尝试次数、最后错误和下次重试时间。

### 常见问题

在事务内发邮件导致长事务；提交前推送造成脏读；应用重启后 RUNNING 永久悬挂；重试没有幂等产生重复通知；线程池无界拖垮主进程。

### 优化方向

当前阶段可先增加启动时任务收敛和通知重试表。进入多实例后，再引入 outbox + 消息队列，不应直接把本地回调替换为不可靠的“发送后即忘”。

<div class="chapter-summary">
认证、缓存、数据库、实时连接和异步任务看似是基础设施问题，实际都围绕同一原则：事实必须唯一且可恢复；权限必须在权威数据处校验；外部副作用不能破坏本地事务。
</div>
'''

READING_GUIDE = r'''
<div class="executive-note">
<h2>30 分钟阅读路线</h2>
<p><strong>0-5 分钟：</strong>阅读“结论先行”和系统架构图，建立单体部署、逻辑模块和双告警链认知。</p>
<p><strong>5-15 分钟：</strong>阅读模块依赖、权限范围、指标写入和告警通知流程，理解数据如何穿过系统。</p>
<p><strong>15-23 分钟：</strong>阅读关键设计决策与工程经验专题，掌握方案背后的取舍。</p>
<p><strong>23-30 分钟：</strong>阅读技术债优先级和 Debug 路径，形成后续维护行动清单。</p>
</div>
'''

DATA_FLOW = r'''
### 3.4 核心业务数据流图

![核心业务数据流图](架构图/核心业务数据流图.drawio.png)

可编辑源文件：[核心业务数据流图.drawio](架构图/核心业务数据流图.drawio)

<div class="risk-card">
<span class="card-label">读图提示</span>
<p>图中绿色实线表示当前产品化指标主链，橙色虚线表示旧版兼容链，红色虚线表示规则结果到 <code>alarm_event</code> 的生产桥接尚未实现。实时摘要不是事实源，前端仍通过 REST 回源数据库。</p>
</div>
'''

CHAPTER_SUMMARIES = {
    "一、项目整体架构分析": "系统是单进程、单数据库的分层单体，同时具备清晰的逻辑业务模块。当前应先强化边界，不应把基础设施组件误判为分布式系统。",
    "二、模块划分与依赖分析": "账号与组织提供治理基础，资源提供业务坐标，指标与告警形成数据闭环，仿真用于验证。权限范围是横跨模块的高耦合能力。",
    "三、核心业务流程与数据流分析": "理解调用链时先确认当前请求属于账号组织流程、产品化指标链、旧告警兼容链还是新版告警状态链。链路判断错误会直接导致排障方向错误。",
    "四、模块详细设计规范": "Controller 维护接口契约，Service 承担权限和事务，Mapper 只负责数据访问。跨模块直接操作 Mapper 会破坏当前正在形成的模块边界。",
    "五、异常处理机制分析": "HTTP、Filter、WebSocket 和异步线程有不同异常出口。统一错误码、traceId 和结构化审计，是把这些出口重新关联起来的关键。",
    "六、关键设计决策分析": "当前主要设计都服务于低成本一致性：单库本地事务、Redis 短期状态、历史与最新双表、摘要推送加 REST 回源。局限主要出现在并发、集群和模型过渡。",
    "七、WebSocket 实时通知专项分析": "WebSocket 只承担变化通知，站内通知和告警事件承担可恢复事实。多实例前必须解决 Session 和 Broker 的跨节点传播。",
    "九、性能分析与系统演进": "当前先做单体优化和边界强化；只有出现独立扩缩容、数据技术或发布节奏需求时，才有足够理由拆分服务。",
    "十、技术债与设计问题分析": "P0 问题直接影响告警闭环和权限正确性；P1 影响事实一致性与长期运行；P2 适合纳入持续治理。",
    "十一、开发者快速理解总结": "新开发者先掌握请求上下文、数据范围和两套告警链，再进入具体业务代码。任何扩展都应先回答事实源、权限、事务和数据生命周期四个问题。",
}


def prepare_markdown() -> str:
    original = SOURCE.read_text(encoding="utf-8")
    start = original.find("本文不替代")
    body = original[start:] if start >= 0 else original
    body = body.replace("# 八、性能分析与系统演进", "# 九、性能分析与系统演进")
    body = body.replace("# 九、技术债与设计问题分析", "# 十、技术债与设计问题分析")
    body = body.replace("# 十、开发者快速理解总结", "# 十一、开发者快速理解总结")
    body = body.replace("\n---\n\n# 一、项目整体架构分析", "\n" + READING_GUIDE + "\n\n---\n\n# 一、项目整体架构分析", 1)
    body = body.replace("\n---\n\n# 二、模块划分与依赖分析", "\n" + DATA_FLOW + "\n\n---\n\n# 二、模块划分与依赖分析", 1)
    body = body.replace("\n# 九、性能分析与系统演进", "\n" + EXPERIENCE_CHAPTER + "\n\n---\n\n# 九、性能分析与系统演进", 1)
    ENHANCED.write_text(body, encoding="utf-8", newline="\n")
    return body


def run_pandoc() -> str:
    cmd = [
        str(PANDOC), str(ENHANCED),
        "--from=gfm+fenced_divs+raw_html",
        "--to=html5",
        "--section-divs",
        "--highlight-style=pygments",
    ]
    result = subprocess.run(cmd, cwd=DOCS, check=True, capture_output=True, text=True, encoding="utf-8")
    return result.stdout


def text_of(node) -> str:
    return "".join(node.itertext()).strip()


def image_to_data_uri(path: Path) -> str:
    suffix = path.suffix.lower()
    mime = "image/png" if suffix == ".png" else "image/svg+xml" if suffix == ".svg" else "image/jpeg"
    encoded = base64.b64encode(path.read_bytes()).decode("ascii")
    return f"data:{mime};base64,{encoded}"


def decorate_fragment(fragment: str):
    wrapper = html.fragment_fromstring(f"<main>{fragment}</main>", create_parent=False)

    for separator in wrapper.xpath(".//hr"):
        separator.getparent().remove(separator)

    headings = wrapper.xpath(".//h1 | .//h2")
    heading_records = []
    for index, heading in enumerate(headings, start=1):
        marker = f"HMARK{index:04d}"
        span = etree.Element("span")
        span.set("class", "marker")
        span.text = marker
        heading.insert(0, span)
        heading_records.append({
            "level": int(heading.tag[1]),
            "title": text_of(heading).replace(marker, "").strip(),
            "id": heading.get("id") or f"heading-{index}",
            "marker": marker,
            "page": None,
        })
        if not heading.get("id"):
            heading.set("id", f"heading-{index}")

    for section in wrapper.xpath(".//section[contains(concat(' ', normalize-space(@class), ' '), ' level1 ')]"):
        h1 = section.find("h1")
        if h1 is None:
            continue
        title = text_of(h1)
        if "六、关键设计决策分析" in title:
            for child in section.xpath("./section[contains(concat(' ', normalize-space(@class), ' '), ' level2 ')]"):
                child.classes.add("decision-section")
        if "十、技术债与设计问题分析" in title:
            for child in section.xpath("./section[contains(concat(' ', normalize-space(@class), ' '), ' level2 ')]"):
                child_title = text_of(child.find("h2")) if child.find("h2") is not None else ""
                if child_title.startswith("P0"):
                    child.classes.add("risk-critical")
                elif child_title.startswith("P1"):
                    child.classes.add("risk-high")
                elif child_title.startswith("P2"):
                    child.classes.add("risk-medium")
        summary = next((value for key, value in CHAPTER_SUMMARIES.items() if key in title), None)
        if summary:
            box = etree.Element("div")
            box.set("class", "chapter-summary")
            box.text = summary
            section.insert(section.index(h1) + 1, box)

    for diagram_section in wrapper.xpath(".//section[contains(concat(' ', normalize-space(@class), ' '), ' level3 ')][.//img]"):
        diagram_section.classes.add("diagram-section")

    for image in wrapper.xpath(".//img"):
        src = image.get("src", "")
        if src.startswith("data:"):
            continue
        candidate = (DOCS / src).resolve()
        if candidate.exists():
            image.set("src", image_to_data_uri(candidate))
        parent = image.getparent()
        if parent is not None and parent.tag == "p" and len(parent) == 1:
            figure = etree.Element("figure")
            parent.addprevious(figure)
            parent.remove(image)
            figure.append(image)
            caption = etree.SubElement(figure, "figcaption")
            caption.text = image.get("alt") or "项目技术图"
            parent.getparent().remove(parent)

    for paragraph in wrapper.xpath(".//p[a]"):
        if ".drawio" in text_of(paragraph):
            paragraph.set("class", (paragraph.get("class", "") + " source-link").strip())

    first_blockquote = wrapper.find("blockquote")
    if first_blockquote is not None:
        first_blockquote.set("class", "executive-note")

    return wrapper, heading_records


def build_toc(records):
    nav = etree.Element("nav")
    nav.set("class", "toc")
    nav.set("id", "目录")
    title = etree.SubElement(nav, "h1")
    title.text = "目录"
    intro = etree.SubElement(nav, "p")
    intro.set("class", "toc-intro")
    intro.text = "目录由当前增强版正文自动生成；页码在首轮排版后回填，并与 PDF 书签保持一致。"
    root_ol = etree.SubElement(nav, "ol")
    current_li = None
    current_sub = None
    for record in records:
        if record["level"] == 1:
            current_li = etree.SubElement(root_ol, "li")
            link = etree.SubElement(current_li, "a", href="#" + record["id"])
            link.text = record["title"]
            page = etree.SubElement(current_li, "span")
            page.set("class", "toc-page")
            page.set("data-marker", record["marker"])
            page.text = str(record["page"] or "")
            current_sub = etree.SubElement(current_li, "ol")
        elif record["level"] == 2 and current_li is not None:
            item = etree.SubElement(current_sub, "li")
            link = etree.SubElement(item, "a", href="#" + record["id"])
            link.text = record["title"]
            page = etree.SubElement(item, "span")
            page.set("class", "toc-page")
            page.set("data-marker", record["marker"])
            page.text = str(record["page"] or "")
    return nav


def cover_html():
    cover = etree.Element("section")
    cover.set("class", "cover")
    etree.SubElement(cover, "div", {"class": "cover-kicker"}).text = "Software Architecture · Engineering Practice"
    h1 = etree.SubElement(cover, "h1")
    h1.text = "项目架构设计与开发理解文档"
    etree.SubElement(cover, "div", {"class": "cover-subtitle"}).text = "数据中心动环监控平台技术白皮书｜从真实代码理解系统边界、数据流、权限模型与演进路径"
    etree.SubElement(cover, "div", {"class": "cover-rule"})
    meta = etree.SubElement(cover, "div", {"class": "cover-meta"})
    for label, value in [
        ("项目", "dcim-iot-platform"),
        ("代码基线", "main · 5d26eeb"),
        ("文档版本", "技术白皮书版 1.0"),
        ("生成日期", "2026-07-12"),
        ("阅读对象", "开发者、维护者、架构复盘人员"),
    ]:
        strong = etree.SubElement(meta, "strong")
        strong.text = label
        span = etree.SubElement(meta, "span")
        span.text = value
    etree.SubElement(cover, "div", {"class": "cover-foot"}).text = "Internal Engineering Documentation · Long-term Reference Edition"
    return cover


def serialize_document(wrapper, records):
    css = CSS_FILE.read_text(encoding="utf-8")
    document = html.Element("html")
    document.set("lang", "zh-CN")
    head = etree.SubElement(document, "head")
    etree.SubElement(head, "meta", charset="utf-8")
    etree.SubElement(head, "meta", name="viewport", content="width=device-width, initial-scale=1")
    etree.SubElement(head, "title").text = "项目架构设计与开发理解文档 - 技术白皮书"
    style = etree.SubElement(head, "style")
    style.text = css
    body = etree.SubElement(document, "body")
    body.append(cover_html())
    body.append(build_toc(records))
    body.append(wrapper)
    return "<!DOCTYPE html>\n" + etree.tostring(document, encoding="unicode", method="html")


def print_pdf(html_path: Path, output_path: Path):
    if output_path.exists():
        output_path.unlink()
    PROFILE.mkdir(parents=True, exist_ok=True)
    uri = html_path.resolve().as_uri()
    cmd = [
        str(EDGE),
        "--headless=new",
        "--disable-gpu",
        "--disable-extensions",
        "--allow-file-access-from-files",
        "--no-pdf-header-footer",
        f"--user-data-dir={PROFILE}",
        f"--print-to-pdf={output_path}",
        uri,
    ]
    subprocess.run(cmd, check=True, cwd=PUB, capture_output=True)
    for _ in range(100):
        if output_path.exists() and output_path.stat().st_size > 10_000:
            return
        time.sleep(0.1)
    raise RuntimeError(f"PDF output not created: {output_path}")


def locate_pages(pdf_path: Path, records):
    reader = PdfReader(str(pdf_path))
    page_texts = [(page.extract_text() or "") for page in reader.pages]
    marker_map = {}
    for page_index, text in enumerate(page_texts):
        for match in re.findall(r"HMARK\s*(\d{4})", text):
            marker_map[f"HMARK{match}"] = page_index + 1
        for record in records:
            if record["marker"] in text:
                marker_map[record["marker"]] = page_index + 1
    for record in records:
        record["page"] = marker_map.get(record["marker"])
    return len(reader.pages), marker_map


def add_outline_and_metadata(source_pdf: Path, final_pdf: Path, records):
    reader = PdfReader(str(source_pdf))
    writer = PdfWriter()
    for page in reader.pages:
        writer.add_page(page)
    parents = {}
    current_parent = None
    for record in records:
        if not record["page"]:
            continue
        page_index = max(0, min(len(reader.pages) - 1, record["page"] - 1))
        if record["level"] == 1:
            current_parent = writer.add_outline_item(record["title"], page_index)
            parents[record["id"]] = current_parent
        elif record["level"] == 2:
            writer.add_outline_item(record["title"], page_index, parent=current_parent)
    writer.add_metadata({
        "/Title": "项目架构设计与开发理解文档 - 技术白皮书",
        "/Author": "dcim-iot-platform 项目组",
        "/Subject": "系统架构、模块边界、业务流程、工程经验与演进分析",
        "/Keywords": "DCIM, IoT, Spring Boot, Vue, Redis, WebSocket, MySQL, Architecture",
        "/Creator": "Codex + Pandoc + Microsoft Edge",
    })
    with final_pdf.open("wb") as handle:
        writer.write(handle)


def main():
    PUB.mkdir(parents=True, exist_ok=True)
    prepare_markdown()
    fragment = run_pandoc()
    wrapper, records = decorate_fragment(fragment)

    HTML_FILE.write_text(serialize_document(wrapper, records), encoding="utf-8", newline="\n")
    print_pdf(HTML_FILE, FIRST_PDF)
    first_pages, marker_map = locate_pages(FIRST_PDF, records)

    for marker_node in wrapper.xpath(".//span[contains(concat(' ', normalize-space(@class), ' '), ' marker ')]"):
        marker_node.getparent().remove(marker_node)

    HTML_FILE.write_text(serialize_document(wrapper, records), encoding="utf-8", newline="\n")
    print_pdf(HTML_FILE, PRINT_PDF)
    final_pages = len(PdfReader(str(PRINT_PDF)).pages)
    add_outline_and_metadata(PRINT_PDF, FINAL_PDF, records)

    missing = [record["title"] for record in records if not record["page"]]
    print(f"first_pages={first_pages}")
    print(f"final_pages={final_pages}")
    print(f"headings={len(records)} mapped={len(marker_map)} missing={len(missing)}")
    if missing:
        print("missing_headings=" + " | ".join(missing[:10]))
    print(f"output={FINAL_PDF}")

    if PROFILE.exists():
        shutil.rmtree(PROFILE, ignore_errors=True)


if __name__ == "__main__":
    main()