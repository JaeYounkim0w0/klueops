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
AI_PROVIDER_HEADING = "AI Provider와 Local Models"
IMAGE_ALT_TEXTS = (
    "KlueOps 메뉴를 운영 관리, AI, 설정 영역으로 구분한 구조도",
    "감지부터 검증과 해결까지 이어지는 권장 운영 흐름도",
    "Platform, Tenant, Workspace, Cluster, Namespace, Resource의 관리 범위와 데이터 흐름도",
)


def update_phase_two_sections(document: Document) -> None:
    """Phase 2에서 실제 제공하는 Application Delivery와 AI 설정 안내를 반영한다."""
    replacements = {
        "목적  관련 Kubernetes 리소스를 애플리케이션 관점으로 묶어 상태와 제한된 운영 조치를 제공합니다.":
            "목적  Tenant Chart를 Custom Values로 Cluster에 Helm 배포하고 Application 상태와 수명주기를 운영합니다.",
        "사용 시점  개별 Pod보다 서비스 단위로 상태, 분석, Restart와 Rollback을 확인할 때 사용합니다.":
            "사용 시점  Artifact Hub Chart를 가져오거나 보유 Chart를 Cluster와 Namespace에 배포하고 상태를 확인할 때 사용합니다.",
        "AI Analysis에서 식별한 Application의 상태를 확인합니다.":
            "Chart Library를 기본 시작점으로 사용하고 Chart가 없을 때 Discover 또는 Source/.tgz 가져오기를 선택합니다.",
        "상태 동기화, 보호된 Restart, Rollback preview와 실행을 제공합니다.":
            "Values Profile, 대상 Cluster/Namespace, 선택형 HTTPRoute를 설정한 뒤 Preview와 정확한 확인 문구를 거쳐 배포합니다.",
        "Application 범위 AI Analysis와 최근 운영 작업으로 연결합니다.":
            "Application 상세에서 workload/Pod, Service·접근 경로, Helm History와 upgrade/rollback/uninstall을 확인합니다.",
        "처음 사용할 때  현재는 애플리케이션 운영 화면이며 완성된 Docker 또는 Helm 배포 플랫폼이 아닙니다.":
            "처음 사용할 때  Chart Library에서 검증된 버전을 선택하고 내부 Service 방식으로 작은 테스트 배포부터 시작합니다.",
        "주의  미구현 배포 API를 상용 배포 기능으로 해석하면 안 됩니다. GitOps 연동은 후속 범위입니다.":
            "주의  이 기능은 GitOps Controller가 아닙니다. 대상, Values와 Preview를 확인한 명시적 Helm 작업만 실행합니다.",
        "12 Applications와 Argo CD의 향후 방향": "12 Application Delivery 운영 경계",
        "Argo CD는 후속 선택 연동으로 둡니다. 현재 AIOps는 Argo CD가 없어도 Kubernetes API, Event, Log와 Snapshot을 이용해 진단할 수 있어야 합니다. 연동된 환경에서는 배포 변경과 장애의 관계를 더 정확히 설명하고 안전한 수동 Sync를 제공할 수 있습니다.":
            "Application Delivery는 KlueOps가 관리하는 Helm Release를 명시적으로 배포·변경·삭제합니다. Argo CD나 Flux를 설치하거나 Git 상태를 지속 동기화하지 않으며, Tenant가 별도로 운영하는 GitOps Controller와 책임을 섞지 않습니다.",
        "단계별 연동 순서": "안전한 운영 순서",
        "1.  읽기 전용으로 Application, Health, Sync, Drift, Git revision과 배포 이력을 수집합니다.":
            "1.  Tenant Library의 Chart 버전과 Values revision을 고정하고 대상 Cluster와 Namespace를 확인합니다.",
        "2.  배포 시각과 Incident, Event, Log, Rollout 상태를 시간축으로 연결합니다.":
            "2.  Preview에서 렌더링 결과, Secret 마스킹, Namespace와 Exposure 계획을 확인합니다.",
        "3.  Diff와 실행 전 검증을 제공하고 RBAC와 Audit가 적용된 수동 Refresh 및 Sync를 허용합니다.":
            "3.  정확한 확인 문구로 Helm 작업을 시작하고 Job과 Application History에서 진행·실패 단계를 추적합니다.",
        "4.  자동 Prune, 강제 Sync, Application 삭제와 Git 또는 Helm values 직접 수정은 별도 승인 전까지 제외합니다.":
            "4.  작업 후 workload/Pod, Service와 endpoint를 검증하고 필요할 때 preview 후 rollback 또는 uninstall합니다.",
    }
    for paragraph in document.paragraphs:
        if paragraph.text in replacements:
            paragraph.text = replacements[paragraph.text]

    # 더 이상 제품 방향과 맞지 않는 Argo CD 중심 도식과 참고 링크를 제거한다.
    obsolete_caption = next(
        (paragraph for paragraph in document.paragraphs if paragraph.text == "그림 4 AIOps와 Argo CD의 권장 책임 경계"),
        None,
    )
    if obsolete_caption is not None:
        previous_element = obsolete_caption._element.getprevious()
        if previous_element is not None and previous_element.xpath(".//w:drawing"):
            previous_element.getparent().remove(previous_element)
        obsolete_caption._element.getparent().remove(obsolete_caption._element)
    for paragraph in list(document.paragraphs):
        if paragraph.text.startswith("Argo CD 참고"):
            paragraph._element.getparent().remove(paragraph._element)

    if any(paragraph.text == AI_PROVIDER_HEADING for paragraph in document.paragraphs):
        return
    anchor = next(paragraph for paragraph in document.paragraphs if paragraph.text == "계정 및 권한")
    previous = Paragraph(anchor._element.getprevious(), anchor._parent)
    current = insert_after(previous, AI_PROVIDER_HEADING, "Heading 2")
    current = insert_after(current, "목적  Local Ollama와 선택형 외부 LLM 연결, 모델과 Tenant 목적별 사용 경로를 관리합니다.", "Normal")
    current = insert_after(current, "사용 시점  AI Analysis, AI Chat과 Helm Values 제안에 사용할 Provider나 모델을 변경할 때 사용합니다.", "Normal")
    current = insert_after(current, "Platform Manager는 Provider profile과 credential을 등록하고 연결을 검증합니다.", "List Bullet")
    current = insert_after(current, "Tenant 관리자는 허용된 profile/model을 목적별로 선택하고 외부 전송 여부를 명시적으로 설정합니다.", "List Bullet")
    current = insert_after(current, "Local Models에서는 Ollama 설치 모델을 동기화하고 승인 목록의 9B 이하 모델만 추가합니다.", "List Bullet")
    current = insert_after(current, "API key와 credential은 저장 후 다시 표시하지 않으며 화면과 로그에서 마스킹합니다.", "List Bullet")
    insert_after(current, "주의  외부 Provider 사용은 Tenant 데이터 반출 결정입니다. 전송 범위와 fallback을 확인한 뒤 활성화합니다.", "Normal")


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


def apply_image_alt_text(document: Document) -> None:
    """시각 도식을 보지 못하는 사용자도 의미를 알 수 있도록 대체 텍스트를 지정한다."""
    image_properties = document._element.xpath(".//wp:docPr")
    for properties, description in zip(image_properties, IMAGE_ALT_TEXTS, strict=False):
        properties.set("descr", description)
        properties.set("title", description)


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

    update_phase_two_sections(document)

    apply_supported_font(document)
    apply_image_alt_text(document)
    normalize_section_page_breaks(document)
    compact_short_tables(document)
    compact_console_section(document)

    temporary = GUIDE.with_suffix(".docx.tmp")
    document.save(temporary)
    os.replace(temporary, GUIDE)


if __name__ == "__main__":
    main()
