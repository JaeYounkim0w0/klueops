import { chromium } from "../../../../frontend/node_modules/playwright/index.mjs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.dirname(fileURLToPath(import.meta.url));
const output = path.join(root, "screenshots");
const base = process.env.KLUEOPS_MOCKUP_URL || "http://127.0.0.1:4173";
const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });

async function capture(file, screen, action) {
  await page.goto(`${base}/?screen=${screen}`);
  if (action) await action(page);
  await page.screenshot({ path: path.join(output, file), fullPage: true });
}

await capture("01-discover.png", "discover");
await capture("02-library.png", "library");
await capture("03-values-studio.png", "values");
await capture("04-deployment-preview.png", "preview");
await capture("05-applications.png", "applications");
await capture("06-ai-provider-settings.png", "ai-settings");
await capture("07-import-confirmation.png", "discover", async (p) => {
  await p.getByLabel("Helm Chart 검색").fill("nginx");
  await p.getByRole("button", { name: "Artifact Hub 검색" }).click();
  await p.getByRole("button", { name: "Tenant Library로 가져오기" }).click();
});
await capture("08-deploy-exact-confirmation.png", "preview", (p) => p.getByRole("button", { name: "확정 단계로" }).click());
await capture("09-rollback-confirmation.png", "applications", (p) => p.getByRole("button", { name: "Rollback" }).click());
await capture("10-provider-profile-modal.png", "ai-settings", (p) => p.getByRole("button", { name: "＋ Provider Profile" }).click());
await capture("11-sources.png", "sources");
await capture("12-exposure.png", "exposure");
await capture("13-application-detail.png", "application-detail");
await capture("14-local-models.png", "models");
await capture("15-uninstall-plan.png", "application-detail", (p) => p.getByRole("button", { name: "Uninstall 계획" }).first().click());
await capture("16-local-model-add.png", "models", (p) => p.getByRole("button", { name: "＋ Local Model 추가" }).click());
await capture("17-deployment-start.png", "applications", (p) => p.getByRole("button", { name: "Application 배포" }).click());
await capture("18-required-states.png", "states");

await page.setViewportSize({ width: 390, height: 844 });
await capture("19-applications-mobile.png", "applications");
await page.setViewportSize({ width: 900, height: 900 });
await capture("20-applications-tablet.png", "applications");
await page.setViewportSize({ width: 1440, height: 1000 });
await capture("21-users-access.png", "access-control");
await capture("22-user-invite.png", "access-control", (p) => p.getByRole("button", { name: "＋ User 추가" }).click());
await capture("23-user-offboard-plan.png", "access-control", (p) => p.getByRole("button", { name: "접근 중지 계획" }).click());
await capture("24-oidc-group-mapping.png", "access-control", (p) => p.getByRole("button", { name: "＋ OIDC Group Mapping" }).click());
await browser.close();
