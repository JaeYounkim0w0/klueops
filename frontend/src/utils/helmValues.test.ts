import { describe, expect, it } from "vitest";
import { reviewNodePortValues } from "./helmValues";

describe("reviewNodePortValues", () => {
  it("rejects a nodePort outside the Kubernetes default range", () => {
    const result = reviewNodePortValues({
      service: { type: "NodePort", nodePort: 35432 },
    });

    expect(result.errors).toEqual([
      "$.service.nodePort 35432은 Kubernetes 기본 허용 범위 30000-32767 밖입니다.",
    ]);
    expect(result.warnings).toEqual([]);
  });

  it("warns when nodePort is configured without a NodePort service type", () => {
    const result = reviewNodePortValues({
      service: { type: "ClusterIP", ports: { postgresql: { nodePort: 30432 } } },
    });

    expect(result.errors).toEqual([]);
    expect(result.warnings[0]).toContain("type은 ClusterIP");
    expect(result.warnings[0]).toContain("type: NodePort");
  });

  it("accepts a valid nested nodePort with an inherited service type", () => {
    const result = reviewNodePortValues({
      service: { type: "NodePort", ports: { postgresql: { nodePort: 30432 } } },
    });

    expect(result).toEqual({ errors: [], warnings: [] });
  });

  it("rejects a Service type stored in clusterIP", () => {
    const result = reviewNodePortValues({
      server: { service: { clusterIP: "NodePort", type: "NodePort" } },
    });

    expect(result.errors[0]).toContain("$.server.service.clusterIP");
    expect(result.errors[0]).toContain("nodePort 필드");
  });

  it("accepts empty, headless, IPv4, and IPv6 clusterIP values", () => {
    expect(reviewNodePortValues({ service: { clusterIP: "" } }).errors).toEqual([]);
    expect(reviewNodePortValues({ service: { clusterIP: "None" } }).errors).toEqual([]);
    expect(reviewNodePortValues({ service: { clusterIP: "10.96.0.10" } }).errors).toEqual([]);
    expect(reviewNodePortValues({ service: { clusterIP: "fd00::10" } }).errors).toEqual([]);
  });
});
