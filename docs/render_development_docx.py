"""Build an editable Word version of DEVELOPMENT_REPORT.md."""

import re
from pathlib import Path

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor
from docx.opc.constants import RELATIONSHIP_TYPE


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "DEVELOPMENT_REPORT.md"
OUTPUT = ROOT / "BandDecision_Development_Report.docx"
INLINE = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`|\[[^]]+\]\([^)]+\))")
BLUE = RGBColor(31, 69, 149)
PALE = "E9EFFB"
ALT = "F7F9FD"


def font(run, name="맑은 고딕", size=None, bold=None, color=None):
    run.font.name = name
    run._element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:eastAsia"), name)
    if size is not None:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if color is not None:
        run.font.color.rgb = color
    return run


def hyperlink(paragraph, label, target):
    relationship = paragraph.part.relate_to(target, RELATIONSHIP_TYPE.HYPERLINK, is_external=True)
    link = OxmlElement("w:hyperlink")
    link.set(qn("r:id"), relationship)
    text_run = OxmlElement("w:r")
    properties = OxmlElement("w:rPr")
    color = OxmlElement("w:color")
    color.set(qn("w:val"), "1F4595")
    properties.append(color)
    text_run.append(properties)
    value = OxmlElement("w:t")
    value.text = label
    text_run.append(value)
    link.append(text_run)
    paragraph._p.append(link)


def inline(paragraph, text, small=False):
    last = 0
    for match in INLINE.finditer(text):
        if match.start() > last:
            font(paragraph.add_run(text[last:match.start()]), size=8.5 if small else 10)
        token = match.group()
        if token.startswith("**"):
            font(paragraph.add_run(token[2:-2]), size=8.5 if small else 10, bold=True)
        elif token.startswith("`"):
            font(paragraph.add_run(token[1:-1]), "Consolas", 7.8 if small else 9, color=BLUE)
        else:
            label, target = token[1:].split("](", 1)
            hyperlink(paragraph, label, target[:-1])
        last = match.end()
    if last < len(text):
        font(paragraph.add_run(text[last:]), size=8.5 if small else 10)


def shade(cell, fill):
    properties = cell._tc.get_or_add_tcPr()
    color = OxmlElement("w:shd")
    color.set(qn("w:fill"), fill)
    properties.append(color)


def no_split(row):
    properties = row._tr.get_or_add_trPr()
    properties.append(OxmlElement("w:cantSplit"))


def repeat_header(row):
    properties = row._tr.get_or_add_trPr()
    item = OxmlElement("w:tblHeader")
    item.set(qn("w:val"), "true")
    properties.append(item)


def build_table(document, lines):
    data = [[value.strip() for value in line.strip().strip("|").split("|")] for line in lines]
    data = [data[0]] + data[2:]
    columns = len(data[0])
    table = document.add_table(rows=len(data), cols=columns)
    table.autofit = False
    table.style = "Table Grid"
    widths = [Cm(3.4), Cm(7.2), Cm(6.7)] if columns == 3 else [Cm(6.7), Cm(10.6)]
    if columns != 2 and columns != 3:
        widths = [Cm(17.3 / columns)] * columns
    for row_number, values in enumerate(data):
        row = table.rows[row_number]
        no_split(row)
        if row_number == 0:
            repeat_header(row)
        for column, value in enumerate(values):
            cell = row.cells[column]
            cell.width = widths[column]
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            if row_number == 0:
                shade(cell, PALE)
            elif row_number % 2 == 0:
                shade(cell, ALT)
            paragraph = cell.paragraphs[0]
            paragraph.style = "Normal"
            paragraph.paragraph_format.space_after = Pt(2)
            paragraph.paragraph_format.space_before = Pt(2)
            paragraph.paragraph_format.line_spacing = 1.1
            inline(paragraph, value, small=True)
            if row_number == 0:
                for run in paragraph.runs:
                    run.bold = True
                    run.font.color.rgb = BLUE
    document.add_paragraph().paragraph_format.space_after = Pt(1)


def make_document():
    document = Document()
    section = document.sections[0]
    section.page_height = Cm(29.7)
    section.page_width = Cm(21)
    section.top_margin = Cm(1.7)
    section.bottom_margin = Cm(1.7)
    section.left_margin = Cm(1.8)
    section.right_margin = Cm(1.8)

    normal = document.styles["Normal"]
    normal.font.name = "맑은 고딕"
    normal.font.size = Pt(10)
    normal._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.17
    title = document.styles["Title"]
    title.font.name = "맑은 고딕"
    title._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
    title.font.size = Pt(21)
    title.font.bold = True
    title.font.color.rgb = RGBColor(0, 0, 0)
    title.paragraph_format.space_after = Pt(13)
    for style_name, size, before, after in [
        ("Heading 1", 15, 18, 8),
        ("Heading 2", 12, 13, 6),
    ]:
        style = document.styles[style_name]
        style.font.name = "맑은 고딕"
        style._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = BLUE
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.keep_with_next = True

    lines = SOURCE.read_text(encoding="utf-8").splitlines()
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if not line:
            index += 1
            continue
        if line.startswith("| "):
            table_lines = []
            while index < len(lines) and lines[index].startswith("|"):
                table_lines.append(lines[index])
                index += 1
            build_table(document, table_lines)
            continue
        if line.startswith("```"):
            index += 1
            while index < len(lines) and not lines[index].startswith("```"):
                paragraph = document.add_paragraph(style="Normal")
                paragraph.paragraph_format.left_indent = Cm(0.5)
                paragraph.paragraph_format.space_after = Pt(0)
                font(paragraph.add_run(lines[index]), "Consolas", 8.5)
                index += 1
            index += 1
            continue
        if line.startswith("# "):
            document.add_paragraph(line[2:], style="Title")
        elif line.startswith("## "):
            document.add_paragraph(line[3:], style="Heading 1")
        elif line.startswith("### "):
            document.add_paragraph(line[4:], style="Heading 2")
        else:
            paragraph = document.add_paragraph(style="Normal")
            inline(paragraph, line)
        index += 1
    document.save(OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    make_document()
