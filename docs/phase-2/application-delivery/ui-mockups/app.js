const allowedScreens = ["discover", "library", "sources", "values", "exposure", "preview", "applications", "application-detail", "states", "access-control", "ai-settings", "models"];
const overlay = document.querySelector("[data-overlay]");
const dialog = overlay.querySelector(".modal");
const modalKicker = overlay.querySelector(".modal-kicker");
const modalTitle = overlay.querySelector("#modal-title");
const modalBody = overlay.querySelector(".modal-body");
const modalActions = overlay.querySelector(".modal-actions");
const closeButton = overlay.querySelector(".modal-close");
let lastFocused;
let toastTimer;

function requestedScreen() {
  const value = new URLSearchParams(window.location.search).get("screen");
  return allowedScreens.includes(value) ? value : "discover";
}

function showScreen(screen, push = true) {
  document.querySelectorAll("[data-screen-panel]").forEach((panel) => panel.classList.toggle("active", panel.dataset.screenPanel === screen));
  document.querySelectorAll(".nav-item[data-screen]").forEach((item) => {
    const active = item.dataset.screen === screen || (screen === "application-detail" && item.dataset.screen === "applications");
    item.classList.toggle("active", active);
    active ? item.setAttribute("aria-current", "page") : item.removeAttribute("aria-current");
  });
  if (push) {
    const url = new URL(window.location.href);
    url.searchParams.set("screen", screen);
    window.history.pushState({ screen }, "", url);
  }
  window.scrollTo({ top: 0, behavior: "instant" });
  document.body.classList.remove("nav-open");
  document.querySelector(".mobile-menu")?.setAttribute("aria-expanded", "false");
}

function toast(message) {
  const element = document.querySelector(".toast");
  element.textContent = message;
  element.classList.add("show");
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => element.classList.remove("show"), 2600);
}

function closeModal() {
  overlay.hidden = true;
  document.body.classList.remove("modal-open");
  modalBody.innerHTML = "";
  modalActions.innerHTML = "";
  document.querySelectorAll(".sidebar, .screen.active, .topbar, .concept-banner").forEach((element) => element.removeAttribute("inert"));
  lastFocused?.focus();
}

function openModal({ kicker = "APPLICATION DELIVERY", title, body, confirm = "확인", danger = false, exact, onConfirm }) {
  lastFocused = document.activeElement;
  modalKicker.textContent = kicker;
  modalTitle.textContent = title;
  modalBody.innerHTML = body;
  modalActions.innerHTML = `<button class="secondary" data-modal-cancel>취소</button><button class="${danger ? "danger-button" : "primary"}" data-modal-confirm>${confirm}</button>`;
  const confirmButton = modalActions.querySelector("[data-modal-confirm]");
  if (exact) {
    confirmButton.disabled = true;
    const exactInput = modalBody.querySelector("[data-exact-input]");
    const exactError = modalBody.querySelector("[data-exact-error]");
    exactInput.addEventListener("input", () => {
      confirmButton.disabled = exactInput.value !== exact;
      exactError.hidden = exactInput.value.length === 0 || exactInput.value === exact;
    });
  }
  const requiredCheck = modalBody.querySelector("[data-require-check]");
  if (requiredCheck) {
    confirmButton.disabled = true;
    requiredCheck.addEventListener("change", () => { confirmButton.disabled = !requiredCheck.checked; });
  }
  confirmButton.addEventListener("click", () => {
    closeModal();
    if (onConfirm) onConfirm();
  });
  modalActions.querySelector("[data-modal-cancel]").addEventListener("click", closeModal);
  overlay.hidden = false;
  document.body.classList.add("modal-open");
  document.querySelectorAll(".sidebar, .screen.active, .topbar, .concept-banner").forEach((element) => element.setAttribute("inert", ""));
  (modalBody.querySelector("input, select, textarea") || confirmButton).focus();
}

function formField(label, value = "", hint = "") {
  return `<label class="modal-field"><span>${label}</span><input value="${value}" />${hint ? `<small>${hint}</small>` : ""}</label>`;
}

function exactBody(summary, exact, warning) {
  return `<div class="danger-summary"><strong>${summary}</strong><p>${warning}</p></div><div class="confirm-checks"><p>✓ Preflight 결과는 실행 시점까지 5분간 유효합니다.</p><p>✓ 작업 결과는 Job Center와 Audit에 기록됩니다.</p></div><label class="modal-field exact-field"><span>계속하려면 <code>${exact}</code> 입력</span><input data-exact-input autocomplete="off" placeholder="${exact}" /><small class="field-error" data-exact-error hidden>대상 이름과 정확히 일치해야 합니다.</small></label>`;
}

function jobResult(name) {
  openModal({ kicker: "JOB CENTER", title: `${name} 작업이 시작되었습니다`, body: `<div class="job-progress"><div><span>요청 검증</span><strong>완료</strong></div><div><span>Runner 작업 대기</span><strong>진행 중</strong></div><div class="progress-track"><i></i></div><p>화면을 닫아도 작업은 계속됩니다. 우측 상단 Job Center에서 상태를 확인할 수 있습니다.</p></div>`, confirm: "Job Center 보기", onConfirm: jobCenterModal });
}

function jobCenterModal() {
  openModal({ kicker: "GLOBAL JOB CENTER", title: "실행 중·최근 작업", body: `<div class="job-list"><div><b>Helm install · payments-web</b><span class="status progressing">● Running 42%</span><small>Application에서 DEPLOYING 상태로 확인 가능</small></div><div><b>Chart import · bitnami/nginx</b><span class="status progressing">● Running 62%</span><small>완료 후 Chart Library로 이동</small></div><div><b>Ollama model download · granite3.3:8b</b><span class="status healthy">✓ Completed</span><small>Local Models에서 검증 상태 확인</small></div></div><div class="info-box">Application 작업의 완료·실패 이력은 대상 Application의 History에 영구 보존됩니다.</div>`, confirm: "닫기" });
}

function sourceModal() {
  openModal({ title: "Helm 소스 추가", body: `<div class="step-mini"><b>1 연결 정보</b><span>2 검사 결과</span><span>3 저장</span></div><div class="segmented"><button class="active" type="button">Helm Repository</button><button type="button">OCI Registry</button></div>${formField("표시 이름", "team-charts")}${formField("Repository URL", "https://charts.example.com", "HTTPS만 허용하며 Browser가 아닌 Backend에서 연결합니다.")}<label class="modal-field"><span>Credential</span><select><option>인증 없음</option><option>Existing Secret 선택</option></select></label><div class="info-box">연결 검사는 Chart 데이터나 Tenant 정보를 외부로 보내지 않습니다.</div>`, confirm: "연결 검사", onConfirm: sourceResultModal });
}

function sourceResultModal() {
  openModal({ title: "연결 검사 완료", body: `<div class="step-mini"><span>1 연결 정보</span><b>2 검사 결과</b><span>3 저장</span></div><div class="review-list"><p><span>Endpoint</span><strong>✓ TLS 연결 성공</strong></p><p><span>Index</span><strong>42 charts · 186 versions</strong></p><p><span>Latency</span><strong>184 ms</strong></p><p><span>Credential</span><strong>인증 없음</strong></p></div><div class="info-box">Source를 저장하면 15분마다 index를 동기화합니다. Chart archive는 가져오기를 선택할 때만 저장합니다.</div>`, confirm: "Tenant Source 저장", onConfirm: () => toast("team-charts Source를 저장했습니다.") });
}

function directImportModal() {
  openModal({ title: "URL로 Chart 직접 가져오기", body: `<label class="modal-field"><span>Source type</span><select><option>OCI reference</option><option>Helm Repository</option></select></label>${formField("Chart reference", "oci://registry.example.com/charts/nginx")}${formField("Version", "18.2.4", "정확한 SemVer 또는 digest로 고정합니다.")}<label class="external-consent"><input type="checkbox" /><span>이 연결을 Tenant Sources에 저장</span></label><div class="info-box">한 건만 가져오며 체크하지 않으면 영구 Source를 만들지 않습니다.</div>`, confirm: "조회 및 검사", onConfirm: () => toast("Chart metadata와 archive를 검사 중입니다.") });
}

function localModelModal() {
  openModal({ kicker: "DEFAULT OLLAMA · MODEL MANAGEMENT", title: "Local Model 추가", body: `${formField("Ollama library model tag", "granite3.3:8b", "임의 URL과 Modelfile은 허용하지 않습니다.")}<div class="review-list"><p><span>Parameter limit</span><strong>9B 이하</strong></p><p><span>예상 download</span><strong>4.9 GB</strong></p><p><span>Volume 여유</span><strong>58.8 GB</strong></p><p><span>초기 상태</span><strong>Candidate</strong></p></div><div class="info-box">다운로드 후 size, parameter, license와 capability를 검사하고 regression gate를 통과해야 Tenant routing에 사용할 수 있습니다.</div>`, confirm: "Download Job 시작", onConfirm: () => jobResult("Ollama model download") });
}

function providerModal(provider = "New Provider", step = 1) {
  const steps = `<div class="step-mini"><${step === 1 ? "b" : "span"}>1 Provider</${step === 1 ? "b" : "span"}><${step === 2 ? "b" : "span"}>2 Credential</${step === 2 ? "b" : "span"}><${step === 3 ? "b" : "span"}>3 Model</${step === 3 ? "b" : "span"}><${step === 4 ? "b" : "span"}>4 Tenant</${step === 4 ? "b" : "span"}></div>`;
  const bodies = {
    1: `${steps}<label class="modal-field"><span>Provider type</span><select><option>Ollama</option><option>OpenAI</option><option>Google GenAI</option><option>OpenAI Compatible</option></select></label>${formField("Profile 이름", provider === "New Provider" ? "Team Provider" : provider)}${formField("Base URL", provider.includes("OpenAI") ? "https://api.openai.com" : "http://ollama.aiops.svc:11434")}`,
    2: `${steps}<label class="modal-field"><span>Credential</span><select><option>Existing Secret 선택</option><option>새 API key 등록</option></select><small>저장 후 credential 값은 다시 표시하지 않습니다.</small></label><div class="review-list"><p><span>Connectivity</span><strong>✓ Endpoint reachable</strong></p><p><span>Synthetic prompt</span><strong>✓ Response validated</strong></p></div>`,
    3: `${steps}<label class="modal-field"><span>Default model</span><select><option>qwen2.5-coder:7b · Approved</option><option>gemma3:4b · Approved</option></select><small>Local Candidate와 9B 초과 모델은 선택 목록에 나타나지 않습니다.</small></label><div class="info-box">Capability, context limit와 최근 regression 결과를 확인했습니다.</div>`,
    4: `${steps}<div class="review-list"><p><span>Profile</span><strong>${provider === "New Provider" ? "Team Provider" : provider}</strong></p><p><span>Allowed Tenant</span><strong>Platform Engineering</strong></p><p><span>Purpose</span><strong>AI Analysis · Chat · Helm Values</strong></p><p><span>External transfer</span><strong>OFF</strong></p></div><div class="external-consent"><input type="checkbox" data-require-check /><div><strong>설정과 Audit 정책 확인</strong><p>External Provider는 Tenant별 별도 동의 전까지 routing할 수 없습니다.</p></div></div>`,
  };
  openModal({ kicker: "PLATFORM ADMIN · AI", title: `${provider} 설정`, body: bodies[step], confirm: step < 4 ? "다음" : "Profile 저장", onConfirm: step < 4 ? () => providerModal(provider, step + 1) : () => toast("Provider Profile을 저장했습니다.") });
}

function deploymentStartModal() {
  openModal({ kicker: "APPLICATION DELIVERY", title: "어떤 Chart를 배포할까요?", body: `<p class="modal-note">Chart를 이미 보유했는지에 따라 가장 짧은 시작 경로를 선택합니다.</p><div class="launch-options"><button type="button" data-start="library"><strong>Chart Library에서 선택 <em>권장</em></strong><span>Tenant가 검증·승인한 Chart로 바로 시작</span></button><button type="button" data-start="discover"><strong>새 Chart 검색</strong><span>Artifact Hub에서 찾아 Library로 가져오기</span></button><button type="button" data-start="import"><strong>URL 또는 .tgz 가져오기</strong><span>알고 있는 원본이나 보유 파일 검사</span></button></div><div class="info-box">Cluster는 Values 설정 후 Target 단계에서 선택합니다. 이 단계에서는 Cluster를 변경하지 않습니다.</div>`, confirm: "닫기" });
}

function uninstallConfirmation() {
  openModal({ kicker: "위험 작업 확인", title: "payments-web을 제거할까요?", body: exactBody("Helm resource 6개와 HTTPRoute 1개를 삭제합니다.", "payments-web/payments", "PVC는 없으며 shared Namespace와 Chart Library는 유지합니다. 실패하면 cleanup 상태를 Application History에 남깁니다."), confirm: "Uninstall 실행", danger: true, exact: "payments-web/payments", onConfirm: () => jobResult("Helm uninstall") });
}

function addUserModal() {
  openModal({ kicker: "TENANT · USER LIFECYCLE", title: "Platform Engineering에 User 추가", body: `<div class="step-mini"><b>1 Identity</b><span>2 Role & Scope</span><span>3 Review</span></div><label class="modal-field"><span>Identity 연결 방식</span><select><option>Keycloak 초대 사용자 생성</option><option>외부 OIDC 사용자 사전 등록</option><option>OIDC Group 연결</option></select><small>외부 OIDC에서는 KlueOps가 비밀번호를 만들지 않습니다.</small></label>${formField("Email", "new-user@example.com")}<div class="field-grid modal-grid"><label>Role<select><option>Viewer</option><option>Operator</option><option>Cluster Admin</option><option>Tenant Admin</option></select></label><label>Scope<select><option>Tenant 전체</option><option>Workspace</option><option>Cluster</option><option>Namespace</option></select></label></div><label class="external-consent"><input type="checkbox" data-require-check /><span>초대와 Role/Scope, IdP 처리 방식을 확인했습니다.</span></label><div class="info-box">첫 로그인 시 issuer+subject를 membership에 연결합니다. verified email과 issuer allowlist를 검사합니다.</div>`, confirm: "초대 생성", onConfirm: () => toast("User 초대를 만들고 Audit에 기록했습니다.") });
}

function offboardUserModal() {
  openModal({ kicker: "위험 작업 계획", title: "Payments Operator 접근 중지", body: `<div class="review-list"><p><span>Tenant</span><strong>Platform Engineering</strong></p><p><span>Role/Scope</span><strong>Operator · prod-seoul/payments</strong></p><p><span>Sessions</span><strong>2개 revoke</strong></p><p><span>Role bindings</span><strong>1개 회수</strong></p><p><span>Running jobs</span><strong>없음</strong></p><p><span>Audit</span><strong>Actor snapshot 유지</strong></p></div><div class="danger-summary"><strong>Portal 접근은 즉시 차단됩니다.</strong><p>Keycloak 계정은 기본적으로 비활성화하며 영구 삭제하지 않습니다.</p></div>`, confirm: "Exact confirmation으로", danger: true, onConfirm: () => openModal({ kicker: "위험 작업 확인", title: "사용자를 탈퇴 처리할까요?", body: exactBody("RoleBinding, Session과 개인 Credential을 회수합니다.", "payments-operator", "기존 Audit과 작업 실행자 표시는 보존됩니다. 마지막 Platform Manager는 이 작업을 수행할 수 없습니다."), confirm: "탈퇴 처리", danger: true, exact: "payments-operator", onConfirm: () => jobResult("User offboarding") }) });
}

function simpleInfo(title, message) {
  openModal({ title, body: `<div class="info-box"><strong>${title}</strong><p>${message}</p></div>`, confirm: "닫기" });
}

function activateApplicationTab(tab, push = true) {
  const activeButton = document.querySelector(`[data-app-tab="${tab}"]`) || document.querySelector('[data-app-tab="overview"]');
  document.querySelectorAll("[data-app-tab]").forEach((item) => {
    item.classList.toggle("active", item === activeButton);
    item.setAttribute("aria-selected", String(item === activeButton));
  });
  document.querySelectorAll("[data-app-tab-panel]").forEach((item) => item.classList.toggle("active", item.dataset.appTabPanel === activeButton.dataset.appTab));
  if (push) {
    const url = new URL(window.location.href);
    url.searchParams.set("tab", activeButton.dataset.appTab);
    window.history.pushState({ screen: "application-detail", tab: activeButton.dataset.appTab }, "", url);
  }
}

function actionFor(button) {
  const label = button.textContent.replace(/\s+/g, " ").trim();
  const panel = button.closest("[data-screen-panel]")?.dataset.screenPanel;

  if (button.dataset.screen) return () => showScreen(button.dataset.screen);
  if (button.classList.contains("chip")) return () => { button.parentElement.querySelectorAll(".chip").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label} 필터를 적용했습니다.`); };
  if (button.closest(".choice-grid")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("selected")); button.classList.add("selected"); toast(`${label} 방식을 선택했습니다. Preview를 다시 계산합니다.`); };
  if (button.closest(".app-tabs")) return () => {
    activateApplicationTab(button.dataset.appTab);
  };
  if (button.closest(".values-nav")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label.replace(/\d+$/, "")} Values 항목을 표시합니다.`); };
  if (button.closest(".mode-tabs")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label} 편집 모드로 전환했습니다.`); };
  if (button.closest(".segmented")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); };
  if (button.getAttribute("aria-label") === "검색") return () => openModal({ kicker: "GLOBAL SEARCH", title: "KlueOps 전체 검색", body: `${formField("검색어")}<p class="modal-note">Cluster, Release, Chart와 Cook Book을 Tenant 권한 범위에서 검색합니다.</p>`, confirm: "검색" });
  if (button.getAttribute("aria-label") === "작업 센터") return jobCenterModal;
  if (button.getAttribute("aria-label") === "대화상자 닫기") return closeModal;
  if (button.getAttribute("aria-label") === "메뉴 열기") return () => {
    const open = document.body.classList.toggle("nav-open");
    button.setAttribute("aria-expanded", String(open));
  };

  const actions = {
    "Overview": () => simpleInfo("1차 기능 화면", "실제 제품의 Overview route로 이동합니다. Phase 2 시안에서는 현재 화면을 유지합니다."),
    "Clusters": () => simpleInfo("Cluster 선택", "등록된 Cluster 목록에서 Application Delivery 대상과 권한을 확인합니다."),
    "Cook Book": () => simpleInfo("Cook Book", "점검 절차 화면으로 이동하는 기존 1차 기능 링크입니다."),
    "?": () => simpleInfo("Application Delivery 도움말", "Discover → Library → Values → Target/Exposure → Preview → Application 순서로 진행합니다. 실행 중 progress는 Job Center, 완료·실패 이력은 Application History에서 확인합니다."),
    "↗ URL로 직접 가져오기": directImportModal,
    "＋ Source 등록": sourceModal,
    "Artifact Hub 검색": () => toast("Artifact Hub에서 ‘nginx’ 검색 결과 1,284건을 불러왔습니다."),
    "Tenant Library로 가져오기": () => openModal({ title: "Chart를 가져올까요?", body: `<div class="review-list"><p><span>Chart</span><strong>bitnami/nginx 18.2.4</strong></p><p><span>Source</span><strong>Artifact Hub → 원본 Helm repository</strong></p><p><span>Digest</span><strong>Import 후 고정 및 검증</strong></p><p><span>Tenant</span><strong>Platform Engineering</strong></p></div><div class="info-box">가져오기는 Cluster를 변경하지 않습니다. 완료 후 Chart Library에서 사용할 수 있습니다.</div>`, confirm: "가져오기", onConfirm: () => jobResult("Chart import") }),
    "↑ .tgz 업로드": () => openModal({ title: "Helm Chart 업로드", body: `<label class="drop-zone"><input type="file" accept=".tgz" /><strong>.tgz 파일을 선택하거나 끌어놓으세요</strong><span>최대 20 MiB · 압축 해제 크기와 경로 traversal을 검사합니다.</span></label><div class="info-box">업로드 후 Chart.yaml, digest, values/schema와 provenance를 검사합니다.</div>`, confirm: "검사 후 업로드", onConfirm: () => jobResult("Chart upload") }),
    "초기화": () => openModal({ title: "Values 변경을 초기화할까요?", body: `<div class="warning-box"><strong>저장하지 않은 14개 변경이 사라집니다.</strong><p>마지막 저장 revision인 prod-ha-v2로 되돌립니다.</p></div>`, confirm: "변경 초기화", danger: true, onConfirm: () => toast("Values를 마지막 저장 상태로 되돌렸습니다.") }),
    "Values 저장": () => openModal({ title: "Values Profile 저장", body: `${formField("Profile 이름", "prod-ha-v3")}${formField("Revision note", "Increase replicas and resource requests")}<div class="info-box">14개 변경 값이 schema를 통과했습니다. Secret-like 값은 포함되지 않았습니다.</div>`, confirm: "새 revision 저장", onConfirm: () => toast("prod-ha-v3 revision을 저장했습니다.") }),
    "↑": () => toast("Values 제안을 생성 중입니다. Form 편집은 계속할 수 있습니다."),
    "변경": () => openModal({ title: "배포 대상 변경", body: `<label class="modal-field"><span>Cluster</span><select><option>prod-seoul</option><option>dev-busan</option></select></label><label class="modal-field"><span>Namespace</span><select><option>payments</option><option>checkout</option></select></label>${formField("Release 이름", "payments-web")}<div class="info-box">접근 가능한 Cluster와 Namespace만 표시됩니다.</div>`, confirm: "대상 적용", onConfirm: () => toast("배포 대상을 적용하고 Preview를 다시 생성합니다.") }),
    "전체 렌더링 YAML 보기 ↗": () => openModal({ title: "Rendered manifests", body: `<div class="code-toolbar"><span>6 resources · Secret value masked</span><button type="button">복사</button></div><pre class="modal-code">apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: payments-web\nspec:\n  replicas: 3\n---\napiVersion: v1\nkind: Service\nmetadata:\n  name: payments-web</pre>`, confirm: "닫기" }),
    "이전": () => showScreen("values"),
    "확정 단계로 →": () => openModal({ kicker: "위험 작업 확인", title: "payments-web을 배포할까요?", body: exactBody("prod-seoul / payments에 Helm resource 6개와 HTTPRoute 1개를 적용합니다.", "payments-web", "접근 URL은 https://nginx.cluster.co.kr/입니다. Exposure가 REQUIRED 상태에서 실패하면 Helm atomic rollback 정책을 적용합니다."), confirm: "배포 실행", danger: true, exact: "payments-web", onConfirm: () => jobResult("Helm deployment") }),
    "Application 배포": deploymentStartModal,
    "···": () => panel === "applications" ? openModal({ title: "Application 작업", body: `<div class="action-list"><button type="button">Application 상세 보기</button><button type="button">Audit 기록 보기</button><button type="button">Uninstall 계획 생성</button></div>`, confirm: "닫기" }) : panel === "sources" ? openModal({ title: "Source 작업", body: `<div class="action-list"><button type="button">지금 동기화</button><button type="button">연결 검사</button><button type="button">Credential 교체</button><button type="button">Source 비활성화</button></div>`, confirm: "닫기" }) : panel === "models" ? openModal({ title: "Local Model 작업", body: `<div class="action-list"><button type="button">Capability 보기</button><button type="button">Regression 실행</button><button type="button">Tenant 허용 설정</button><button type="button">삭제 가능 여부 검사</button></div>`, confirm: "닫기" }) : panel === "access-control" ? openModal({ title: "User 작업", body: `<div class="action-list"><button type="button">역할과 Scope 편집</button><button type="button">Access Preview</button><button type="button">Session revoke</button><button type="button">접근 중지 계획</button></div>`, confirm: "닫기" }) : simpleInfo("Provider 작업", "연결 검사, Profile 편집, 비활성화와 삭제 작업을 선택할 수 있습니다."),
    "Values Diff": () => openModal({ title: "Revision 6 → 7 Values Diff", body: `<pre class="modal-code"><span class="minus">- replicaCount: 2</span>\n<span class="plus">+ replicaCount: 3</span>\n<span class="plus">+ resources.requests.cpu: 250m</span>\n<span class="plus">+ resources.requests.memory: 256Mi</span></pre>`, confirm: "닫기" }),
    "Rollback": () => openModal({ kicker: "위험 작업 확인", title: "Revision 6으로 Rollback", body: exactBody("payments-web을 이전 Values와 Chart 상태로 되돌립니다.", "payments-web", "새 Helm revision 8이 생성되며 현재 revision 7은 이력에 유지됩니다."), confirm: "Rollback 실행", danger: true, exact: "payments-web", onConfirm: () => jobResult("Helm rollback") }),
    "Upgrade": () => showScreen("values"),
    "＋ Provider Profile": () => providerModal(),
    "＋ Local Model 추가": localModelModal,
    "정책 보기": () => openModal({ kicker: "DATA GOVERNANCE", title: "외부 LLM 전송 정책", body: `<div class="policy-list"><p>✓ Tenant와 purpose별 명시적 opt-in</p><p>✓ Secret, token, certificate, kubeconfig 제외</p><p>✓ raw manifest 대신 필요한 evidence만 최소화</p><p>✓ Provider/model과 context 크기를 Audit에 기록</p><p>✕ Local 실패 시 외부로 자동 전환하지 않음</p></div>`, confirm: "확인" }),
    "설정": () => providerModal(button.closest(".provider-card")?.querySelector("h3")?.textContent || "Provider"),
    "Kubernetes Console": () => toast("payments-web Deployment를 Kubernetes Console에서 엽니다."),
    "접근 설정 변경": () => showScreen("exposure"),
    "AI Analysis": () => toast("Application evidence를 수집하는 분석 Job을 시작합니다."),
    "Uninstall 계획": () => openModal({ kicker: "위험 작업 계획", title: "payments-web Uninstall 계획", body: `<div class="review-list"><p><span>Helm resources</span><strong>6개 삭제</strong></p><p><span>Companion</span><strong>HTTPRoute 1개 삭제</strong></p><p><span>PVC</span><strong>없음</strong></p><p><span>Namespace</span><strong>payments 유지</strong></p><p><span>Chart Library</span><strong>nginx 18.2.4 유지</strong></p></div><div class="danger-summary"><strong>계획 생성은 아직 Resource를 삭제하지 않습니다.</strong><p>Preflight가 통과했습니다. 다음 단계에서 payments-web/payments를 정확히 입력해야 합니다.</p></div>`, confirm: "Exact confirmation으로", danger: true, onConfirm: uninstallConfirmation }),
    "변경 저장": () => openModal({ kicker: "TENANT AI ROUTING", title: "AI Routing 변경 저장", body: `<div class="review-list"><p><span>AI Analysis</span><strong>Default Ollama / qwen2.5-coder:7b · Approved</strong></p><p><span>AI Chat</span><strong>Default Ollama / qwen2.5-coder:7b · Approved</strong></p><p><span>Helm Values</span><strong>Default Ollama / gemma3:4b · Approved</strong></p><p><span>External</span><strong>OFF</strong></p></div><div class="info-box">Candidate는 평가 전용이라 선택되지 않았습니다. 변경은 새 요청부터 적용됩니다.</div>`, confirm: "Routing 저장", onConfirm: () => toast("Tenant AI Routing 정책을 저장했습니다.") }),
    "＋ User 추가": addUserModal,
    "역할과 Scope 편집": () => simpleInfo("RoleBinding 편집", "Role과 Tenant/Workspace/Cluster/Namespace scope를 변경하면 메뉴 Preview와 실제 API 권한을 다시 계산합니다."),
    "접근 중지 계획": offboardUserModal,
    "Tenant 기능 설정": () => openModal({ kicker: "TENANT FEATURE POLICY", title: "Platform Engineering 기능 설정", body: `<div class="policy-list"><p>✓ Core Overview · 필수</p><p>✓ Cluster Operations · ON</p><p>✓ Kubernetes Console · ON</p><p>✓ AI Operations · ON</p><p>✓ Application Delivery · ON</p><p>✓ Access Control · 필수</p></div><div class="info-box">기능 설정은 Role에 없는 권한을 부여할 수 없습니다. OFF는 해당 메뉴와 직접 route/API를 함께 비활성화합니다.</div>`, confirm: "닫기" }),
  };
  return actions[label] || (button.dataset.toast ? () => toast(button.dataset.toast) : () => simpleInfo(label || "동작 안내", "이 컨트롤의 상세 화면 또는 선택 상태가 표시되는 시안입니다."));
}

document.querySelectorAll("button").forEach((button) => {
  if (button.closest(".modal-actions")) return;
  button.addEventListener("click", actionFor(button));
  button.dataset.interactive = "true";
});

document.querySelectorAll(".result-card, .release-table tbody tr").forEach((row) => row.addEventListener("click", () => {
  row.parentElement.querySelectorAll(".selected, .row-selected").forEach((item) => item.classList.remove("selected", "row-selected"));
  row.classList.add(row.classList.contains("result-card") ? "selected" : "row-selected");
}));

document.querySelectorAll(".select-like, .switch").forEach((control) => {
  if (!control.classList.contains("switch")) control.setAttribute("role", "button");
  control.tabIndex = 0;
  control.addEventListener("click", () => control.classList.contains("switch") ? openModal({ kicker: "외부 전송 동의", title: "외부 LLM 전송을 켤까요?", body: `<div class="warning-box"><strong>Tenant 데이터가 외부 Provider로 전송될 수 있습니다.</strong><p>Provider, purpose와 전송 데이터 범위를 검토한 뒤 Platform 정책 동의가 필요합니다.</p></div><label class="external-consent"><input type="checkbox" data-require-check /><span>데이터 최소화 및 Audit 정책을 확인했습니다.</span></label>`, confirm: "정책 검토 계속" }) : simpleInfo("Routing 선택", "허용된 Provider Profile과 9B 이하 local model 목록을 선택합니다."));
  control.addEventListener("keydown", (event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); control.click(); } });
});

overlay.addEventListener("click", (event) => {
  if (event.target === overlay) { closeModal(); return; }
  const startButton = event.target.closest("[data-start]");
  if (startButton) {
    const target = startButton.dataset.start;
    closeModal();
    if (target === "import") directImportModal(); else showScreen(target);
    return;
  }
  const inlineButton = event.target.closest(".segmented button, .code-toolbar button, .action-list button");
  if (!inlineButton) return;
  if (inlineButton.closest(".segmented")) {
    inlineButton.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active"));
    inlineButton.classList.add("active");
    return;
  }
  if (inlineButton.closest(".code-toolbar")) { toast("Rendered YAML을 clipboard에 복사했습니다."); return; }
  simpleInfo(inlineButton.textContent.trim(), `${inlineButton.textContent.trim()} 후속 화면의 설계 진입점입니다. 실행 전 대상과 영향을 다시 확인합니다.`);
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !overlay.hidden) { closeModal(); return; }
  if (event.key !== "Tab" || overlay.hidden) return;
  const focusable = [...dialog.querySelectorAll('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]')];
  if (!focusable.length) return;
  const first = focusable[0]; const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
  if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
});
window.addEventListener("popstate", () => showScreen(requestedScreen(), false));
showScreen(requestedScreen(), false);
if (requestedScreen() === "application-detail") {
  const initialTab = new URLSearchParams(window.location.search).get("tab");
  activateApplicationTab(initialTab || "overview", false);
}
