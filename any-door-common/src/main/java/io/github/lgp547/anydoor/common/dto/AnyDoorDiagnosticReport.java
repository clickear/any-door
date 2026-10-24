package io.github.lgp547.anydoor.common.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned, transport-neutral diagnostic protocol shared by HTTP, attach and the IDE. */
public class AnyDoorDiagnosticReport {
    public static final String PROTOCOL_VERSION = "1";
    private String protocolVersion = PROTOCOL_VERSION;
    private String invocationId;
    private long timestamp = System.currentTimeMillis();
    private String target;
    private String overallStatus = "PASS";
    private List<Stage> stages = new ArrayList<>();
    private Map<String, Long> timings = new LinkedHashMap<>();
    private String sanitizedException;

    public String getProtocolVersion() { return protocolVersion; }
    public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
    public String getInvocationId() { return invocationId; }
    public void setInvocationId(String invocationId) { this.invocationId = invocationId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getOverallStatus() { return overallStatus; }
    public void setOverallStatus(String overallStatus) { this.overallStatus = overallStatus; }
    public List<Stage> getStages() { return stages; }
    public void setStages(List<Stage> stages) { this.stages = stages; }
    public Map<String, Long> getTimings() { return timings; }
    public void setTimings(Map<String, Long> timings) { this.timings = timings; }
    public String getSanitizedException() { return sanitizedException; }
    public void setSanitizedException(String sanitizedException) { this.sanitizedException = sanitizedException; }

    public void addStage(Stage stage) {
        stages.add(stage);
        timings.put(stage.getId(), stage.getDurationMillis());
        overallStatus = worse(overallStatus, stage.getStatus());
    }

    public static String worse(String left, String right) {
        return rank(right) > rank(left) ? right : left;
    }

    private static int rank(String value) {
        if ("FAIL".equals(value)) return 3;
        if ("WARN".equals(value)) return 2;
        if ("RUNNING".equals(value)) return 1;
        return 0;
    }

    public static class Stage {
        private String id;
        private String name;
        private String status = "PASS";
        private long durationMillis;
        private List<Check> checks = new ArrayList<>();
        public Stage() { }
        public Stage(String id, String name) { this.id = id; this.name = name; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getDurationMillis() { return durationMillis; }
        public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
        public List<Check> getChecks() { return checks; }
        public void setChecks(List<Check> checks) { this.checks = checks; }
        public void add(Check check) { checks.add(check); status = AnyDoorDiagnosticReport.worse(status, check.getStatus()); }
    }

    public static class Check {
        private String id;
        private String title;
        private String status;
        private String summary;
        private List<String> evidence = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();
        private long durationMillis;
        public Check() { }
        public Check(String id, String title, String status, String summary) {
            this.id = id; this.title = title; this.status = status; this.summary = summary;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<String> getEvidence() { return evidence; }
        public void setEvidence(List<String> evidence) { this.evidence = evidence; }
        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
        public long getDurationMillis() { return durationMillis; }
        public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }
        public Check evidence(String value) { evidence.add(value); return this; }
        public Check suggest(String value) { suggestions.add(value); return this; }
    }
}
