import { chromium } from "../../../../frontend/node_modules/playwright/index.mjs";
import assert from "node:assert/strict";

const base = process.env.KLUEOPS_MOCKUP_URL || "http://127.0.0.1:4173";
const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));

await page.goto(`${base}/?screen=applications`);
assert.equal(await page.locator("button:not([data-interactive])").count(), 0, "a static button has no interaction contract");
await page.getByRole("button", { name: "Application 배포" }).click();
await page.getByRole("button", { name: /Chart Library에서 선택/ }).click();
assert.match(page.url(), /screen=library/);

await page.goto(`${base}/?screen=application-detail`);
await page.getByRole("tab", { name: "History" }).click();
assert.match(page.url(), /tab=history/);
assert.equal(await page.getByRole("heading", { name: "Operation History" }).isVisible(), true);
await page.getByRole("button", { name: "Uninstall 계획" }).first().click();
await page.getByRole("button", { name: "Exact confirmation으로" }).click();
const uninstall = page.getByRole("button", { name: "Uninstall 실행" });
assert.equal(await uninstall.isDisabled(), true);
await page.getByRole("textbox", { name: /계속하려면/ }).fill("payments-web/payments");
assert.equal(await uninstall.isEnabled(), true);

await page.goto(`${base}/?screen=sources`);
await page.getByRole("button", { name: "＋ Source 등록" }).click();
await page.getByRole("button", { name: "연결 검사" }).click();
assert.equal(await page.getByRole("button", { name: "Tenant Source 저장" }).isVisible(), true);

await page.goto(`${base}/?screen=ai-settings`);
assert.equal((await page.locator(".routing-card").innerText()).includes("후보⌄"), false);
await page.getByRole("button", { name: "＋ Provider Profile" }).click();
for (let step = 0; step < 3; step += 1) await page.getByRole("button", { name: "다음" }).click();
const saveProvider = page.getByRole("button", { name: "Profile 저장" });
assert.equal(await saveProvider.isDisabled(), true);
await page.getByRole("checkbox").check();
assert.equal(await saveProvider.isEnabled(), true);

for (const width of [900, 390]) {
  await page.setViewportSize({ width, height: 900 });
  await page.goto(`${base}/?screen=applications`);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  assert.ok(overflow <= 1, `${width}px viewport has ${overflow}px horizontal overflow`);
}

assert.deepEqual(errors, []);
await browser.close();
console.log("Phase 2 mockup interaction and responsive smoke tests passed.");
