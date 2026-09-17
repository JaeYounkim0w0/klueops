#!/usr/bin/env python3
"""현재 KlueOps 운영 화면을 기준으로 사용자 가이드를 생성한다."""

from pathlib import Path

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / "K8s-AI-Ops-Platform-menu-guide.docx"
SCREENSHOTS = ROOT / "screenshots"
# Word, LibreOffice, macOS에서 한글 글리프가 동일하게 보이는 범용 글꼴을 사용한다.
FONT = "Arial Unicode MS"
BLUE = "1F66D1"
DARK_BLUE = "163A63"
PALE = "F5F7FA"
GRAY = "D9E0E8"


SCREENS = [
    ("운영 현황과 대응", "Dashboard", "01-dashboard.png",
     "등록된 클러스터의 상태, 열린 Incident, 정책 위반, AI 분석과 실행 중 작업을 한 번에 파악하고 대응 우선순위를 정합니다.",
     ["Tenant와 업무 그룹을 확인한 뒤 핵심 운영 지표에서 이상 수치를 찾습니다.", "지금 확인할 항목은 심각도와 반복 여부를 반영한 순서이므로 첫 대응 진입점으로 사용합니다.", "운영 근거 갱신은 최신 동기화 결과를 다시 불러오며 Kubernetes 자체 상태를 변경하지 않습니다."],
     "과거 Event가 남아 있을 수 있으므로 발생 시각과 현재 workload 상태를 함께 확인합니다."),
    ("운영 현황과 대응", "실시간 운영 판단", "02-triage.png",
     "여러 신호를 영향도, 위험도, 발생 시각과 처리 상태로 정렬해 지금 조사할 대상을 결정합니다.",
     ["범위와 상태 필터로 조사 대상을 줄이고 위험도가 높은 항목부터 근거를 펼칩니다.", "초보자용 설명은 문제 의미와 확인 순서를 제공하고 숙련자용 정보는 원본 근거를 빠르게 보여줍니다.", "분석 또는 Incident로 이동한 뒤 같은 Cluster와 Namespace 범위를 유지했는지 확인합니다."],
     "이 화면의 우선순위는 조사를 돕는 지표이며 실제 장애 선언이나 변경 승인을 대신하지 않습니다."),
    ("운영 현황과 대응", "Fleet Command Center", "03-fleet-command.png",
     "여러 클러스터의 건강도와 공통 위험 신호를 비교하고 전체 운영 범위의 상태를 조망합니다.",
     ["전체 현황에서 Critical, 위험 신호와 동기화 지연을 비교합니다.", "클러스터를 선택해 세부 신호와 최근 작업을 확인합니다.", "Platform Manager도 등록된 Kubernetes credential의 RBAC를 우회하지 않습니다."],
     "집계 수치가 오래 유지되면 Data and Runtime의 Watch와 동기화 상태를 확인합니다."),
    ("운영 현황과 대응", "Incidents", "04-incidents.png",
     "반복 장애와 운영 이슈를 상태, 담당자, 영향 범위와 타임라인으로 관리합니다.",
     ["Cluster, Namespace, 상태와 심각도로 목록을 필터링합니다.", "상세에서 연결된 분석, 명령 실행, 상태 변경과 담당자를 확인합니다.", "종료 전 조치 결과와 재분석 근거가 타임라인에 연결됐는지 검토합니다."],
     "Event가 사라졌다는 이유만으로 종료하지 말고 workload 상태와 검증 결과를 함께 남깁니다."),
    ("클러스터 운영", "Clusters", "05-clusters.png",
     "운영 대상 Kubernetes 클러스터를 등록하고 연결, 동기화, 버전 호환성과 자격 증명 상태를 관리합니다.",
     ["등록 후 연결 확인과 전체 동기화를 각각 실행합니다. 연결 성공은 동기화 RBAC 충족을 뜻하지 않습니다.", "상세에서 Namespace, workload, Pod, Service, storage, network와 Event를 확인합니다.", "인증 정보 원문 보기는 마스킹 해제 권한과 감사 기록을 요구하며 일반 운영에서는 다시 복사하지 않습니다.", "명령 작업 공간에서 대상 Cluster와 Namespace가 고정됐는지 확인하고 조회 명령부터 실행합니다."],
     "클러스터 ServiceAccount 권한과 KlueOps 로그인 역할은 서로 다른 보안 경계입니다."),
    ("Application Delivery", "Deployed Applications", "06-applications.png",
     "KlueOps가 Helm으로 배포한 Application의 상태, Chart 정보, Runtime, 접근 경로와 변경 이력을 운영합니다.",
     ["상태 요약과 필터로 실행 중, 실패, 확인 필요 Application을 찾습니다.", "선택한 Application의 Chart, 제공사, Chart와 App 버전, Cluster와 Namespace를 확인합니다.", "Runtime에서 workload 준비 상태, Service IP와 port, NodePort, Ingress 또는 HTTPRoute 상태를 확인합니다.", "Upgrade, Rollback, Uninstall은 Preview와 정확한 확인 문구를 거친 뒤 비동기 Job으로 실행됩니다."],
     "Uninstall 성공 시 Application 메타데이터도 제거되며 실패했을 때만 원인 확인을 위해 남습니다."),
    ("Application Delivery", "Chart Library", "07-chart-library.png",
     "현재 Tenant가 검증하고 보관한 Helm Chart와 버전을 관리하고 배포 흐름을 시작합니다.",
     ["Chart 이름, 제공사, source와 버전을 확인한 뒤 Values Profile을 선택하거나 새로 만듭니다.", "직접 업로드한 tgz는 안전 검사와 metadata 확인을 거쳐 저장합니다.", "불필요한 Chart 제거는 새 배포에서 숨기는 작업이며 기존 Release와 Values 이력은 유지됩니다."],
     "동일 Chart 이름이라도 repository와 제공사, exact version이 다를 수 있습니다."),
    ("Application Delivery", "Discover", "08-discover.png",
     "Artifact Hub에서 Helm Chart를 검색하고 exact version을 현재 Tenant의 Chart Library로 가져옵니다.",
     ["검색어를 입력하고 repository, 제공사, Chart와 App 버전을 비교합니다.", "가져오기 확인에서 destination Tenant와 version을 다시 검토합니다.", "가져온 Chart는 Chart Library에서 Values 검증과 배포를 이어갑니다."],
     "검색 결과를 바로 Cluster에 설치하지 않고 Tenant Library와 Preview를 거칩니다."),
    ("Application Delivery", "Sources", "09-sources.png",
     "Tenant가 사용하는 Helm repository와 직접 관리 source를 등록하고 연결 상태를 관리합니다.",
     ["Source 종류, endpoint와 표시 이름을 입력하고 연결을 검증합니다.", "인증 정보는 암호화 저장되며 저장 후 원문을 다시 표시하지 않습니다.", "변경이 기존 Chart와 Release에 미치는 영향을 확인한 뒤 수정합니다."],
     "공용 Artifact Hub 검색과 Tenant 전용 repository 등록은 목적이 다릅니다."),
    ("AI 기반 운영", "AI Analysis", "10-ai-analysis.png",
     "선택한 범위의 Kubernetes 상태, Event, 로그와 구성을 수집해 원인, 위험, 조치와 검증 명령을 생성합니다.",
     ["분석 범위와 모드를 확인하고 비동기 분석을 시작합니다.", "Kubernetes 정보 수집의 Complete 또는 Partial 상태를 먼저 확인합니다.", "Root Cause, Log Analysis, Risk Forecast와 Runbook을 근거 ledger 및 원본 resource와 함께 검토합니다.", "Command Safety 등급을 확인한 뒤 검증 명령을 실행하고 결과를 분석에 연결합니다."],
     "AI 답변은 운영 조언입니다. 수집 범위, fallback, confidence와 실제 Kubernetes 근거를 확인합니다."),
    ("AI 기반 운영", "AI Chat", "11-ai-chat.png",
     "일반 Kubernetes 질문 또는 선택한 Cluster와 resource 문맥을 바탕으로 운영 상담을 진행합니다.",
     ["일반 상담과 resource 기반 상담 중 목적에 맞는 모드를 선택합니다.", "Cluster, Namespace와 resource가 표시되면 질문 전에 범위를 확인합니다.", "응답이 중단되거나 timeout이면 Provider 상태와 전송 정책을 확인합니다."],
     "Secret 원문, token, 비밀번호와 민감한 로그는 질문에 포함하지 않습니다."),
    ("AI 기반 운영", "Runbooks", "12-runbooks.png",
     "반복 장애를 진단하고 조치하는 검증 명령, 예상 결과, 안전한 변경과 복구 절차를 관리합니다.",
     ["증상, source와 category로 Runbook을 찾습니다.", "요구 권한, 대상 범위, 명령 안전도와 예상 결과를 확인합니다.", "변경 명령은 Preview 또는 dry run을 먼저 수행하고 사후 검증과 rollback을 함께 사용합니다."],
     "Cook Book은 점검 명령 모음이고 Runbook은 장애 처리 절차입니다."),
    ("AI 기반 운영", "AI 신뢰 센터", "13-ai-trust.png",
     "AI 분석의 근거 충족률, 품질 gate, 모델과 prompt 정보, 운영자 feedback을 검토합니다.",
     ["운영 검증 커버리지와 실패한 품질 항목을 확인합니다.", "모델, prompt와 공개 가능한 evidence 상태를 비교합니다.", "정확, 개선 필요, 부정확 또는 위험 제안 feedback을 남깁니다."],
     "점수는 모델의 자신감이 아니라 운영자가 검증 가능한 근거와 행동을 충분히 받았는지를 나타냅니다."),
    ("거버넌스와 감사", "Policies", "14-policies.png",
     "Kubernetes 구성과 운영 기준의 위반 항목을 Cluster와 Namespace별로 확인합니다.",
     ["결과와 심각도로 정책 위반을 필터링합니다.", "대상 resource, 탐지 근거와 권장 조치를 확인합니다.", "예외가 필요하면 범위와 만료 조건을 별도로 기록합니다."],
     "정책 위반은 구성 근거이며 실제 CPU 또는 메모리 사용률을 의미하지 않을 수 있습니다."),
    ("거버넌스와 감사", "Audit", "15-audit.png",
     "로그인 사용자, Tenant 범위, 요청과 변경 작업의 감사 이벤트를 추적합니다.",
     ["행위자, action, target과 request ID로 기록을 검색합니다.", "배포, 권한 변경, 명령 실행과 AI 요청의 상관관계를 request ID로 확인합니다.", "보관 기간과 삭제 미리보기는 Data and Runtime 정책과 함께 관리합니다."],
     "감사 로그에는 credential과 Secret 원문을 저장하지 않습니다."),
    ("거버넌스와 감사", "Operations Reliability", "16-operations-reliability.png",
     "OIDC, session cookie, 공개 URL, Provider와 데이터 보호 등 운영 안전 설정의 준비 상태를 확인합니다.",
     ["Blocked 또는 경고 상태를 선택해 원인과 필요한 설정을 확인합니다.", "로컬 HTTP 경고와 실제 운영 환경의 TLS 요구를 구분합니다.", "운영 환경에서는 HTTPS, Secure cookie, 조직 IdP와 Secret manager를 적용합니다."],
     "로컬 acceptance의 HTTP 설정을 실제 운영 환경의 안전 설정으로 간주하지 않습니다."),
    ("플랫폼 설정", "Data and Runtime", "17-data-runtime.png",
     "데이터 보관 기간, 비동기 Job timeout, Kubernetes Watch, 분석 품질 기준과 runtime 상태를 관리합니다.",
     ["변경 전에 현재 값과 영향 범위를 확인하고 저장 후 적용 상태를 검증합니다.", "Watch가 끊기면 재시작 또는 재동기화를 수행합니다.", "보관 정리는 미리보기 후 실행하며 실행 중 작업과 Kubernetes resource는 삭제하지 않습니다."],
     "전역 설정은 모든 Tenant에 영향을 줄 수 있으므로 Platform Manager만 변경합니다."),
    ("플랫폼 설정", "AI Providers", "18-ai-providers.png",
     "Local Ollama와 외부 LLM Provider profile, 9B 이하 로컬 모델과 용도별 routing을 관리합니다.",
     ["Provider endpoint와 credential을 등록한 뒤 연결 검증을 수행합니다.", "Ollama 설치 모델을 동기화하고 평가를 통과한 모델을 활성화합니다.", "AI Analysis, AI Chat과 Helm Values 제안의 profile과 model을 Tenant별로 지정합니다.", "외부 Provider 사용 전 데이터 전송 범위와 fallback 정책을 확인합니다."],
     "사용 중인 모델은 routing을 다른 모델로 전환한 뒤 제거합니다."),
    ("플랫폼 설정", "사용자 및 권한", "19-users-access.png",
     "현재 Tenant의 사용자 membership, 역할, Cluster와 Namespace scope, 기능 접근을 관리합니다.",
     ["OIDC 사용자를 초대하고 Tenant Admin, Cluster Admin, Operator 또는 Viewer 역할을 지정합니다.", "Cluster Admin과 Operator에는 필요한 scope만 부여합니다.", "접근 중지 Preview에서 소유 작업과 영향 범위를 확인합니다.", "Platform Manager는 플랫폼 계정 권한에서 전역 역할과 사용자 활성 상태를 관리합니다."],
     "화면 항목 숨김은 보안 경계가 아니며 Backend가 capability와 scope를 다시 검사합니다."),
    ("플랫폼 설정", "Tenant 및 그룹 관리", "20-tenancy.png",
     "회사 단위 Tenant와 업무 그룹을 만들고 OIDC Group mapping과 Tenant 기능 정책을 관리합니다.",
     ["Tenant를 만든 뒤 운영 목적에 맞는 업무 그룹을 생성합니다.", "OIDC Group은 외부 사용자 집합이며 제품 역할과 scope는 KlueOps mapping으로 결정합니다.", "Tenant 기능 변경 시 영향을 받는 기능과 API를 Preview합니다."],
     "Company와 OIDC Group을 동일 개념으로 강제하지 않으며 한 회사가 여러 Group을 역할별로 사용할 수 있습니다."),
    ("개인 설정", "사용자 설정", "21-preferences.png",
     "현재 사용자의 화면 언어와 개인 운영 경험 설정을 관리합니다.",
     ["한국어 또는 영어를 선택하고 화면과 새 AI 요청에 적용되는지 확인합니다.", "Kubernetes resource 이름, Event reason, 로그와 명령은 원문을 유지합니다."],
     "과거 AI 분석 결과는 생성 당시 언어가 유지될 수 있습니다."),
]


# 최초 진입 화면에서 이어지는 실제 하위 화면, 탭과 작업 팝업을 업무 흐름별로 설명한다.
SUBPAGES = {
    "Fleet Command Center": [
        ("운영 신뢰도 추세", "22-fleet-trend.png", "Incident와 상태 변경 이력을 기간과 범위별로 비교해 반복되는 운영 위험을 찾습니다.",
         ["Cluster, Namespace와 기간을 정한 뒤 필터를 적용합니다.", "발생 추세와 범위별 분포를 비교하고 상세 대상으로 이동합니다."],
         [("기간과 범위", "Cluster와 Namespace, 조회 기간을 제한합니다.", "CPU와 메모리 사용률이 아니라 Event 기반 지표입니다."), ("추세 그래프", "Incident와 상태 변화의 증감을 확인합니다.", "수집 공백은 Watch 상태와 함께 해석합니다.")]),
        ("검증 랩", "23-fleet-validation.png", "AI 분석 품질과 알려진 장애 계약을 실제 운영과 분리해 검증합니다.",
         ["Benchmark와 가상 장애 검증 결과를 먼저 확인합니다.", "실제 Kubernetes 검증은 격리 Namespace, RBAC 사전 점검과 자동 정리 조건을 확인한 뒤 실행합니다."],
         [("Release Gate", "분류, 근거, 명령 안전성 점수를 확인합니다.", "점수 미달 모델은 운영 routing으로 승격하지 않습니다."), ("가상 검증", "고정 장애 시나리오를 변경 없이 검사합니다.", "실제 Cluster를 변경하지 않습니다."), ("Live 검증", "사전 점검 후 격리 fixture를 생성합니다.", "TTL과 정리 결과를 반드시 확인합니다.")]),
    ],
    "Incidents": [
        ("수동 Incident 생성", "24-incident-create-dialog.png", "자동 탐지 밖의 운영 이슈를 동일한 Incident 수명주기로 관리합니다.",
         ["Cluster와 Namespace, 심각도, 제목, resource와 현상을 입력합니다.", "생성 후 근거 탭에 사실 자료를 추가하고 담당자와 목표 시간을 지정합니다."],
         [("대상", "Cluster, Namespace와 resource 범위를 지정합니다.", "다른 Tenant의 대상은 선택할 수 없습니다."), ("초기 설명", "관측한 사실과 영향만 기록합니다.", "추정 원인은 근거와 분리합니다.")]),
        ("Incident 상세", "30-incident-detail.png", "하나의 장애에서 영향, 근거, 타임라인, 검증, 협업과 재발 방지를 끝까지 관리합니다.",
         ["개요에서 심각도, 반복 횟수, 다음 행동과 복구 조건을 확인합니다.", "상태를 바꿀 때 확인 결과 또는 조치 메모를 남깁니다.", "조치 후 검증·조치 탭에서 관찰을 시작하고 재발 방지 탭에서 회고 초안을 만듭니다."],
         [("영향·변경", "연결 resource와 변경 원인 후보를 비교합니다.", "추정 연결은 사실 연결과 구분합니다."), ("근거", "FACT와 INFERENCE를 분리해 확인합니다.", "전문가 모드에서 근거 분리 작업이 가능합니다."), ("타임라인", "상태, 댓글과 조치 이력을 시간 순으로 봅니다.", "감사 기록과 함께 사용합니다."), ("검증·조치", "Runbook, 관찰과 검증 명령을 실행합니다.", "변경 전에 명령 안전도와 RBAC를 확인합니다."), ("협업", "담당자, 태그, 목표 시간과 메모를 관리합니다.", "병합은 원본 Incident를 삭제하지 않습니다."), ("재발 방지", "관련 정책, 재분석과 회고를 연결합니다.", "정상화 근거 없이 종료하지 않습니다.")]),
    ],
    "Clusters": [
        ("클러스터 등록", "29-cluster-registration-dialog.png", "Kubeconfig 또는 ServiceAccount token으로 운영 대상 Cluster를 등록합니다.",
         ["이름, 환경, Provider와 설명을 입력합니다.", "인증 방식을 선택하고 credential, API server override와 TLS 옵션을 검토합니다.", "등록 후 연결 확인과 리소스 동기화를 각각 실행합니다."],
         [("Kubeconfig", "현재 context와 인증 정보를 등록합니다.", "파일 전체에 불필요한 context와 사용자를 포함하지 않습니다."), ("ServiceAccount token", "API server, CA와 bearer token을 등록합니다.", "필요한 get list watch와 작업별 create update 권한을 별도로 부여합니다."), ("TLS 검증", "운영 환경에서는 인증서 검증을 유지합니다.", "검증 해제는 로컬 임시 환경에만 제한합니다.")]),
        ("클러스터 상세", "25-cluster-detail.png", "Cluster의 준비도, Namespace, 리소스, Event, Node와 최근 작업을 한 범위에서 확인합니다.",
         ["동기화 시각과 문제 수를 먼저 확인합니다.", "Namespace를 선택하면 요약, 리소스와 Event 범위가 함께 바뀝니다.", "연결 확인과 리소스 동기화는 서로 다른 검증임을 기억합니다."],
         [("운영 상태 요약", "문제 resource와 최근 분석을 확인합니다.", "과거 Event와 현재 상태를 함께 봅니다."), ("운영 준비도", "권한, credential과 버전 호환성을 평가합니다.", "Platform 역할도 Kubernetes RBAC를 우회하지 않습니다."), ("등록 인증 정보", "권한이 있을 때 원문 보기 절차를 시작합니다.", "확인 사유가 Audit에 기록되며 화면을 닫으면 다시 마스킹합니다.")]),
        ("클러스터 준비도", "53-cluster-readiness-detail.png", "등록 credential의 실제 작업 가능 범위와 업그레이드 차단 요인을 확인합니다.",
         ["준비도 점검을 실행하고 capability, credential, upgrade 항목을 차례로 확인합니다.", "Denied verb와 대상 scope를 기준으로 최소 권한을 보완합니다."],
         [("Capability", "조회, 동기화, 배포와 콘솔에 필요한 권한을 분리해 봅니다.", "cluster admin 부여 전 최소 권한을 우선 검토합니다."), ("Credential", "만료, reveal 가능 여부와 연결 상태를 확인합니다.", "Secret 원문을 문서나 채팅에 복사하지 않습니다."), ("Upgrade", "Kubernetes minor skew와 차단 요인을 확인합니다.", "지원 범위 밖 버전은 등록 성공과 별개입니다.")]),
        ("리소스 상세", "27-cluster-resource-detail.png", "선택한 Kubernetes resource의 상태, 관계, 로그와 YAML을 확인합니다.",
         ["개요에서 status와 핵심 field, relation map과 변경 이력을 확인합니다.", "Pod 기반 대상은 로그 탭에서 container와 최근 또는 stream 모드를 선택합니다.", "YAML 탭은 Secret value를 마스킹한 읽기 전용 manifest로 사용합니다."],
         [("개요", "상태, 관련 resource, 변경과 Incident를 확인합니다.", "수집 시각을 확인합니다."), ("로그", "Pod와 container를 선택해 최근 로그 또는 stream을 봅니다.", "민감정보가 포함될 수 있어 공유 범위를 제한합니다."), ("YAML", "현재 manifest와 핵심 metadata를 봅니다.", "편집 화면이 아니며 변경은 검증된 절차를 사용합니다.")]),
        ("Kubernetes 콘솔", "28-kubernetes-console.png", "등록 Cluster 경계 안에서 kubectl 명령을 검증하고 실행 결과를 보존합니다.",
         ["Cluster, Namespace와 resource target을 고정합니다.", "Cook Book 또는 명령 template을 적용하고 안전도 검증 결과를 확인합니다.", "출력과 종료 코드를 검토하고 필요하면 같은 범위를 재분석합니다."],
         [("명령 작업 공간", "get, describe, logs와 YAML template을 구성합니다.", "조회 명령부터 시작합니다."), ("변경 확인", "대상, namespace와 normalized command를 재확인합니다.", "위험 작업은 별도 확인과 dry run을 요구합니다."), ("출력", "실시간 출력, 검색, 줄바꿈, 복사와 다운로드를 제공합니다.", "출력의 token과 Secret을 외부로 전달하지 않습니다."), ("도구 모음", "Cook Book, 즐겨찾기와 실행 이력을 사용합니다.", "즐겨찾기에도 credential을 저장하지 않습니다.")]),
    ],
    "Deployed Applications": [
        ("배포 시작 경로", "31-application-start-dialog.png", "보유 Chart, Artifact Hub 검색 또는 tgz 업로드 중 시작 방법을 선택합니다.",
         ["검증된 Chart가 있으면 Chart Library를 사용합니다.", "Chart가 없으면 Discover에서 exact version을 가져오거나 보유 tgz를 검사합니다."],
         [("Chart Library", "Tenant가 이미 검증한 Chart로 시작합니다.", "권장 시작 경로입니다."), ("Chart 검색", "Artifact Hub 결과를 Tenant Library로 가져옵니다.", "가져오기 자체는 배포가 아닙니다."), ("tgz 업로드", "보유 package를 안전 검사 후 저장합니다.", "20 MiB 제한과 압축 안전 검사를 적용합니다.")]),
        ("Values Studio", "33-values-studio.png", "Chart 기본 values를 유지하면서 Tenant별 override Profile과 revision을 관리합니다.",
         ["기존 Profile을 선택하거나 이름과 설명을 입력해 새 Profile을 만듭니다.", "Form 또는 YAML 방식으로 override만 수정합니다.", "Helm render 검증을 통과한 revision을 저장하고 Target 단계로 이동합니다."],
         [("Form", "values schema가 있는 field를 입력합니다.", "표시되지 않는 고급 field는 YAML을 사용합니다."), ("YAML", "Chart 구조에 맞는 override YAML을 편집합니다.", "list와 map 자료형을 혼동하지 않습니다."), ("Revision", "저장한 변경을 불변 이력으로 관리합니다.", "배포는 선택한 revision을 정확히 참조합니다.")]),
        ("Custom Values AI 도우미", "34-values-ai-assistant.png", "정확한 Chart 제공사와 버전의 기본 values와 schema를 근거로 override 초안을 만듭니다.",
         ["원하는 replica, service, persistence, resource와 보안 조건을 자연어로 입력합니다.", "제안 YAML을 diff와 Helm render로 검증한 뒤 적용합니다.", "빈 YAML 입력도 빈 object로 처리되며 사용자의 요구만으로 초안을 만들 수 있습니다."],
         [("입력 문맥", "Chart, repository, Chart/App 버전과 기본 values 골격을 전달합니다.", "특정 제품에 하드코딩된 prompt를 사용하지 않습니다."), ("Secret", "existing Secret 이름 또는 placeholder만 요청합니다.", "비밀번호 원문을 LLM에 보내지 않습니다."), ("검증", "YAML parse, schema와 helm template을 확인합니다.", "검증 실패 제안은 revision으로 저장하지 않습니다.")]),
        ("배포 Wizard", "35-deployment-wizard.png", "Cluster, Namespace, release와 접근 방식을 선택하고 manifest Preview 후 배포합니다.",
         ["Target Cluster와 Namespace, release name을 지정합니다.", "Cluster 내부, Chart 관리형 또는 KlueOps HTTPRoute 중 접근 방식을 선택합니다.", "Preview에서 manifest, 권한, Service port, Gateway와 경고를 확인한 뒤 확인 문구로 배포합니다."],
         [("Target", "배포 Cluster, Namespace와 release를 결정합니다.", "Helm release Secret 권한을 사전 검사합니다."), ("Cluster 내부", "Chart Service만 생성합니다.", "외부 접근 경로는 제공하지 않습니다."), ("Chart 관리형", "Values가 생성하는 Ingress 또는 Route를 사용합니다.", "Chart가 지원하는 정확한 field를 사용합니다."), ("KlueOps HTTPRoute", "렌더링된 Service와 Ready Gateway를 선택합니다.", "backend port는 Service spec ports port이며 targetPort와 nodePort가 아닙니다."), ("Preview", "Helm render 결과와 생성·변경 resource를 확인합니다.", "Preview는 Cluster를 변경하지 않습니다.")]),
    ],
    "Chart Library": [
        ("tgz Chart 가져오기", "32-chart-upload-dialog.png", "로컬 Helm package를 안전 검사 후 Tenant Library에 저장합니다.",
         ["20 MiB 이하 tgz를 선택합니다.", "digest 중복, metadata, 경로 traversal, link와 압축 해제 크기 검사를 통과한 결과만 가져옵니다."],
         [("파일 검사", "Chart.yaml과 package 구조를 검사합니다.", "실패 파일은 Library에 저장하지 않습니다."), ("중복 판정", "digest와 Chart version을 비교합니다.", "같은 이름이라도 source와 version을 확인합니다.")]),
        ("Chart 제거 확인", "49-chart-removal-dialog.png", "더 이상 새 배포에 사용하지 않을 Chart를 Tenant Library에서 숨깁니다.",
         ["Package, source와 보존 대상을 확인합니다.", "표시된 확인 문자열을 정확히 입력한 뒤 제거합니다."],
         [("제거 범위", "Chart와 version을 Library 목록에서 숨깁니다.", "기존 Application과 Release를 삭제하지 않습니다."), ("보존 범위", "배포 이력과 Values revision을 유지합니다.", "완전 삭제 작업과 구분합니다.")]),
    ],
    "Discover": [
        ("Artifact Hub 검색 결과", "50-discover-results.png", "제품명으로 Chart 후보를 비교하고 exact version을 Tenant Library로 가져옵니다.",
         ["repository 표시 이름, description, version과 Verified 또는 Official 표시를 비교합니다.", "선택한 결과를 가져온 뒤 Chart Library에서 제공사와 source를 다시 확인합니다."],
         [("검색", "제품명 또는 기능 키워드로 최대 결과를 조회합니다.", "기본 검색어를 자동 입력하지 않습니다."), ("가져오기", "선택 버전을 현재 Tenant Library에 저장합니다.", "Cluster에는 아직 아무 것도 배포하지 않습니다.")]),
    ],
    "Sources": [
        ("Repository 추가", "48-source-add-dialog.png", "Tenant 전용 Helm Repository 또는 OCI Registry 연결을 등록합니다.",
         ["유형, 표시 이름과 외부 HTTPS endpoint를 입력합니다.", "필요할 때만 credential을 입력하고 검증 후 저장합니다."],
         [("Helm Repository", "index.yaml 기반 repository를 등록합니다.", "공개 HTTPS endpoint를 사용합니다."), ("OCI Registry", "OCI Chart source를 등록합니다.", "지원하는 인증 방식과 경로를 확인합니다."), ("Credential", "암호화해 저장하고 이후 재표시하지 않습니다.", "변경 시 새 credential을 다시 입력합니다.")]),
    ],
    "AI Analysis": [
        ("분석 결과 초보자 보기", "51-analysis-detail.png", "문제 의미, 품질 점수와 다음 확인 순서를 설명 중심으로 검토합니다.",
         ["분석 범위, 생성 언어, model, latency와 context를 확인합니다.", "Quality Gate의 Evidence, Log, Actionability와 Coverage를 확인합니다.", "관련 Application과 검증 행동으로 이동합니다."],
         [("요약", "위험도와 핵심 결론을 먼저 읽습니다.", "AI 결론을 사실로 확정하지 않습니다."), ("Quality Gate", "근거 정렬, 로그 구체성, 행동성과 범위를 평가합니다.", "낮은 차원은 추가 수집 대상으로 사용합니다."), ("재시도", "현재 범위로 새 분석을 실행합니다.", "기존 분석 이력은 별도로 유지됩니다.")]),
        ("분석 결과 숙련자 보기", "52-analysis-expert-detail.png", "근거, issue group, 명령 안전도와 원본 데이터를 밀도 높게 검토합니다.",
         ["Kubernetes 정보 수집 상태와 Evidence ledger를 먼저 확인합니다.", "Command Safety에서 READ ONLY, 변경과 위험 명령을 구분합니다.", "검증 명령 결과를 분석에 연결하고 같은 scope를 재분석합니다."],
         [("Issue Group", "원인, 영향 resource와 근거를 그룹별로 봅니다.", "근거가 부족한 결론은 NEEDS EVIDENCE로 남깁니다."), ("Command Safety", "명령, 대상, 이유와 안전 등급을 확인합니다.", "변경 명령은 RBAC, dry run과 rollback guard를 거칩니다."), ("Raw result", "구조화된 원본 분석을 확인합니다.", "일반 운영에서는 화면 요약을 우선 사용합니다.")]),
    ],
    "Runbooks": [
        ("Runbook 상세 절차", "37-runbook-expanded.png", "진단, 안전한 조치와 사후 검증을 순서대로 실행합니다.",
         ["원인 확인 명령과 예상 결과를 먼저 검토합니다.", "안전한 조치가 있으면 권한과 scope를 확인합니다.", "검증 명령과 rollback 안내로 종료 조건을 확인합니다."],
         [("System Runbook", "제품이 제공하는 표준 절차입니다.", "직접 수정 대신 사용자 Runbook으로 복제합니다."), ("Custom Runbook", "Tenant 운영자가 편집하고 version을 관리합니다.", "비활성화와 삭제 전 사용 중인 절차를 확인합니다.")]),
        ("Runbook 작성과 편집", "38-runbook-editor.png", "반복 가능한 운영 절차를 versioned Runbook으로 저장합니다.",
         ["제목, trigger, category, resource kind와 안전도를 정의합니다.", "초보자 설명, 검증 명령, 기대 결과, safe action, 사후 검증과 rollback을 분리해 작성합니다."],
         [("검증 명령", "원인을 확인하는 읽기 명령을 작성합니다.", "namespace placeholder를 명시합니다."), ("Safe action", "선택적 변경 행동을 기록합니다.", "무조건 실행되는 명령으로 작성하지 않습니다."), ("Version", "편집할 때 새 version을 생성합니다.", "과거 version 복원도 새 version으로 기록됩니다.")]),
    ],
    "AI 신뢰 센터": [
        ("모델과 Release 근거", "41-ai-trust-evidence.png", "AI 품질 승격에 사용된 benchmark, regression과 운영 근거를 검토합니다.",
         ["운영자 검증 비율과 품질 gate 상태를 확인합니다.", "model과 prompt 조합별 결과와 release evidence를 비교합니다."],
         [("Model Prompt", "운영 분석에 사용한 조합의 교정 상태를 봅니다.", "Provider 연결 성공과 품질 승격은 별개입니다."), ("Release Evidence", "Benchmark, regression과 운영 검증을 확인합니다.", "필수 근거가 없으면 NEEDS EVIDENCE입니다."), ("Contract 평가", "고정 corpus의 항목별 성능을 비교합니다.", "실제 Tenant 데이터와 혼합하지 않습니다.")]),
    ],
    "Policies": [
        ("정책 설정", "39-policy-settings.png", "평가할 Kubernetes 구성 규칙을 활성화하거나 중지합니다.",
         ["정책 목적, 심각도와 활성 상태를 확인합니다.", "변경 후 대상 Cluster 평가를 다시 실행합니다."],
         [("활성 정책", "다음 평가부터 규칙을 적용합니다.", "기존 평가 결과는 변경 이력으로 남습니다."), ("비활성 정책", "새 평가에서 규칙을 제외합니다.", "예외 사유와 기간을 별도로 기록합니다.")]),
        ("정책 변경 이력", "40-policy-history.png", "누가 언제 어떤 정책 구성을 변경했는지 추적합니다.",
         ["변경 시각, actor와 변경 전후 값을 확인합니다.", "평가 결과 변화와 Audit request ID를 함께 비교합니다."],
         [("변경 기록", "정책 enable, disable과 설정 변경을 봅니다.", "데이터가 없으면 아직 변경 이력이 없는 상태입니다."), ("상관 확인", "Audit와 평가 결과의 시각을 비교합니다.", "이력 자체를 정책 통과 근거로 사용하지 않습니다.")]),
    ],
    "AI Providers": [
        ("Provider Profile 추가와 편집", "42-ai-provider-dialog.png", "Local Ollama 또는 외부 Provider endpoint, model 허용 목록과 credential을 관리합니다.",
         ["Provider 종류, 이름, Base URL, 기본 model과 허용 model을 입력합니다.", "외부 API key는 저장 후 재표시하지 않으며 저장 직후 연결 검증을 수행합니다."],
         [("Local Ollama", "로컬 endpoint와 9B 이하 승인 model을 사용합니다.", "설치 목록 동기화 후 평가와 승격을 수행합니다."), ("외부 Provider", "OpenAI 호환 또는 지원 Provider를 설정합니다.", "데이터 외부 전송과 fallback 정책을 확인합니다."), ("Tenant routing", "AI Analysis, Chat과 Values 목적별 primary와 fallback을 지정합니다.", "사용 중인 model은 routing 전환 전 삭제할 수 없습니다.")]),
    ],
    "사용자 및 권한": [
        ("OIDC Group Mapping 목록", "43-access-group-mapping.png", "OIDC Group claim과 Tenant 역할, resource scope의 자동 권한 합산 규칙을 관리합니다.",
         ["issuer, group path, 역할과 scope를 확인합니다.", "Mapping 활성화 또는 삭제가 다음 로그인과 유효 권한에 미치는 영향을 검토합니다."],
         [("Grant union", "직접 Membership과 Group Mapping 권한을 합산합니다.", "한 경로의 권한 제거가 다른 grant를 제거하지 않습니다."), ("비활성화", "Mapping의 새 권한 합산을 중지합니다.", "직접 Membership은 유지됩니다.")]),
        ("메뉴와 기능 정책", "44-access-feature-policy.png", "Tenant에서 사용할 선택 기능을 제어하고 메뉴와 API capability를 함께 제한합니다.",
         ["필수 기능과 선택 기능을 구분합니다.", "변경 전 영향 받는 사용자와 업무 흐름을 확인합니다."],
         [("필수 기능", "제품 기본 운영에 필요해 중지할 수 없습니다.", "Backend capability가 최종 보안 경계입니다."), ("선택 기능", "Tenant Admin 또는 Platform Manager가 켜고 끕니다.", "화면 숨김만으로 권한을 구현하지 않습니다.")]),
        ("사용자 초대", "45-user-invite-dialog.png", "OIDC identity가 처음 로그인할 때 연결할 직접 Tenant Membership을 미리 등록합니다.",
         ["issuer와 email을 입력하고 역할과 scope를 선택합니다.", "Workspace, Cluster 또는 Namespace 범위라면 대상 값을 추가로 지정합니다."],
         [("초대 대기", "제품 계정을 새로 만드는 대신 OIDC identity 연결을 기다립니다.", "issuer와 email이 실제 claim과 정확히 일치해야 합니다."), ("역할", "Tenant Admin, Cluster Admin, Operator 또는 Viewer를 선택합니다.", "최소 권한과 최소 scope를 사용합니다.")]),
        ("Group Mapping 추가", "54-group-mapping-dialog.png", "Keycloak Group 구성원에게 Tenant 역할과 scope를 자동 합산합니다.",
         ["OIDC issuer와 정확한 Group path를 입력합니다.", "역할과 Tenant, Workspace, Cluster 또는 Namespace 범위를 선택해 저장합니다."],
         [("Group path", "OIDC token의 group claim 값과 일치시킵니다.", "Company 이름과 동일한 개념으로 강제하지 않습니다."), ("Scope", "업무 책임에 필요한 범위만 지정합니다.", "Cluster Admin과 Operator의 범위를 구분합니다.")]),
        ("플랫폼 계정과 권한", "47-platform-accounts.png", "Platform Manager가 전체 OIDC 사용자 상태와 전역 역할을 관리합니다.",
         ["사용자 identity, 활성 상태와 전역 역할을 확인합니다.", "계정 비활성화 전 Tenant Membership과 진행 중 작업 영향을 검토합니다."],
         [("Platform Manager", "모든 Tenant의 제품 기능을 관리합니다.", "등록 Cluster credential 권한은 별도입니다."), ("계정 상태", "로그인 가능 여부를 활성 또는 중지합니다.", "Keycloak 계정 삭제와 제품 상태 변경을 구분합니다.")]),
    ],
}


def shade(cell, color):
    props = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), color)
    props.append(shd)


def borders(cell):
    props = cell._tc.get_or_add_tcPr()
    item = OxmlElement("w:tcBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        line = OxmlElement(f"w:{edge}")
        line.set(qn("w:val"), "single")
        line.set(qn("w:sz"), "4")
        line.set(qn("w:color"), GRAY)
        item.append(line)
    props.append(item)


def cell_margins(cell, top=100, start=120, bottom=100, end=120):
    """표의 텍스트가 테두리에 붙지 않도록 셀 안쪽 여백을 통일한다."""
    props = cell._tc.get_or_add_tcPr()
    margins = props.first_child_found_in("w:tcMar")
    if margins is None:
        margins = OxmlElement("w:tcMar")
        props.append(margins)
    for name, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = margins.find(qn(f"w:{name}"))
        if node is None:
            node = OxmlElement(f"w:{name}")
            margins.append(node)
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")


def format_table_paragraph(paragraph, header=False, first_column=False):
    """표 안의 글꼴, 행간과 정렬을 읽기 좋은 값으로 맞춘다."""
    paragraph.paragraph_format.space_before = Pt(0)
    paragraph.paragraph_format.space_after = Pt(0)
    paragraph.paragraph_format.line_spacing = 1.08
    if header:
        paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for run in paragraph.runs:
        run.font.size = Pt(9.2)
        run.font.bold = header or first_column
        if header:
            run.font.color.rgb = RGBColor(255, 255, 255)


def add_table(document, headers, rows, widths):
    table = document.add_table(rows=1, cols=len(headers))
    table.autofit = False
    table.style = "Table Grid"
    header_props = table.rows[0]._tr.get_or_add_trPr()
    repeat = OxmlElement("w:tblHeader")
    repeat.set(qn("w:val"), "true")
    header_props.append(repeat)
    for i, header in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = header
        cell.width = Inches(widths[i])
        cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        shade(cell, DARK_BLUE)
        borders(cell)
        cell_margins(cell)
        format_table_paragraph(cell.paragraphs[0], header=True)
    for row_index, values in enumerate(rows):
        row = table.add_row()
        row_props = row._tr.get_or_add_trPr()
        row_props.append(OxmlElement("w:cantSplit"))
        cells = row.cells
        for i, value in enumerate(values):
            cells[i].text = value
            cells[i].width = Inches(widths[i])
            cells[i].vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            borders(cells[i])
            cell_margins(cells[i])
            if row_index % 2:
                shade(cells[i], "F3F7FC")
            format_table_paragraph(cells[i].paragraphs[0], first_column=i == 0)


def configure(document):
    section = document.sections[0]
    section.page_width, section.page_height = Inches(8.5), Inches(11)
    section.top_margin = section.bottom_margin = Inches(0.7)
    section.left_margin = section.right_margin = Inches(0.75)
    for style in document.styles:
        if not hasattr(style, "font"):
            continue
        style.font.name = FONT
        style.font.color.rgb = RGBColor(0, 0, 0)
        fonts = style._element.get_or_add_rPr().get_or_add_rFonts()
        for key in ("ascii", "hAnsi", "eastAsia", "cs"):
            fonts.set(qn(f"w:{key}"), FONT)
    normal = document.styles["Normal"]
    normal.font.size = Pt(10.5)
    normal.paragraph_format.space_after = Pt(7)
    normal.paragraph_format.line_spacing = 1.18
    document.styles["Title"].font.size = Pt(28)
    document.styles["Title"].font.bold = True
    title_properties = document.styles["Title"]._element.get_or_add_pPr()
    for border in list(title_properties.findall(qn("w:pBdr"))):
        title_properties.remove(border)
    for name, size in (("Heading 1", 20), ("Heading 2", 15), ("Heading 3", 12)):
        style = document.styles[name]
        style.font.size = Pt(size)
        style.font.bold = True
        style.paragraph_format.space_before = Pt(14)
        style.paragraph_format.space_after = Pt(7)
        style.paragraph_format.keep_with_next = True


def apply_supported_font(document):
    """LibreOffice에서도 한글 글꼴이 유지되도록 모든 run에 글꼴을 명시한다."""
    def update_run(run):
        run.font.name = FONT
        fonts = run._element.get_or_add_rPr().get_or_add_rFonts()
        for key in ("ascii", "hAnsi", "eastAsia", "cs"):
            fonts.set(qn(f"w:{key}"), FONT)

    for paragraph in document.paragraphs:
        for run in paragraph.runs:
            update_run(run)
    for table in document.tables:
        for row in table.rows:
            for cell in row.cells:
                for paragraph in cell.paragraphs:
                    for run in paragraph.runs:
                        update_run(run)


def add_page_number(paragraph):
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = paragraph.add_run()
    begin = OxmlElement("w:fldChar"); begin.set(qn("w:fldCharType"), "begin")
    instruction = OxmlElement("w:instrText"); instruction.set(qn("xml:space"), "preserve"); instruction.text = " PAGE "
    end = OxmlElement("w:fldChar"); end.set(qn("w:fldCharType"), "end")
    run._r.extend((begin, instruction, end))


def bullet(document, text):
    document.add_paragraph(text, style="List Bullet")


def numbered(document, text):
    document.add_paragraph(text, style="List Number")


def add_screen(document, screen, number, figure_number):
    chapter, title, filename, purpose, actions, note = screen
    label = document.add_paragraph()
    label.paragraph_format.page_break_before = True
    run = label.add_run(chapter.upper())
    run.bold = True; run.font.size = Pt(8.5); run.font.color.rgb = RGBColor(31, 102, 209)
    document.add_heading(f"{number}  {title}", level=1)
    document.add_paragraph(purpose)
    image_paragraph = document.add_paragraph()
    image_paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    picture = image_paragraph.add_run().add_picture(str(SCREENSHOTS / filename), width=Inches(6.8))
    picture._inline.docPr.set("descr", f"KlueOps {title} 운영 화면")
    caption = document.add_paragraph(f"그림 {figure_number}  {title} 운영 화면")
    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.runs[0].italic = True; caption.runs[0].font.size = Pt(9)
    document.add_heading("주요 작업", level=2)
    for action in actions:
        bullet(document, action)
    paragraph = document.add_paragraph()
    paragraph.add_run("운영 시 주의  ").bold = True
    paragraph.add_run(note)


def add_subpage(document, parent_title, detail, figure_number):
    """메뉴에서 이어지는 상세 화면과 작업 흐름을 독립된 가이드 절로 추가한다."""
    title, filename, purpose, actions, features = detail
    label = document.add_paragraph()
    label.paragraph_format.page_break_before = True
    label_run = label.add_run(f"{parent_title.upper()}  DETAIL FLOW")
    label_run.bold = True
    label_run.font.size = Pt(8.5)
    label_run.font.color.rgb = RGBColor(31, 102, 209)
    document.add_heading(title, level=2)
    document.add_paragraph(purpose)
    image_paragraph = document.add_paragraph()
    image_paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    # 상세 화면은 절차와 확인 표가 한 페이지 안에서 자연스럽게 이어지도록 크기를 제한한다.
    picture = image_paragraph.add_run().add_picture(str(SCREENSHOTS / filename), width=Inches(4.7))
    picture._inline.docPr.set("descr", f"KlueOps {parent_title}의 {title} 화면")
    caption = document.add_paragraph(f"그림 {figure_number}  {title}")
    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.runs[0].italic = True
    caption.runs[0].font.size = Pt(9)
    document.add_heading("작업 순서", level=3)
    for index, action in enumerate(actions, start=1):
        paragraph = document.add_paragraph()
        paragraph.paragraph_format.left_indent = Inches(0.24)
        paragraph.paragraph_format.first_line_indent = Inches(-0.24)
        paragraph.add_run(f"{index}. {action}")
    document.add_heading("기능과 안전 기준", level=3)
    add_table(document, ["기능", "사용 방법", "확인 사항"], features, [1.35, 2.85, 2.8])


def build():
    required_images = [item[2] for item in SCREENS]
    required_images.extend(detail[1] for details in SUBPAGES.values() for detail in details)
    missing = [filename for filename in required_images if not (SCREENSHOTS / filename).is_file()]
    if missing:
        raise FileNotFoundError(f"Missing guide screenshots: {', '.join(missing)}")
    document = Document()
    configure(document)
    add_page_number(document.sections[0].footer.paragraphs[0])

    title = document.add_paragraph(style="Title")
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    title.add_run("KlueOps 운영 가이드")
    subtitle = document.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.add_run("클러스터 운영과 Application Delivery 사용자 매뉴얼").bold = True
    metadata = document.add_paragraph()
    metadata.alignment = WD_ALIGN_PARAGRAPH.CENTER
    metadata.add_run("대상  Platform Manager Tenant Admin Cluster Admin Operator Viewer\n")
    metadata.add_run("기준  2026년 9월 17일 main 브랜치와 로컬 Kubernetes 검증 화면 54종")
    cover = document.add_paragraph(); cover.alignment = WD_ALIGN_PARAGRAPH.CENTER
    cover_picture = cover.add_run().add_picture(str(SCREENSHOTS / "01-dashboard.png"), width=Inches(6.8))
    cover_picture._inline.docPr.set("descr", "KlueOps Dashboard 운영 화면")
    document.add_paragraph("이 문서는 실제 KlueOps에 로그인해 확인한 최초 화면, 상세 화면, 탭, 팝업과 하위 작업 흐름을 함께 설명합니다. 화면 위치를 외우기보다 수행하려는 업무와 현재 Tenant, 업무 그룹, Cluster, Namespace 범위를 기준으로 사용합니다.")

    document.add_page_break()
    document.add_heading("이 가이드의 사용법", level=1)
    document.add_paragraph("공통 운영 범위와 역할을 이해한 뒤 필요한 업무 장을 찾습니다. 각 메뉴의 최초 화면 다음에는 독립 상세 페이지, 탭, 팝업과 완료 조건을 실제 화면 순서대로 설명합니다. 표시되는 기능은 역할, Tenant 기능 정책과 resource scope에 따라 달라질 수 있습니다.")
    add_table(document, ["업무 영역", "사용 목적", "대표 화면"], [
        ["운영 현황과 대응", "현재 위험과 처리 우선순위 결정", "Dashboard 실시간 운영 판단 Fleet Command Incidents"],
        ["클러스터 운영", "연결 동기화 resource 조회와 명령 검증", "Clusters 클러스터 상세 명령 작업 공간"],
        ["Application Delivery", "Chart 탐색 Values 검증 배포와 수명주기", "Applications Chart Library Discover Sources"],
        ["AI 기반 운영", "근거 기반 분석 상담과 Runbook", "AI Analysis AI Chat Runbooks AI 신뢰 센터"],
        ["거버넌스와 감사", "정책 감사 추적과 운영 준비 상태", "Policies Audit Operations Reliability"],
        ["플랫폼 설정", "Runtime Provider 사용자 Tenant 관리", "Data and Runtime AI Providers 사용자 및 권한 Tenant 관리"],
    ], [1.45, 2.5, 3.25])

    document.add_heading("공통 운영 범위", level=1)
    document.add_paragraph("모든 작업은 현재 선택한 Tenant와 업무 그룹을 기준으로 합니다. Cluster와 Namespace를 선택하는 화면에서는 작업 대상이 이 범위에 속하는지 다시 확인합니다. 운영 통합 검색은 resource와 기능을 찾고 운영 알림은 비동기 Job 완료와 실패를 알려줍니다.")
    for item in ["Tenant를 바꾸면 목록, 권한과 기능 정책이 함께 다시 계산됩니다.", "deep link를 사용해도 Backend가 capability와 resource scope를 다시 검사합니다.", "세션 만료 안내가 나타나면 남은 시간을 확인하고 연장하거나 다시 로그인합니다.", "운영 안전 경고는 TLS, OIDC, cookie와 Provider 구성을 나타냅니다."]:
        bullet(document, item)

    document.add_heading("역할과 권한", level=1)
    add_table(document, ["역할", "주요 책임", "중요한 제한"], [
        ["Platform Manager", "모든 Tenant 전역 설정 Provider 계정과 권한 관리", "Kubernetes credential RBAC는 우회하지 않음"],
        ["Tenant Admin", "구성원 기능 정책 Chart와 AI routing 관리", "다른 Tenant resource 접근 불가"],
        ["Cluster Admin", "허용 Cluster 설정과 Application 수명주기 관리", "허용되지 않은 scope 접근 불가"],
        ["Operator", "허용 범위 분석 배포 점검과 안전한 운영", "관리 capability를 벗어난 작업 불가"],
        ["Viewer", "운영 현황과 허용 resource 조회", "배포 변경 명령과 관리 작업 불가"],
    ], [1.25, 3.2, 2.75])

    document.add_heading("권장 시작 흐름", level=1)
    for item in ["로그인 후 Tenant와 업무 그룹을 확인합니다.", "Dashboard에서 위험 신호와 진행 중 작업을 확인합니다.", "대상 Cluster와 Namespace의 동기화 시각과 현재 상태를 확인합니다.", "AI Analysis 또는 Runbook으로 원인과 검증 순서를 확인합니다.", "조회 명령부터 실행하고 변경은 Preview 또는 dry run과 확인 절차를 거칩니다.", "조치 후 같은 범위를 재분석하고 Incident와 Audit에 결과가 연결됐는지 확인합니다."]:
        numbered(document, item)

    document.add_page_break()
    document.add_heading("Helm Application 배포 절차", level=1)
    document.add_paragraph("KlueOps Application Delivery는 Git repository를 지속 동기화하는 GitOps Controller가 아닙니다. Tenant가 보유하거나 Artifact Hub에서 가져온 Helm Chart를 Custom Values로 검증한 뒤 명시적으로 install, upgrade, rollback과 uninstall합니다.")
    for index, item in enumerate([
        "Chart Library에서 검증된 Chart를 선택합니다. 없으면 Discover 또는 Sources와 tgz 가져오기를 사용합니다.",
        "Values Profile을 만들고 YAML 또는 schema Form으로 override를 저장합니다. 빈 입력은 빈 object로 처리하며 exact Chart로 Helm render를 통과해야 합니다.",
        "AI Values 제안은 Chart 제공사, Chart와 App 버전, values와 schema 골격을 사용합니다. Secret 원문 대신 existing Secret 이름을 요청합니다.",
        "Cluster와 Namespace를 선택합니다. Preview는 Helm release Secret의 get list create 권한을 확인합니다.",
        "HTTPRoute는 렌더링된 Service의 spec ports port와 Ready Gateway를 사용하며 targetPort나 nodePort를 backend port로 사용하지 않습니다.",
        "PostgreSQL과 Redis 같은 TCP 서비스는 port forward, NodePort, LoadBalancer 또는 TCPRoute를 사용합니다.",
        "Preview의 manifest, 경고와 접근 경로를 검토하고 정확한 확인 문구로 비동기 배포를 시작합니다.",
        "Applications에서 상태가 Running 또는 Failed로 수렴하는지 확인하고 Runtime, endpoint와 History를 검토합니다.",
    ], start=1):
        paragraph = document.add_paragraph()
        paragraph.paragraph_format.left_indent = Inches(0.24)
        paragraph.paragraph_format.first_line_indent = Inches(-0.24)
        paragraph.add_run(f"{index}. {item}")

    document.add_heading("명령 안전과 분석 검증", level=1)
    add_table(document, ["단계", "확인 내용"], [
        ["범위 고정", "Cluster Namespace resource와 원본 Analysis 연결 확인"],
        ["명령 분류", "Read Only Safe Change Destructive 등급과 confirmation 확인"],
        ["사전 검증", "Preview dry run 현재 상태와 예상 변경 확인"],
        ["실행", "종료 코드 실시간 출력 마스킹과 Audit request ID 확인"],
        ["사후 검증", "상태 전후 비교 재분석과 rollback 필요 여부 확인"],
    ], [1.55, 5.65])

    figure_number = 1
    for number, screen in enumerate(SCREENS, start=1):
        add_screen(document, screen, number, figure_number)
        figure_number += 1
        for detail in SUBPAGES.get(screen[1], []):
            add_subpage(document, screen[1], detail, figure_number)
            figure_number += 1

    document.add_page_break()
    document.add_heading("상태와 오류 해석", level=1)
    add_table(document, ["상태", "의미와 대응"], [
        ["Loading", "최신 데이터를 조회 중입니다. 장시간 유지되면 API와 session 상태를 확인합니다."],
        ["Empty", "현재 범위에 데이터가 없습니다. 화면의 시작 행동을 수행합니다."],
        ["Partial", "일부 Kubernetes 또는 AI source만 완료됐습니다. 실패 source와 confidence를 확인합니다."],
        ["Fallback", "AI 응답 대신 Kubernetes 근거와 결정론적 규칙으로 결과를 만들었습니다."],
        ["Blocked", "권한 안전 정책 또는 필수 환경조건 때문에 실행하지 않았습니다."],
        ["Degraded", "Route 또는 resource 참조가 수락되지 않았습니다. 유효한 접근 경로로 취급하지 않습니다."],
    ], [1.35, 5.85])

    document.add_heading("문제 해결 체크리스트", level=1)
    for item in ["화면 데이터가 오래됨  Tenant 업무 그룹과 동기화 시각을 확인하고 근거를 갱신합니다.", "연결은 되지만 동기화 실패  등록 ServiceAccount의 cluster scope list watch 권한을 확인합니다.", "Helm 배포 권한 오류  대상 Namespace의 Secret get list create와 Chart resource 권한을 확인합니다.", "Application이 Deploying에 머묾  Job 완료 여부와 상태 수렴 poll을 확인합니다.", "HTTPRoute가 Degraded  Gateway listener allowedRoutes와 Service port 및 protocol을 확인합니다.", "AI 요청이 timeout  Provider 연결 model routing과 section diagnostics를 확인합니다.", "세션 연장 실패  OIDC 공개 URL cookie와 Backend session 상태를 확인합니다."]:
        bullet(document, item)

    document.add_heading("관련 문서", level=1)
    for item in ["최초 설치  docs operations initial installation", "구성 요소 재배포  docs operations component deployment", "보안과 OIDC  docs security authentication authorization", "제품 명세와 기여  docs product current product specification, CONTRIBUTING 및 docs development"]:
        paragraph = document.add_paragraph(item, style="List Bullet")
        paragraph.paragraph_format.space_after = Pt(0)

    document.core_properties.title = "KlueOps 운영 가이드"
    document.core_properties.subject = "클러스터 운영과 Application Delivery 사용자 매뉴얼"
    document.core_properties.author = "Jae Youn Kim"
    apply_supported_font(document)
    document.save(OUTPUT)
    print(f"Updated {OUTPUT}")


if __name__ == "__main__":
    build()
