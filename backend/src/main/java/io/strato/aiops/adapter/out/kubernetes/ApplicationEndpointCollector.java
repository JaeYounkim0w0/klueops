package io.strato.aiops.adapter.out.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.strato.aiops.application.port.out.ApplicationRuntimeInspectionPort.Endpoint;

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

    private ApplicationEndpointCollector() { }

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

    private static boolean ingressReady(Ingress ingress) {
        return ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null
                && ingress.getStatus().getLoadBalancer().getIngress() != null
                && !ingress.getStatus().getLoadBalancer().getIngress().isEmpty();
    }

    private static Set<String> tlsHosts(List<IngressTLS> tlsEntries) {
        Set<String> hosts = new LinkedHashSet<>();
        if (tlsEntries != null) tlsEntries.forEach(entry -> {
            if (entry.getHosts() != null) hosts.addAll(entry.getHosts());
        });
        return hosts;
    }

    private static boolean belongsToRelease(Map<String, String> labels, Map<String, String> annotations,
                                            String releaseName) {
        return releaseName.equals(value(labels, INSTANCE_LABEL)) || releaseName.equals(value(annotations, RELEASE_ANNOTATION));
    }

    private static String annotation(GenericKubernetesResource route, String name, String fallback) {
        String value = value(route.getMetadata().getAnnotations(), name);
        return "https".equalsIgnoreCase(value) ? "https" : fallback;
    }

    private static String value(Map<String, String> source, String name) {
        return source == null ? null : source.get(name);
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> items ? items : List.of();
    }

    private static String url(String scheme, String host, String path) {
        return scheme + "://" + host + normalizedPath(path);
    }

    private static String normalizedPath(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.startsWith("/") ? path : "/" + path;
    }

    private static void append(List<Endpoint> endpoints, String type, String name, String url, String status) {
        if (endpoints.stream().noneMatch(item -> item.type().equals(type) && item.url().equals(url))) {
            endpoints.add(new Endpoint(type, name, url, status));
        }
    }
}
