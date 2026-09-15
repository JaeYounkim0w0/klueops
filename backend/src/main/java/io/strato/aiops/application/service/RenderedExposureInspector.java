package io.strato.aiops.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helm 렌더 결과에 Chart가 직접 관리하는 노출 리소스가 있는지 검사한다.
 */
final class RenderedExposureInspector {
    private static final Pattern ROUTABLE_KIND = Pattern.compile(
            "(?m)^kind:\\s*(Ingress|HTTPRoute)\\s*(?:#.*)?$");

    private RenderedExposureInspector() { }

    static Detection detect(String manifest) {
        boolean ingress = false;
        boolean httpRoute = false;
        Matcher matcher = ROUTABLE_KIND.matcher(manifest == null ? "" : manifest);
        while (matcher.find()) {
            ingress |= "Ingress".equals(matcher.group(1));
            httpRoute |= "HTTPRoute".equals(matcher.group(1));
        }
        return new Detection(ingress, httpRoute);
    }

    static void requireChartManagedExposure(String manifest) {
        if (!detect(manifest).present()) {
            throw new IllegalArgumentException(
                    "Chart-managed exposure requires the rendered Chart to contain an Ingress or HTTPRoute");
        }
    }

    record Detection(boolean ingress, boolean httpRoute) {
        boolean present() { return ingress || httpRoute; }
    }
}
