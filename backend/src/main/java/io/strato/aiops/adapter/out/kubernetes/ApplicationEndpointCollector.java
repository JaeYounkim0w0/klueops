package io.strato.aiops.adapter.out.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.strato.aiops.application.port.out.ApplicationRuntimeInspectionPort.Endpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helm release가 소유한 Ingress와 HTTPRoute를 사용자 접근 URL로 정규화한다.
 */
final class ApplicationEndpointCollector {
    private static final String RELEASE_ANNOTATION = "meta.helm.sh/release-name";
    private static final String INSTANCE_LABEL = "app.kubernetes.io/instance";
    private static final String SCHEME_ANNOTATION = "klueops.io/url-scheme";

    /** ApplicationEndpointCollector 인스턴스를 필요한 의존성과 초기 상태로 구성한다. */
    private ApplicationEndpointCollector() { }

    /** ApplicationEndpointCollector의 ingressEndpoints 처리에 필요한 업무 로직을 수행한다. */
    static List<Endpoint> ingressEndpoints(Collection<Ingress> ingresses, String releaseName) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (Ingress ingress : ingresses) {
            if (!belongsToRelease(ingress.getMetadata().getLabels(), ingress.getMetadata().getAnnotations(), releaseName)
                    || ingress.getSpec() == null || ingress.getSpec().getRules() == null) continue;
            Set<String> tlsHosts = tlsHosts(ingress.getSpec().getTls());
            String status = ingressReady(ingress) ? "READY" : "APPLIED";
            for (IngressRule rule : ingress.getSpec().getRules()) {
                if (rule.getHost() == null || rule.getHost().isBlank()) continue;
                List<HTTPIngressPath> paths = rule.getHttp() == null || rule.getHttp().getPaths() == null
                        ? List.of() : rule.getHttp().getPaths();
                if (paths.isEmpty()) append(endpoints, "INGRESS", ingress.getMetadata().getName(),
                        url(tlsHosts.contains(rule.getHost()) ? "https" : "http", rule.getHost(), "/"), status);
                paths.forEach(path -> append(endpoints, "INGRESS", ingress.getMetadata().getName(),
                        url(tlsHosts.contains(rule.getHost()) ? "https" : "http", rule.getHost(), path.getPath()), status));
            }
        }
        return List.copyOf(endpoints);
    }

    /** ApplicationEndpointCollector의 httpRouteEndpoints 처리에 필요한 업무 로직을 수행한다. */
    static List<Endpoint> httpRouteEndpoints(Collection<GenericKubernetesResource> routes, String releaseName) {
        List<Endpoint> endpoints = new ArrayList<>();
        for (GenericKubernetesResource route : routes) {
            if (!belongsToRelease(route.getMetadata().getLabels(), route.getMetadata().getAnnotations(), releaseName)) continue;
            String scheme = annotation(route, SCHEME_ANNOTATION, "http");
            String status = routeStatus(route);
            List<?> hosts = list(route.get("spec", "hostnames"));
            List<String> paths = routePaths(route);
            for (Object host : hosts) {
                if (!(host instanceof String hostname) || hostname.isBlank()) continue;
                for (String path : paths) append(endpoints, "HTTP_ROUTE", route.getMetadata().getName(),
                        url(scheme, hostname, path), status);
            }
        }
        return List.copyOf(endpoints);
    }

    /** ApplicationEndpointCollector의 routePaths 처리에 필요한 업무 로직을 수행한다. */
    private static List<String> routePaths(GenericKubernetesResource route) {
        Set<String> paths = new LinkedHashSet<>();
        for (Object ruleValue : list(route.get("spec", "rules"))) {
            if (!(ruleValue instanceof Map<?, ?> rule)) continue;
            for (Object matchValue : list(rule.get("matches"))) {
                if (!(matchValue instanceof Map<?, ?> match) || !(match.get("path") instanceof Map<?, ?> path)) continue;
                Object value = path.get("value");
                if (value instanceof String text) paths.add(normalizedPath(text));
            }
        }
        return paths.isEmpty() ? List.of("/") : List.copyOf(paths);
    }

    /** ApplicationEndpointCollector의 routeStatus 처리에 필요한 업무 로직을 수행한다. */
    private static String routeStatus(GenericKubernetesResource route) {
        boolean accepted = false;
        boolean resolved = false;
        boolean rejected = false;
        for (Object parentValue : list(route.get("status", "parents"))) {
            if (!(parentValue instanceof Map<?, ?> parent)) continue;
            for (Object conditionValue : list(parent.get("conditions"))) {
                if (!(conditionValue instanceof Map<?, ?> condition)) continue;
                String type = String.valueOf(condition.get("type"));
                String value = String.valueOf(condition.get("status"));
                if ("Accepted".equals(type)) accepted |= "True".equals(value);
                if ("ResolvedRefs".equals(type)) resolved |= "True".equals(value);
                if (("Accepted".equals(type) || "ResolvedRefs".equals(type)) && "False".equals(value)) rejected = true;
            }
        }
        if (accepted && resolved) return "READY";
        return rejected ? "DEGRADED" : "APPLIED";
    }

    /** ApplicationEndpointCollector의 ingressReady 처리에 필요한 업무 로직을 수행한다. */
    private static boolean ingressReady(Ingress ingress) {
        return ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null
                && ingress.getStatus().getLoadBalancer().getIngress() != null
                && !ingress.getStatus().getLoadBalancer().getIngress().isEmpty();
    }

    /** ApplicationEndpointCollector의 tlsHosts 처리에 필요한 업무 로직을 수행한다. */
    private static Set<String> tlsHosts(List<IngressTLS> tlsEntries) {
        Set<String> hosts = new LinkedHashSet<>();
        if (tlsEntries != null) tlsEntries.forEach(entry -> {
            if (entry.getHosts() != null) hosts.addAll(entry.getHosts());
        });
        return hosts;
    }

    /** ApplicationEndpointCollector의 belongsToRelease 처리에 필요한 업무 로직을 수행한다. */
    private static boolean belongsToRelease(Map<String, String> labels, Map<String, String> annotations,
                                            String releaseName) {
        return releaseName.equals(value(labels, INSTANCE_LABEL)) || releaseName.equals(value(annotations, RELEASE_ANNOTATION));
    }

    /** ApplicationEndpointCollector의 annotation 처리에 필요한 업무 로직을 수행한다. */
    private static String annotation(GenericKubernetesResource route, String name, String fallback) {
        String value = value(route.getMetadata().getAnnotations(), name);
        return "https".equalsIgnoreCase(value) ? "https" : fallback;
    }

    /** ApplicationEndpointCollector의 value 처리에 필요한 업무 로직을 수행한다. */
    private static String value(Map<String, String> source, String name) {
        return source == null ? null : source.get(name);
    }

    /** ApplicationEndpointCollector의 list 처리 결과를 조회해 반환한다. */
    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    /** ApplicationEndpointCollector의 url 처리에 필요한 업무 로직을 수행한다. */
    private static String url(String scheme, String host, String path) {
        return scheme + "://" + host + normalizedPath(path);
    }

    /** ApplicationEndpointCollector의 normalizedPath 처리 데이터를 필요한 표현으로 변환한다. */
    private static String normalizedPath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    /** ApplicationEndpointCollector의 append 처리에 필요한 업무 로직을 수행한다. */
    private static void append(List<Endpoint> endpoints, String type, String name, String url, String status) {
        if (endpoints.stream().noneMatch(item -> item.type().equals(type) && item.url().equals(url))) {
            try {
                URI parsed = new URI(url);
                int port = parsed.getPort() > 0 ? parsed.getPort() : "https".equals(parsed.getScheme()) ? 443 : 80;
                endpoints.add(new Endpoint(type, name, url, status, parsed.getHost(), port,
                        null, null, "EXTERNAL_DOMAIN"));
            } catch (URISyntaxException exception) {
                // 비표준 hostname은 URL 원문을 유지하고 구조화 주소만 비워 사용자 화면을 중단하지 않는다.
                endpoints.add(new Endpoint(type, name, url, status));
            }
        }
    }
}
