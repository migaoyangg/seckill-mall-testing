from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


OUT = "/Users/migaoyang/Projects/seckill-mall/deliverables/测试开发工程师简历_一页版.docx"
NAVY = RGBColor(31, 78, 121)
DARK = RGBColor(35, 35, 35)
GRAY = RGBColor(95, 95, 95)


def set_cn_font(run, name="STHeiti", size=9.8, bold=False, color=DARK):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:ascii"), name)
    run._element.rPr.rFonts.set(qn("w:hAnsi"), name)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size + 1)
    run.font.bold = bold
    run.font.color.rgb = color


def set_cell_margin(cell, top=20, start=25, bottom=20, end=25):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    tcMar = tcPr.first_child_found_in("w:tcMar")
    if tcMar is None:
        tcMar = OxmlElement("w:tcMar")
        tcPr.append(tcMar)
    for m, v in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = tcMar.find(qn(f"w:{m}"))
        if node is None:
            node = OxmlElement(f"w:{m}")
            tcMar.append(node)
        node.set(qn("w:w"), str(v))
        node.set(qn("w:type"), "dxa")


def shade_cell(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = tcPr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tcPr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_border(cell, color="D9D9D9", size="4"):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = tcPr.first_child_found_in("w:tcBorders")
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tcPr.append(borders)
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        tag = f"w:{edge}"
        node = borders.find(qn(tag))
        if node is None:
            node = OxmlElement(tag)
            borders.append(node)
        node.set(qn("w:val"), "single")
        node.set(qn("w:sz"), size)
        node.set(qn("w:color"), color)


def format_para(p, before=0, after=0, line=1.03):
    f = p.paragraph_format
    f.space_before = Pt(before)
    f.space_after = Pt(after)
    f.line_spacing = line


def add_text(p, text, bold=False, size=9.8, color=DARK):
    r = p.add_run(text)
    set_cn_font(r, size=size, bold=bold, color=color)
    return r


def section_heading(doc, text):
    p = doc.add_paragraph()
    p.style = doc.styles["Heading 1"]
    format_para(p, before=4.5, after=2.5, line=1)
    add_text(p, text, bold=True, size=11.8, color=NAVY)


def bullet(doc, text, marker=None):
    p = doc.add_paragraph(style="List Bullet")
    format_para(p, after=1.4, line=1.1)
    p.paragraph_format.left_indent = Inches(0.17)
    p.paragraph_format.first_line_indent = Inches(-0.13)
    add_text(p, text, size=9.35)
    if marker:
        add_text(p, marker, size=9.0, color=GRAY)


doc = Document()
sec = doc.sections[0]
sec.page_width = Inches(8.5)
sec.page_height = Inches(11)
sec.top_margin = Inches(0.34)
sec.bottom_margin = Inches(0.32)
sec.left_margin = Inches(0.48)
sec.right_margin = Inches(0.48)
sec.header_distance = Inches(0.15)
sec.footer_distance = Inches(0.15)

normal = doc.styles["Normal"]
normal.font.name = "STHeiti"
normal._element.rPr.rFonts.set(qn("w:ascii"), "STHeiti")
normal._element.rPr.rFonts.set(qn("w:hAnsi"), "STHeiti")
normal._element.rPr.rFonts.set(qn("w:eastAsia"), "STHeiti")
normal.font.size = Pt(10.8)
normal.font.color.rgb = DARK
normal.paragraph_format.space_after = Pt(0)

for style_name in ("Title", "Heading 1", "Heading 2"):
    style = doc.styles[style_name]
    style.font.name = "STHeiti"
    style._element.rPr.rFonts.set(qn("w:ascii"), "STHeiti")
    style._element.rPr.rFonts.set(qn("w:hAnsi"), "STHeiti")
    style._element.rPr.rFonts.set(qn("w:eastAsia"), "STHeiti")
    style.font.color.rgb = RGBColor(0, 0, 0)

# Header with photo placeholder
head = doc.add_table(rows=1, cols=2)
head.autofit = False
head.columns[0].width = Inches(6.55)
head.columns[1].width = Inches(0.95)
left = head.cell(0, 0)
right = head.cell(0, 1)
for c in (left, right):
    set_cell_margin(c, 15, 15, 15, 15)
    set_cell_border(c, color="FFFFFF", size="0")
    c.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER

p = left.paragraphs[0]
p.style = doc.styles["Title"]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
format_para(p, after=1.5, line=1)
add_text(p, "蒙冠源", bold=True, size=20, color=RGBColor(0, 0, 0))

p = left.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
format_para(p, after=2, line=1)
add_text(p, "求职意向  软件测试开发工程师", bold=True, size=10.2, color=NAVY)

p = left.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
format_para(p, after=1, line=1)
add_text(p, "电话  16677266727  ｜  邮箱  migoyang66@163.com  ｜  意向城市  北京 / 天津", size=8.2, color=GRAY)

set_cell_border(right, color="BFBFBF", size="8")
shade_cell(right, "F2F2F2")
p = right.paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
format_para(p, before=24, after=24, line=1)
add_text(p, "证件照\n位置", bold=True, size=9.0, color=GRAY)

section_heading(doc, "教育经历")
t = doc.add_table(rows=1, cols=2)
t.autofit = False
t.columns[0].width = Inches(5.9)
t.columns[1].width = Inches(1.55)
for c in t.rows[0].cells:
    set_cell_margin(c, 0, 0, 0, 0)
    c.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    set_cell_border(c, color="FFFFFF", size="0")
p = t.cell(0, 0).paragraphs[0]
format_para(p, line=1)
add_text(p, "天津中德应用技术大学  ｜  软件工程（全日制本科）", bold=True, size=9.3)
p = t.cell(0, 1).paragraphs[0]
p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
format_para(p, line=1)
add_text(p, "2023.09 - 2027.06", size=8.8, color=GRAY)

p = doc.add_paragraph()
format_para(p, before=1, after=1.5, line=1.04)
add_text(p, "主修课程  ", bold=True, size=8.8, color=NAVY)
add_text(p, "软件测试基础、数据结构与算法、操作系统、计算机网络、数据库原理、Python 程序设计、Java 程序设计", size=8.75, color=GRAY)

section_heading(doc, "项目经历")

def project_header(title, date, tech, link, role="个人独立开发"):
    table = doc.add_table(rows=1, cols=2)
    table.autofit = False
    table.columns[0].width = Inches(6.10)
    table.columns[1].width = Inches(1.40)
    for cell in table.rows[0].cells:
        set_cell_margin(cell, 0, 0, 0, 0)
        set_cell_border(cell, color="FFFFFF", size="0")
    p1 = table.cell(0, 0).paragraphs[0]
    format_para(p1, before=2, after=1, line=1)
    add_text(p1, title, bold=True, size=10.7, color=RGBColor(0, 0, 0))
    if len(link) <= 55:
        add_text(p1, "   GitHub  ", bold=True, size=7.2, color=GRAY)
        add_text(p1, link, size=7.2, color=GRAY)
    else:
        p_link = table.cell(0, 0).add_paragraph()
        format_para(p_link, after=0.5, line=1)
        add_text(p_link, "GitHub  ", bold=True, size=7.2, color=GRAY)
        add_text(p_link, link, size=7.2, color=GRAY)
    p2 = table.cell(0, 1).paragraphs[0]
    p2.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    format_para(p2, before=1, after=0.5, line=1)
    add_text(p2, date, size=9.0, color=GRAY)
    p2 = table.cell(0, 1).add_paragraph()
    p2.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    format_para(p2, after=0.5, line=1)
    add_text(p2, role, bold=True, size=7.5, color=NAVY)
    p = doc.add_paragraph()
    format_para(p, after=1.3, line=1)
    add_text(p, tech, bold=True, size=8.65, color=NAVY)


def project_description(text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(0.10)
    p.paragraph_format.right_indent = Inches(0.04)
    format_para(p, after=1.8, line=1.06)
    pPr = p._p.get_or_add_pPr()
    pBdr = OxmlElement("w:pBdr")
    left_border = OxmlElement("w:left")
    left_border.set(qn("w:val"), "single")
    left_border.set(qn("w:sz"), "18")
    left_border.set(qn("w:space"), "6")
    left_border.set(qn("w:color"), "2F75B5")
    pBdr.append(left_border)
    pPr.append(pBdr)
    add_text(p, "项目描述  ", bold=True, size=8.9, color=NAVY)
    add_text(p, text, size=8.95, color=GRAY)


def bullet_labeled(label, text, marker=None):
    p = doc.add_paragraph(style="List Bullet")
    format_para(p, after=1.2, line=1.08)
    p.paragraph_format.left_indent = Inches(0.17)
    p.paragraph_format.first_line_indent = Inches(-0.13)
    add_text(p, label + "：", bold=True, size=9.25)
    add_text(p, text, size=9.2)
    if marker:
        add_text(p, "  " + marker, size=8.9, color=GRAY)


project_header(
    "秒购商城全链路自动化测试体系",
    "2026.07 至今",
    "Java  ·  JUnit 5  ·  MockMvc  ·  Python  ·  pytest  ·  requests  ·  Appium  ·  UiAutomator2  ·  JMeter",
    "https://github.com/migaoyangg/seckill-mall-testing",
)
project_description(
    "面向用户、商品、订单、支付退款和限时秒杀业务，建立服务端接口与 Android 客户端分层自动化测试体系，覆盖权限、异常、幂等、状态流转及端到端业务流程。",
)
bullet_labeled("服务端自动化", "使用 JUnit 5、Mockito、MockMvc 覆盖登录、缓存、订单取消、库存回补、支付幂等及秒杀异常回滚；基于 pytest + requests 封装多角色账号、Token、公共断言和数据清理。")
bullet_labeled("移动端自动化", "基于 Appium + UiAutomator2 + Page Object 封装登录、首页和商品弹窗页面，覆盖登录校验、会话保持、商品浏览、订单切换及创建订单，失败时采集截图、页面 XML 和 Logcat。")
bullet_labeled("性能与一致性", "使用 JMeter 在单机开发环境执行 100 用户瞬时并发，吞吐量 123.30 请求/秒，P95 255.6 ms、P99 283.9 ms，错误率 0%；100 笔订单全部异步落库，重复订单、库存差值和队列积压均为 0。")
bullet_labeled("缺陷定位", "结合接口响应、应用日志及 MySQL/Redis 状态，定位并修复参数类型异常返回 HTTP 500、管理员路径漏拦截和 Token fixture 复用导致的测试不稳定问题。")
bullet_labeled("测试规模", "累计沉淀 79 条自动化用例（Java 38 条、HTTP 接口 28 条、Android UI 13 条），覆盖用户、商品、订单、支付退款、秒杀及 Android 核心链路；Java、HTTP 接口与 Android UI 全量回归分别 38/38、28/28、13/13 通过。")

project_header(
    "TestFlow 测试任务调度与质量分析平台",
    "2026.09 至今",
    "Python  ·  FastAPI  ·  SQLAlchemy  ·  SQLite  ·  Redis  ·  WebSocket  ·  pytest  ·  Appium  ·  ADB",
    "https://github.com/migaoyangg/seckill-mall-testing/tree/main/testflow-platform",
)
project_description(
    "针对自动化脚本分散执行、环境配置不统一、Android 设备争抢和测试证据难追溯的问题，开发统一测试调度平台，形成配置、调度、执行、分析与归档闭环。",
)
bullet_labeled("调度与执行", "设计项目、环境、套件、任务、用例结果和证据模型，通过异步 Worker 调用 pytest，支持状态流转、超时取消、失败重跑、定时计划及 Redis 队列，并自动识别套件虚拟环境。")
bullet_labeled("权限与安全", "实现 ADMIN、TESTER、VIEWER 三级 RBAC，采用 PBKDF2-SHA256 加盐存储密码、摘要化保存会话令牌；限制执行目录和 pytest marker，并使用无 shell 子进程。")
bullet_labeled("设备与质量分析", "通过 ADB 发现设备并实现独占锁，自动管理 Appium 生命周期；使用 WebSocket 推送日志，解析 JUnit XML，归档测试证据并统计通过率、平均耗时、执行趋势和高频失败用例。")
bullet_labeled("核心成果", "平台核心模块测试 8/8 通过；成功调度接口冒烟 12/12 和 Android UI 回归 13/13，任务结束后自动释放设备锁与平台启动的 Appium 进程，完成全链路验证。")

section_heading(doc, "专业技能")
skill_texts = [
    ("编程与自动化", "Java、Python、SQL；JUnit 5、Mockito、MockMvc、pytest、requests、Appium、Page Object"),
    ("接口与性能", "Postman、curl、JMeter；掌握接口关联、参数化及 P95/P99、错误率等指标"),
    ("测试报告与工程", "Allure、pytest-html；MySQL、Redis、RabbitMQ、Git、Maven、Linux、ADB、Docker Compose、GitHub Actions"),
    ("AI 工具", "使用 ChatGPT、Codex 辅助用例设计、脚本开发、代码检查、问题排查和文档整理"),
]
for label, value in skill_texts:
    p = doc.add_paragraph(style="List Bullet")
    format_para(p, after=1.0, line=1.04)
    p.paragraph_format.left_indent = Inches(0.17)
    p.paragraph_format.first_line_indent = Inches(-0.13)
    add_text(p, label + "：", bold=True, size=9.0)
    add_text(p, value, size=8.95)

doc.save(OUT)
print(OUT)
