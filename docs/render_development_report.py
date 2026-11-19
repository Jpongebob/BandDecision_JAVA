"""Render DEVELOPMENT_REPORT.md to PDF with Python's stdlib and local Microsoft Edge."""

from __future__ import annotations

import html
import re
import subprocess
import time
import uuid
from pathlib import Path


HERE = Path(__file__).resolve().parent
SOURCE = HERE / "DEVELOPMENT_REPORT.md"
OUTPUT = HERE.parent / "out" / "report-preview" / "development-report.html"
PDF = HERE / "BandDecision_Development_Report.pdf"
EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")


def inline(value: str) -> str:
    placeholders: list[str] = []

    def link(match: re.Match[str]) -> str:
        label, target = match.groups()
        token = f"@@LINK{len(placeholders)}@@"
        # The PDF is submitted on its own. Local file URLs would point to this
        # computer's temporary HTML folder, so print a source filename instead.
        filename = Path(target).name
        placeholders.append(f'<span class="reference">{html.escape(label)} '
                            f'<small>({html.escape(filename)})</small></span>')
        return token

    value = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", link, value)
    value = html.escape(value)
    value = re.sub(r"`([^`]+)`", r"<code>\1</code>", value)
    value = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", value)
    for index, replacement in enumerate(placeholders):
        value = value.replace(f"@@LINK{index}@@", replacement)
    return value


def render(source: str) -> str:
    lines = source.splitlines()
    parts: list[str] = []
    paragraph: list[str] = []

    def flush() -> None:
        if paragraph:
            parts.append("<p>" + inline(" ".join(paragraph)) + "</p>")
            paragraph.clear()

    index = 0
    while index < len(lines):
        line = lines[index]
        if not line.strip():
            flush()
            index += 1
            continue
        if line.startswith("```"):
            flush()
            language = line[3:].strip()
            code: list[str] = []
            index += 1
            while index < len(lines) and not lines[index].startswith("```"):
                code.append(lines[index])
                index += 1
            parts.append(f'<pre class="{html.escape(language)}">{html.escape(chr(10).join(code))}</pre>')
            index += 1
            continue
        if line.startswith("|") and index + 1 < len(lines) and re.fullmatch(r"[|:\- ]+", lines[index + 1]):
            flush()
            headers = [cell.strip() for cell in line.strip().strip("|").split("|")]
            parts.append("<table><thead><tr>" + "".join(f"<th>{inline(cell)}</th>" for cell in headers) + "</tr></thead><tbody>")
            index += 2
            while index < len(lines) and lines[index].startswith("|"):
                cells = [cell.strip() for cell in lines[index].strip().strip("|").split("|")]
                if len(cells) != len(headers):
                    raise ValueError(f"Table column mismatch at line {index + 1}")
                parts.append("<tr>" + "".join(f"<td>{inline(cell)}</td>" for cell in cells) + "</tr>")
                index += 1
            parts.append("</tbody></table>")
            continue
        heading = re.match(r"^(#{1,3}) (.+)$", line)
        if heading:
            flush()
            level = len(heading.group(1))
            parts.append(f"<h{level}>{inline(heading.group(2))}</h{level}>")
            index += 1
            continue
        paragraph.append(line.strip())
        index += 1
    flush()
    return "\n".join(parts)


def main() -> None:
    body = render(SOURCE.read_text(encoding="utf-8"))
    page = """<!doctype html><html lang="ko"><head><meta charset="utf-8">
<title>BandDecision 개발 문서</title><style>
@page { size: A4; margin: 16mm 17mm 16mm; }
* { box-sizing: border-box; }
body { font-family: "Malgun Gothic", "맑은 고딕", sans-serif; font-size: 10.5pt; line-height: 1.6; color: #202630; }
h1 { font-size: 24pt; color: #111827; margin: 0 0 7mm; }
h2 { font-size: 16pt; color: #163a89; margin: 6mm 0 4mm; break-after: avoid; }
h2:nth-of-type(n+2) { break-before: page; margin-top: 0; }
h3 { font-size: 11pt; color: #163a89; margin: 5mm 0 2mm; break-after: avoid; }
p { margin: 0 0 3mm; orphans: 3; widows: 3; }
table { width: 100%; border-collapse: collapse; margin: 3mm 0 5mm; font-size: 9.3pt; line-height: 1.5; table-layout: auto; }
thead { display: table-header-group; }
th { background: #eaf0ff; color: #17377b; text-align: left; font-weight: bold; }
th, td { border: 1px solid #d2dbee; padding: 2.1mm 2.2mm; vertical-align: top; overflow-wrap: anywhere; word-break: keep-all; }
tr { break-inside: avoid; }
tr:nth-child(even) td { background: #f8faff; }
code { font-family: Consolas, "Malgun Gothic", monospace; font-size: .92em; color: #17377b; overflow-wrap: anywhere; }
pre { padding: 3mm; background: #f2f5fb; border: 1px solid #d8e1f1; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 8.7pt; break-inside: avoid; }
pre code { font-size: inherit; }
.reference { color: #174dc7; }
.reference small { color: #536179; font-size: .83em; }
strong { color: #102b69; }
</style></head><body>""" + body + "</body></html>"
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(page, encoding="utf-8")
    if not EDGE.exists():
        raise RuntimeError("Microsoft Edge를 찾을 수 없습니다. HTML을 브라우저에서 열어 PDF로 인쇄하세요: " + str(OUTPUT))
    profile = OUTPUT.parent / ("edge-profile-" + uuid.uuid4().hex)
    profile.mkdir()
    PDF.unlink(missing_ok=True)
    subprocess.run(
        [str(EDGE), "--headless", "--disable-gpu", "--no-sandbox", "--disable-crash-reporter",
         "--no-first-run", f"--user-data-dir={profile}", f"--print-to-pdf={PDF}",
         "--no-pdf-header-footer", OUTPUT.as_uri()],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30, check=False,
    )
    for _ in range(40):
        if PDF.exists() and PDF.stat().st_size > 1000:
            print(PDF)
            return
        time.sleep(0.25)
    raise RuntimeError("PDF 생성에 실패했습니다. HTML 파일을 확인하세요: " + str(OUTPUT))


if __name__ == "__main__":
    main()
