#!/usr/bin/env python3
"""Apply idempotent operator-guide updates to the maintained DOCX artifact."""

from pathlib import Path
import os

from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt
from docx.text.paragraph import Paragraph


GUIDE = Path(__file__).with_name("K8s-AI-Ops-Platform-menu-guide.docx")
COLLECTION_GUIDANCE = (
    "Analysis Runtime의 Kubernetes 정보 수집에서 COMPLETE 또는 PARTIAL 상태와 "
    "source별 성공, 실패, 건너뜀, 지연을 확인합니다. PARTIAL이면 실패 source를 "
    "확인하고 credential, API 연결 또는 권한을 복구한 뒤 같은 범위를 재분석합니다."
)
# Keep a macOS Word-compatible Hangul font explicitly assigned to every run.
# The bundled headless LibreOffice renderer on this host does not expose Hangul
# glyphs, so DOCX text/font integrity and page geometry are verified separately.
GUIDE_FONT = "AppleGothic"
CONSOLE_HEADING = "Kubernetes 콘솔"
CONSOLE_VERIFICATION_GUIDANCE = (
    "변경 또는 삭제 명령이 끝나면 운영 결과 검증에서 Kubernetes 상태의 전후 비교 판정과 "
    "확인 시각을 먼저 봅니다. 상태가 확인되지 않으면 성공 exit code만으로 조치 완료를 선언하지 않습니다."
)
CONSOLE_COOKBOOK_GUIDANCE = (
    "Cook Book 탭에서는 기본 현황, Pod, 로그, Service, workload, 자원, Node, storage, network와 "
    "권한 점검 명령을 검색합니다. 항목의 목적, 실행 범위와 요구 조건을 확인한 뒤 명령 가져오기를 선택합니다."
)
CONSOLE_GUIDED_COOKBOOK_GUIDANCE = (
    "증상별 단계 점검에서 Pod 기동, Service 연결, PVC Pending, Node Pressure 절차를 순서대로 실행하고 "
    "각 항목의 정상 기준과 다음 권장 점검을 확인합니다."
)
RETENTION_GUIDANCE = (
    "Audit log와 완료된 Command execution 보관 기간도 설정할 수 있습니다. 미리보기에서 삭제 건수를 확인한 뒤 "
    "정리를 실행하며, 실행 중인 명령과 Kubernetes 실제 리소스는 삭제하지 않습니다."
)
INCIDENT_EVIDENCE_GUIDANCE = (
    "Incident 보고서를 내보내면 원본 분석과 연결된 명령의 실행자, 시각, 종료 코드, 검증 판정과 "
    "전후 Snapshot 해시가 포함됩니다. credential, 실시간 로그와 Snapshot 원문은 포함되지 않습니다."
)


def insert_after(paragraph: Paragraph, text: str, style: str) -> Paragraph:
    new_element = OxmlElement("w:p")
    paragraph._element.addnext(new_element)
    inserted = Paragraph(new_element, paragraph._parent)
    inserted.style = style
    inserted.add_run(text)
    return inserted


def apply_supported_font(document: Document) -> None:
    def set_run_fonts(properties) -> None:
        fonts = properties.rFonts
        for attribute in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
            fonts.set(qn(attribute), GUIDE_FONT)

    for style in document.styles:
        if not hasattr(style, "font"):
            continue
        style.font.name = GUIDE_FONT
        set_run_fonts(style._element.get_or_add_rPr())

    for paragraph in document.paragraphs:
        for run in paragraph.runs:
            run.font.name = GUIDE_FONT
            set_run_fonts(run._element.get_or_add_rPr())

    for table in document.tables:
        for row in table.rows:
            for cell in row.cells:
                for paragraph in cell.paragraphs:
                    for run in paragraph.runs:
                        run.font.name = GUIDE_FONT
                        set_run_fonts(run._element.get_or_add_rPr())


def normalize_section_page_breaks(document: Document) -> None:
    paragraphs = document.paragraphs
    for index, paragraph in enumerate(paragraphs[:-1]):
        next_paragraph = paragraphs[index + 1]
        if not next_paragraph.style.name.startswith("Heading"):
            continue

        page_breaks = [
            element
            for element in paragraph._element.iter(qn("w:br"))
            if element.get(qn("w:type")) == "page"
        ]
        if not page_breaks:
            continue

        for page_break in page_breaks:
            page_break.getparent().remove(page_break)
        next_paragraph.paragraph_format.page_break_before = True
        if not paragraph.text.strip():
            paragraph._element.getparent().remove(paragraph._element)

    paragraphs = document.paragraphs
    for index, paragraph in enumerate(paragraphs[1:], start=1):
        previous = paragraphs[index - 1]
        if paragraph.paragraph_format.page_break_before and not previous.text.strip():
            previous._element.getparent().remove(previous._element)


def compact_short_tables(document: Document) -> None:
    for table_index in (1, 3):
        table = document.tables[table_index]
        for row_index, row in enumerate(table.rows):
            row_properties = row._tr.get_or_add_trPr()
            if row_properties.find(qn("w:cantSplit")) is None:
                row_properties.append(OxmlElement("w:cantSplit"))
            for cell in row.cells:
                for paragraph in cell.paragraphs:
                    paragraph.paragraph_format.space_before = Pt(0)
                    paragraph.paragraph_format.space_after = Pt(0)
                    paragraph.paragraph_format.line_spacing = 1
                    for run in paragraph.runs:
                        run.font.size = Pt(8.5 if row_index == 0 else 8)


def compact_console_section(document: Document) -> None:
    in_console_section = False
    for paragraph in document.paragraphs:
        if paragraph.text == CONSOLE_HEADING:
            in_console_section = True
            continue
        if in_console_section and paragraph.style.name.startswith("Heading 2"):
            break
        if not in_console_section:
            continue
        paragraph.paragraph_format.space_before = Pt(0)
        paragraph.paragraph_format.space_after = Pt(1)
        paragraph.paragraph_format.line_spacing = 1
        for run in paragraph.runs:
            run.font.size = Pt(9)


def main() -> None:
    document = Document(GUIDE)
    for paragraph in document.paragraphs:
        if paragraph.text == "좌측 메뉴의 목적과 권장 운영 흐름":
            paragraph.text = "플랫폼 기능과 권장 운영 흐름"
        elif paragraph.text.startswith("좌측 메뉴는 운영 관리, AI, 설정의 세 영역"):
            paragraph.text = paragraph.text.replace("좌측 메뉴는", "플랫폼 기능은", 1)

    if not any(COLLECTION_GUIDANCE == paragraph.text for paragraph in document.paragraphs):
        anchor = next(
            paragraph
            for paragraph in document.paragraphs
            if paragraph.text
            == "Evidence Ledger, Confidence, Runtime과 deterministic fallback을 표시합니다."
        )
        insert_after(anchor, COLLECTION_GUIDANCE, "List Bullet")

    if not any(paragraph.text == CONSOLE_HEADING for paragraph in document.paragraphs):
        anchor = next(paragraph for paragraph in document.paragraphs if paragraph.text.startswith("주의  Secret 값과 kubeconfig"))
        current = insert_after(anchor, CONSOLE_HEADING, "Heading 2")
        current = insert_after(current, "목적  선택한 원격 클러스터에서 kubectl 명령과 Pod 터미널을 플랫폼 권한 및 감사 경계 안에서 실행합니다.", "Normal")
        current = insert_after(current, "사용 시점  리소스 상세 확인, AI 분석 검증 명령 실행, Pod 내부 상태 확인이 필요할 때 사용합니다.", "Normal")
        current = insert_after(current, "Cluster와 Namespace는 플랫폼이 고정하며 kubeconfig와 context 변경 옵션은 사용할 수 없습니다.", "List Bullet")
        current = insert_after(current, "조회 명령은 바로 실행하고 변경·삭제·대화형 명령은 대상과 위험도를 확인한 뒤 실행합니다.", "List Bullet")
        current = insert_after(current, "실행 이력에서 상태, 종료 코드, 소요 시간, stdout과 stderr를 확인합니다.", "List Bullet")
        current = insert_after(current, "AI Analysis의 콘솔에서 검증을 사용하면 실행 이력이 원본 분석과 연결됩니다.", "List Bullet")
        current = insert_after(current, "동시 실행 제한 메시지가 표시되면 진행 중인 작업을 확인하고 안내된 시간 뒤 다시 실행합니다.", "List Bullet")
        current = insert_after(current, "처음 사용할 때  kubectl get pods -o wide 같은 읽기 전용 명령으로 권한과 연결을 확인합니다.", "Normal")
        insert_after(current, "주의  Pod 터미널 종료 전 exit를 실행하고, 변경 명령은 실행 후 리소스 상태 확인과 재분석으로 결과를 검증합니다.", "Normal")

    if not any(paragraph.text == CONSOLE_VERIFICATION_GUIDANCE for paragraph in document.paragraphs):
        anchor = next(
            paragraph for paragraph in document.paragraphs
            if paragraph.text.startswith("주의  Pod 터미널 종료 전 exit")
        )
        current = insert_after(anchor, CONSOLE_VERIFICATION_GUIDANCE, "Normal")
        current = insert_after(current, "리소스 기반 명령 만들기에서 Kind와 이름을 입력하면 Get, Describe, Logs, YAML 명령을 안전하게 작성할 수 있습니다.", "List Bullet")
        current = insert_after(current, "Event 확인, 로그 확인, YAML 확인 버튼은 같은 Cluster와 Namespace를 유지한 후속 명령을 준비합니다.", "List Bullet")
        current = insert_after(current, "전후 Snapshot은 필요할 때만 펼쳐 보고, 롤백 후보는 현재 상태와 영향을 다시 확인한 뒤 별도 명령으로 실행합니다.", "List Bullet")
        insert_after(current, "일반 명령은 격리 실행기를 사용합니다. Pod 터미널은 현재 Backend Fabric8 WebSocket 경계를 사용하며 화면의 실행 경계 표시로 구분합니다.", "List Bullet")

    if not any(paragraph.text == CONSOLE_COOKBOOK_GUIDANCE for paragraph in document.paragraphs):
        anchor = next(
            paragraph for paragraph in document.paragraphs
            if paragraph.text == "리소스 기반 명령 만들기에서 Kind와 이름을 입력하면 Get, Describe, Logs, YAML 명령을 안전하게 작성할 수 있습니다."
        )
        current = insert_after(anchor, CONSOLE_COOKBOOK_GUIDANCE, "List Bullet")
        current = insert_after(current, "POD_NAME, SERVICE_NAME 같은 표시가 있으면 실제 리소스 이름으로 바꾸기 전에는 실행할 수 없습니다.", "List Bullet")
        current = insert_after(current, "클러스터 범위 항목은 전체 Namespace로, Namespace 항목은 현재 선택한 Namespace로 작업 범위를 맞춥니다.", "List Bullet")
        current = insert_after(current, "Metrics Server 필요 표시가 있는 사용량 명령은 대상 클러스터에 지표 API가 없으면 결과를 제공하지 못합니다.", "List Bullet")
        insert_after(current, "Cook Book은 Secret 값 출력, shell pipe, port-forward와 임시 리소스 생성 명령을 제공하지 않습니다.", "List Bullet")

    if not any(paragraph.text == CONSOLE_GUIDED_COOKBOOK_GUIDANCE for paragraph in document.paragraphs):
        anchor = next(paragraph for paragraph in document.paragraphs if paragraph.text == CONSOLE_COOKBOOK_GUIDANCE)
        current = insert_after(anchor, CONSOLE_GUIDED_COOKBOOK_GUIDANCE, "List Bullet")
        current = insert_after(current, "리소스 기반 명령 만들기에 입력한 이름과 컨테이너는 Cook Book 명령의 placeholder에 자동 반영됩니다.", "List Bullet")
        insert_after(current, "Metrics APIService가 Available 상태가 아니면 kubectl top 점검은 선택할 수 없습니다.", "List Bullet")

    if not any(paragraph.text == RETENTION_GUIDANCE for paragraph in document.paragraphs):
        anchor = next(paragraph for paragraph in document.paragraphs if paragraph.text == "Event, Analysis, Job, Notification, Incident와 Change 보관 기간을 설정합니다.")
        insert_after(anchor, RETENTION_GUIDANCE, "List Bullet")

    if not any(paragraph.text == INCIDENT_EVIDENCE_GUIDANCE for paragraph in document.paragraphs):
        anchor = next(
            paragraph for paragraph in document.paragraphs
            if paragraph.text == "상세 화면에서 상태 전환, 타임라인, 재발 정보와 보고서를 확인합니다."
        )
        insert_after(anchor, INCIDENT_EVIDENCE_GUIDANCE, "List Bullet")

    apply_supported_font(document)
    normalize_section_page_breaks(document)
    compact_short_tables(document)
    compact_console_section(document)

    temporary = GUIDE.with_suffix(".docx.tmp")
    document.save(temporary)
    os.replace(temporary, GUIDE)


if __name__ == "__main__":
    main()
