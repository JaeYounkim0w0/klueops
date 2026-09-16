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
});
