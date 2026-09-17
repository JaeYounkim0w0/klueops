import { chromium } from "../../frontend/node_modules/playwright/index.mjs";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const outputDirectory = path.join(here, "screenshots");
const applicationUrl = process.env.AIOPS_GUIDE_APPLICATION_URL ?? "http://127.0.0.1:30081";
const username = process.env.AIOPS_GUIDE_USERNAME;
const password = process.env.AIOPS_GUIDE_PASSWORD;

if (!username || !password) {
  throw new Error("AIOPS_GUIDE_USERNAME과 AIOPS_GUIDE_PASSWORD가 필요합니다.");
}

const screens = [
  ["01-dashboard.png", "/", /Dashboard|대시보드/],
  ["02-triage.png", "/triage", /Triage|운영 판단/],
  ["03-fleet-command.png", "/operations/fleet", /Fleet Command/],
  ["04-incidents.png", "/incidents", /Incidents|인시던트/],
  ["05-clusters.png", "/clusters", /Clusters|클러스터/],
  ["06-applications.png", "/applications", /Deployed Applications/],
  ["07-chart-library.png", "/applications/library", /Chart Library/],
  ["08-discover.png", "/applications/discover", /Discover/],
  ["09-sources.png", "/applications/sources", /Sources/],
  ["10-ai-analysis.png", "/analysis", /AI Analysis/],
  ["11-ai-chat.png", "/ai-chat", /AI Chat/],
  ["12-runbooks.png", "/runbooks", /Runbooks/],
  ["13-ai-trust.png", "/ai/trust", /AI 신뢰|AI Trust/],
  ["14-policies.png", "/policies", /Policies|정책/],
  ["15-audit.png", "/audit", /Audit|감사/],
  ["16-operations-reliability.png", "/settings/reliability", /Operations Reliability|운영 신뢰/],
  ["17-data-runtime.png", "/settings/operations", /Data & Runtime/],
  ["18-ai-providers.png", "/settings/ai-providers", /AI Providers/],
  ["19-users-access.png", "/settings/users-access", /사용자 및 권한|Users/],
  ["20-tenancy.png", "/settings/tenancy", /Tenant 및 그룹 관리|Tenant/],
  ["21-preferences.png", "/settings/preferences", /사용자 설정|Preferences/],
];

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  viewport: { width: 1600, height: 1000 },
  deviceScaleFactor: 1,
  locale: "ko-KR",
});
const page = await context.newPage();

// 실제 OIDC 로그인과 BFF 세션을 거쳐 운영 화면을 검증한다.
await page.goto(`${applicationUrl}/login`, { waitUntil: "domcontentloaded" });
await page.getByRole("button", { name: /로그인 계속하기|Continue to sign in|재시도|Retry/ }).click();
await page.getByLabel(/Username or email|사용자 이름|Username/i).fill(username);
await page.locator('input[name="password"]').fill(password);
await page.getByRole("button", { name: /Sign In|로그인/i }).click();
await page.waitForURL((url) => url.origin === new URL(applicationUrl).origin && url.pathname !== "/login", { timeout: 30_000 });

// 예시 데이터가 준비된 기본 Tenant를 선택해 빈 화면이 아닌 실제 운영 상태를 기록한다.
const tenantSelector = page.getByRole("combobox", { name: /Company \/ Tenant|회사 \/ Tenant/ });
if (await tenantSelector.isVisible().catch(() => false)) {
  await tenantSelector.selectOption({ label: "Default Tenant" });
  await page.waitForTimeout(500);
}

await fs.mkdir(outputDirectory, { recursive: true });

async function anonymizeVisibleData() {
  // 문서용 화면에는 개인 계정과 로컬 인프라 식별자를 재현 가능한 예시 값으로 표시한다.
  await page.evaluate(() => {
    const replacements = new Map([
      ["kim jaeyoun", "Platform Manager"],
      ["Acceptance aiops-acceptance-admin", "Platform Manager"],
      ["aiops-acceptance-admin", "platform-admin"],
      ["aiops-admin", "platform-admin"],
      ["docker-desktop-exposure", "sample-cluster"],
      ["dev-master", "production-cluster"],
      ["Default Tenant", "Example Company"],
      ["Default Workspace", "Platform Operations"],
      ["127.0.0.1", "portal.example.local"],
      ["host.docker.internal", "llm.example.local"],
    ]);
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    let node;
    while ((node = walker.nextNode())) {
      let value = node.nodeValue ?? "";
      for (const [source, replacement] of replacements) value = value.split(source).join(replacement);
      node.nodeValue = value;
    }
  });
}

for (const [filename, route, expected] of screens) {
  await page.goto(`${applicationUrl}${route}`, { waitUntil: "domcontentloaded" });
  await page.locator("main, .main-content, .workspace-content").first().waitFor({ state: "visible", timeout: 20_000 }).catch(() => {});
  await page.waitForTimeout(700);
  const pageText = await page.locator("body").innerText();
  if (!expected.test(pageText) && !expected.test(route)) {
    throw new Error(`${route}에서 예상 화면을 확인하지 못했습니다: ${expected}`);
  }
  await anonymizeVisibleData();
  await page.screenshot({ path: path.join(outputDirectory, filename), fullPage: false });
}

// 메뉴 최초 화면 외에 실제 운영자가 사용하는 상세 화면, 탭, 팝업과 하위 작업 화면을 기록한다.
// 데이터가 없는 기능은 건너뛰되 다른 상세 화면 캡처는 계속 수행한다.
const detailedScreens = [];

async function captureDetailed(filename, route, action) {
  try {
    await page.goto(`${applicationUrl}${route}`, { waitUntil: "domcontentloaded" });
    await page.locator("main, .main-content, .workspace-content").first().waitFor({ state: "visible", timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(800);
    if (action) await action();
    await page.waitForTimeout(500);
    await anonymizeVisibleData();
    await page.screenshot({ path: path.join(outputDirectory, filename), fullPage: false });
    detailedScreens.push(filename);
  } catch (error) {
    console.warn(`Skipped ${filename}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

await captureDetailed("22-fleet-trend.png", "/operations/fleet", async () => {
  await page.getByRole("button", { name: /신뢰도 추세/ }).click();
});
await captureDetailed("23-fleet-validation.png", "/operations/fleet", async () => {
  await page.getByRole("button", { name: /검증 랩/ }).click();
});
await captureDetailed("24-incident-create-dialog.png", "/incidents", async () => {
  await page.getByRole("button", { name: /수동 Incident/ }).click();
});

const clusters = await page.evaluate(async () => {
  const response = await fetch("/api/clusters");
  return response.ok ? response.json() : [];
}).catch(() => []);
const guideCluster = Array.isArray(clusters) ? clusters.find((item) => item?.id) : null;
if (guideCluster) {
  await captureDetailed("25-cluster-detail.png", `/clusters/${guideCluster.id}`);
  await captureDetailed("26-cluster-readiness.png", `/clusters/${guideCluster.id}`, async () => {
    const tabs = page.locator(".readiness-tabs button");
    if (await tabs.count()) await tabs.nth(Math.min(1, (await tabs.count()) - 1)).click();
  });
  await captureDetailed("27-cluster-resource-detail.png", `/clusters/${guideCluster.id}`, async () => {
    const resource = page.locator(".resource-table button").first();
    if (await resource.count()) await resource.click();
  });
  await captureDetailed("28-kubernetes-console.png", `/clusters/${guideCluster.id}/console`);
}
await captureDetailed("29-cluster-registration-dialog.png", "/clusters", async () => {
  await page.getByRole("button", { name: /클러스터 등록/ }).click();
});

const tenantId = await page.evaluate(() => localStorage.getItem("aiops.tenantId") || "");
const incidents = await page.evaluate(async (selectedTenantId) => {
  const query = selectedTenantId ? `?tenantId=${encodeURIComponent(selectedTenantId)}` : "";
  const response = await fetch(`/api/incidents${query}`);
  return response.ok ? response.json() : [];
}, tenantId).catch(() => []);
const guideIncident = Array.isArray(incidents) ? incidents.find((item) => item?.id) : null;
if (guideIncident) {
  await captureDetailed("30-incident-detail.png", `/incidents/${guideIncident.id}`);
}

await captureDetailed("31-application-start-dialog.png", "/applications", async () => {
  await page.getByRole("button", { name: /Application 배포|새 배포/ }).click();
});
await captureDetailed("32-chart-upload-dialog.png", "/applications/library", async () => {
  await page.getByRole("button", { name: /.tgz 가져오기/ }).click();
});

await page.goto(`${applicationUrl}/applications/library`, { waitUntil: "domcontentloaded" });
await page.waitForTimeout(800);
const valuesHref = await page.locator('a[href^="/applications/values/"]').first().getAttribute("href").catch(() => null);
const deployHref = await page.locator('a[href^="/applications/deploy/"]').first().getAttribute("href").catch(() => null);
if (valuesHref) {
  await captureDetailed("33-values-studio.png", valuesHref);
  await captureDetailed("34-values-ai-assistant.png", valuesHref, async () => {
    const aiButton = page.getByRole("button", { name: /AI.*제안|제안 생성/ }).first();
    if (await aiButton.count()) await aiButton.click();
  });
}
if (deployHref) {
  await captureDetailed("35-deployment-wizard.png", deployHref);
}

await captureDetailed("36-chart-source-form.png", "/applications/sources");
await captureDetailed("37-runbook-expanded.png", "/runbooks", async () => {
  const summary = page.locator(".runbook-library-summary").first();
  if (await summary.count()) await summary.click();
});
await captureDetailed("38-runbook-editor.png", "/runbooks", async () => {
  await page.getByRole("button", { name: /Runbook 작성/ }).click();
});
await captureDetailed("39-policy-settings.png", "/policies", async () => {
  await page.getByRole("button", { name: /정책 설정/ }).click();
});
await captureDetailed("40-policy-history.png", "/policies", async () => {
  await page.getByRole("button", { name: /변경 이력/ }).click();
});
await captureDetailed("41-ai-trust-evidence.png", "/ai/trust", async () => {
  const evidence = page.getByRole("heading", { name: /Release|릴리스|계약 평가/ }).first();
  if (await evidence.count()) await evidence.scrollIntoViewIfNeeded();
});
await captureDetailed("42-ai-provider-dialog.png", "/settings/ai-providers", async () => {
  await page.getByRole("button", { name: /Provider Profile/ }).click();
});
await captureDetailed("43-access-group-mapping.png", "/settings/users-access", async () => {
  await page.getByRole("button", { name: /OIDC Group Mapping/ }).click();
});
await captureDetailed("44-access-feature-policy.png", "/settings/users-access", async () => {
  await page.getByRole("button", { name: /메뉴 및 기능/ }).click();
});
await captureDetailed("45-user-invite-dialog.png", "/settings/users-access", async () => {
  await page.getByRole("button", { name: /사용자 초대/ }).click();
});
await captureDetailed("46-group-mapping-dialog.png", "/settings/users-access", async () => {
  await page.getByRole("button", { name: /Group Mapping 추가|Group Mapping/ }).first().click();
});
await captureDetailed("47-platform-accounts.png", "/settings/access");
await captureDetailed("48-source-add-dialog.png", "/applications/sources", async () => {
  await page.getByRole("button", { name: /Repository/ }).click();
});
await captureDetailed("49-chart-removal-dialog.png", "/applications/library", async () => {
  const removeButton = page.getByRole("button", { name: /Chart 제거/ }).first();
  if (await removeButton.count()) await removeButton.click();
});
await captureDetailed("50-discover-results.png", "/applications/discover", async () => {
  await page.getByRole("textbox", { name: /Chart 검색어/ }).fill("nginx");
  await page.getByRole("button", { name: /^검색$/ }).click();
  await page.locator(".chart-card, .delivery-empty").first().waitFor({ state: "visible", timeout: 20_000 });
});
const analyses = await page.evaluate(async () => {
  const response = await fetch("/api/analysis/history");
  return response.ok ? response.json() : [];
}).catch(() => []);
const guideAnalysis = Array.isArray(analyses) ? analyses.find((item) => item?.id && item?.clusterId) : null;
const guideAnalysisRoute = guideAnalysis
  ? `/analysis?clusterId=${encodeURIComponent(guideAnalysis.clusterId)}${guideAnalysis.namespace ? `&namespace=${encodeURIComponent(guideAnalysis.namespace)}` : ""}`
  : "/analysis";
await captureDetailed("51-analysis-detail.png", guideAnalysisRoute, async () => {
  const detailButton = page.getByRole("button", { name: /분석 결과 상세/ }).first();
  await detailButton.waitFor({ state: "visible", timeout: 10_000 });
  await detailButton.click();
});
await captureDetailed("52-analysis-expert-detail.png", guideAnalysisRoute, async () => {
  const detailButton = page.getByRole("button", { name: /분석 결과 상세/ }).first();
  await detailButton.waitFor({ state: "visible", timeout: 10_000 });
  await detailButton.click();
  await page.getByRole("button", { name: /숙련자용/ }).click();
});
if (guideCluster) {
  await captureDetailed("53-cluster-readiness-detail.png", `/clusters/${guideCluster.id}`, async () => {
    const readiness = page.getByRole("heading", { name: /클러스터 준비도|Cluster Readiness/ }).first();
    if (await readiness.count()) await readiness.scrollIntoViewIfNeeded();
  });
}
await captureDetailed("54-group-mapping-dialog.png", "/settings/users-access", async () => {
  await page.getByRole("button", { name: /OIDC Group Mapping/ }).click();
  const addButton = page.locator("button.primary-button").filter({ hasText: /Group Mapping/ }).first();
  if (await addButton.count()) await addButton.click();
});

await browser.close();
console.log(`Captured ${screens.length} overview screens and ${detailedScreens.length} detailed screens in ${outputDirectory}`);
