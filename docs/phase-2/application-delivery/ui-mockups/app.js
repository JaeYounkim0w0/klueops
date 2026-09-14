const allowedScreens = ["discover", "library", "sources", "values", "exposure", "preview", "applications", "application-detail", "ai-settings", "models"];
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
  document.querySelectorAll(".nav-item[data-screen]").forEach((item) => item.classList.toggle("active", item.dataset.screen === screen));
  if (push) {
    const url = new URL(window.location.href);
    url.searchParams.set("screen", screen);
    window.history.pushState({ screen }, "", url);
  }
  window.scrollTo({ top: 0, behavior: "instant" });
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
  (modalBody.querySelector("input, select, textarea") || confirmButton).focus();
}

function formField(label, value = "", hint = "") {
  return `<label class="modal-field"><span>${label}</span><input value="${value}" />${hint ? `<small>${hint}</small>` : ""}</label>`;
}

function exactBody(summary, exact, warning) {
  return `<div class="danger-summary"><strong>${summary}</strong><p>${warning}</p></div><div class="confirm-checks"><p>✓ Preflight 결과는 실행 시점까지 5분간 유효합니다.</p><p>✓ 작업 결과는 Job Center와 Audit에 기록됩니다.</p></div><label class="modal-field exact-field"><span>계속하려면 <code>${exact}</code> 입력</span><input data-exact-input autocomplete="off" placeholder="${exact}" /><small class="field-error" data-exact-error hidden>대상 이름과 정확히 일치해야 합니다.</small></label>`;
}

function jobResult(name) {
  openModal({ kicker: "JOB CENTER", title: `${name} 작업이 시작되었습니다`, body: `<div class="job-progress"><div><span>요청 검증</span><strong>완료</strong></div><div><span>Runner 작업 대기</span><strong>진행 중</strong></div><div class="progress-track"><i></i></div><p>화면을 닫아도 작업은 계속됩니다. 우측 상단 Job Center에서 상태를 확인할 수 있습니다.</p></div>`, confirm: "Job Center 보기", onConfirm: () => toast("Job Center drawer를 열었습니다.") });
}

function sourceModal() {
  openModal({ title: "Helm 소스 추가", body: `<div class="segmented"><button class="active" type="button">Helm Repository</button><button type="button">OCI Registry</button></div>${formField("표시 이름", "team-charts")}${formField("Repository URL", "https://charts.example.com", "HTTPS만 허용하며 Browser가 아닌 Backend에서 연결합니다.")}<label class="modal-field"><span>Credential</span><select><option>인증 없음</option><option>Existing Secret 선택</option></select></label><div class="info-box">연결 검사는 Chart 데이터나 Tenant 정보를 외부로 보내지 않습니다.</div>`, confirm: "연결 검사", onConfirm: () => toast("연결 검사가 완료되었습니다. 저장할 수 있습니다.") });
}

function directImportModal() {
  openModal({ title: "URL로 Chart 직접 가져오기", body: `<label class="modal-field"><span>Source type</span><select><option>OCI reference</option><option>Helm Repository</option></select></label>${formField("Chart reference", "oci://registry.example.com/charts/nginx")}${formField("Version", "18.2.4", "정확한 SemVer 또는 digest로 고정합니다.")}<label class="external-consent"><input type="checkbox" /><span>이 연결을 Tenant Sources에 저장</span></label><div class="info-box">한 건만 가져오며 체크하지 않으면 영구 Source를 만들지 않습니다.</div>`, confirm: "조회 및 검사", onConfirm: () => toast("Chart metadata와 archive를 검사 중입니다.") });
}

function localModelModal() {
  openModal({ kicker: "DEFAULT OLLAMA · MODEL MANAGEMENT", title: "Local Model 추가", body: `${formField("Ollama library model tag", "granite3.3:8b", "임의 URL과 Modelfile은 허용하지 않습니다.")}<div class="review-list"><p><span>Parameter limit</span><strong>9B 이하</strong></p><p><span>예상 download</span><strong>4.9 GB</strong></p><p><span>Volume 여유</span><strong>58.8 GB</strong></p><p><span>초기 상태</span><strong>Candidate</strong></p></div><div class="info-box">다운로드 후 size, parameter, license와 capability를 검사하고 regression gate를 통과해야 Tenant routing에 사용할 수 있습니다.</div>`, confirm: "Download Job 시작", onConfirm: () => jobResult("Ollama model download") });
}

function providerModal(provider = "New Provider") {
  openModal({ kicker: "PLATFORM ADMIN · AI", title: `${provider} 설정`, body: `<div class="step-mini"><b>1 Provider</b><span>2 Credential</span><span>3 Model</span><span>4 Tenant</span></div><label class="modal-field"><span>Provider type</span><select><option>Ollama</option><option>OpenAI</option><option>Google GenAI</option><option>OpenAI Compatible</option></select></label>${formField("Profile 이름", provider === "New Provider" ? "" : provider)}${formField("Base URL", provider.includes("OpenAI") ? "https://api.openai.com" : "http://ollama.aiops.svc:11434")}<label class="modal-field"><span>Credential</span><select><option>Existing Secret 선택</option><option>새 API key 등록</option></select><small>저장 후 credential 값은 다시 표시하지 않습니다.</small></label><div class="external-consent"><input type="checkbox" /><div><strong>외부 데이터 전송 Provider</strong><p>외부 Provider인 경우 Tenant에서 purpose별 동의를 추가로 받아야 합니다.</p></div></div>`, confirm: "연결 검사 후 다음", onConfirm: () => toast("Synthetic prompt 연결 검사를 시작했습니다.") });
}

function simpleInfo(title, message) {
  openModal({ title, body: `<div class="info-box"><strong>${title}</strong><p>${message}</p></div>`, confirm: "닫기" });
}

function actionFor(button) {
  const label = button.textContent.replace(/\s+/g, " ").trim();
  const panel = button.closest("[data-screen-panel]")?.dataset.screenPanel;

  if (button.dataset.screen) return () => showScreen(button.dataset.screen);
  if (button.classList.contains("chip")) return () => { button.parentElement.querySelectorAll(".chip").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label} 필터를 적용했습니다.`); };
  if (button.closest(".choice-grid")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("selected")); button.classList.add("selected"); toast(`${label} 방식을 선택했습니다. Preview를 다시 계산합니다.`); };
  if (button.closest(".app-tabs")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label} 탭을 표시합니다.`); };
  if (button.closest(".values-nav")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label.replace(/\d+$/, "")} Values 항목을 표시합니다.`); };
  if (button.closest(".mode-tabs")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); toast(`${label} 편집 모드로 전환했습니다.`); };
  if (button.closest(".segmented")) return () => { button.parentElement.querySelectorAll("button").forEach((item) => item.classList.remove("active")); button.classList.add("active"); };
  if (button.getAttribute("aria-label") === "검색") return () => openModal({ kicker: "GLOBAL SEARCH", title: "KlueOps 전체 검색", body: `${formField("검색어")}<p class="modal-note">Cluster, Release, Chart와 Cook Book을 Tenant 권한 범위에서 검색합니다.</p>`, confirm: "검색" });
  if (button.getAttribute("aria-label") === "작업 센터") return () => openModal({ kicker: "JOB CENTER", title: "실행 중인 작업", body: `<div class="job-list"><div><b>Chart import · bitnami/nginx</b><span class="status progressing">● Running 62%</span></div><div><b>Release upgrade · events</b><span class="status healthy">✓ Completed</span></div></div>`, confirm: "전체 작업 보기" });
  if (button.getAttribute("aria-label") === "대화상자 닫기") return closeModal;

  const actions = {
    "Overview": () => simpleInfo("1차 기능 화면", "실제 제품의 Overview route로 이동합니다. Phase 2 시안에서는 현재 화면을 유지합니다."),
    "Clusters": () => simpleInfo("Cluster 선택", "등록된 Cluster 목록에서 Application Delivery 대상과 권한을 확인합니다."),
    "Cook Book": () => simpleInfo("Cook Book", "점검 절차 화면으로 이동하는 기존 1차 기능 링크입니다."),
    "?": () => simpleInfo("Application Delivery 도움말", "Discover → Library → Values → Preview → Release 순서로 진행합니다. 위험 작업은 실행 전에 별도 확인이 필요합니다."),
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
    "＋ 새 배포": () => showScreen("discover"),
    "···": () => panel === "applications" ? openModal({ title: "Application 작업", body: `<div class="action-list"><button type="button">Application 상세 보기</button><button type="button">Audit 기록 보기</button><button type="button">Uninstall 계획 생성</button></div>`, confirm: "닫기" }) : panel === "sources" ? openModal({ title: "Source 작업", body: `<div class="action-list"><button type="button">지금 동기화</button><button type="button">연결 검사</button><button type="button">Credential 교체</button><button type="button">Source 비활성화</button></div>`, confirm: "닫기" }) : panel === "models" ? openModal({ title: "Local Model 작업", body: `<div class="action-list"><button type="button">Capability 보기</button><button type="button">Regression 실행</button><button type="button">Tenant 허용 설정</button><button type="button">삭제 가능 여부 검사</button></div>`, confirm: "닫기" }) : simpleInfo("Provider 작업", "연결 검사, Profile 편집, 비활성화와 삭제 작업을 선택할 수 있습니다."),
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
    "Uninstall 계획": () => openModal({ kicker: "위험 작업 계획", title: "payments-web Uninstall 계획", body: `<div class="review-list"><p><span>Helm resources</span><strong>6개 삭제</strong></p><p><span>Companion</span><strong>HTTPRoute 1개 삭제</strong></p><p><span>PVC</span><strong>없음</strong></p><p><span>Namespace</span><strong>payments 유지</strong></p><p><span>Chart Library</span><strong>nginx 18.2.4 유지</strong></p></div><div class="danger-summary"><strong>계획 생성은 아직 Resource를 삭제하지 않습니다.</strong><p>Preflight 이후 별도 exact confirmation에서 payments-web/payments를 입력해야 합니다.</p></div>`, confirm: "Uninstall Preview 생성", danger: true, onConfirm: () => toast("Uninstall Preview를 생성했습니다.") }),
    "변경 저장": () => openModal({ kicker: "TENANT AI ROUTING", title: "AI Routing 변경 저장", body: `<div class="review-list"><p><span>AI Analysis</span><strong>Default Ollama / granite3.3:8b 후보</strong></p><p><span>AI Chat</span><strong>Default Ollama / qwen2.5-coder:7b</strong></p><p><span>Helm Values</span><strong>Default Ollama / granite3.3:8b 후보</strong></p><p><span>External</span><strong>OFF</strong></p></div><div class="info-box">실행 중인 Job에는 영향을 주지 않고 새 요청부터 적용됩니다.</div>`, confirm: "Routing 저장", onConfirm: () => toast("Tenant AI Routing 정책을 저장했습니다.") }),
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
  control.setAttribute("role", "button");
  control.tabIndex = 0;
  control.addEventListener("click", () => control.classList.contains("switch") ? openModal({ kicker: "외부 전송 동의", title: "외부 LLM 전송을 켤까요?", body: `<div class="warning-box"><strong>Tenant 데이터가 외부 Provider로 전송될 수 있습니다.</strong><p>Provider, purpose와 전송 데이터 범위를 검토한 뒤 Platform 정책 동의가 필요합니다.</p></div><label class="external-consent"><input type="checkbox" data-require-check /><span>데이터 최소화 및 Audit 정책을 확인했습니다.</span></label>`, confirm: "정책 검토 계속" }) : simpleInfo("Routing 선택", "허용된 Provider Profile과 9B 이하 local model 목록을 선택합니다."));
  control.addEventListener("keydown", (event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); control.click(); } });
});

overlay.addEventListener("click", (event) => {
  if (event.target === overlay) { closeModal(); return; }
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
document.addEventListener("keydown", (event) => { if (event.key === "Escape" && !overlay.hidden) closeModal(); });
window.addEventListener("popstate", () => showScreen(requestedScreen(), false));
showScreen(requestedScreen(), false);
