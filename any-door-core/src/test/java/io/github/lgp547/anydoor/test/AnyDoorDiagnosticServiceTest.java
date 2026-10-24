package io.github.lgp547.anydoor.test;

import io.github.lgp547.anydoor.common.dto.AnyDoorDiagnosticReport;
import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.core.AnyDoorDiagnosticService;
import io.github.lgp547.anydoor.mvc.AnyDoorController;
import io.github.lgp547.anydoor.util.JsonUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AnyDoorDiagnosticServiceTest {
    @Before public void reset() { Target.constructions = 0; Target.invocations = 0; }

    @Test public void diagnosticNeverConstructsOrInvokesBusinessType() {
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticService().diagnose(request(Target.class, "call", Collections.singletonMap("args0", "value")));
        assertEquals(0, Target.constructions);
        assertEquals(0, Target.invocations);
        assertEquals("SKIP", check(report, "execution.safe").getStatus());
        assertEquals("1", report.getProtocolVersion());
    }

    @Test public void missingClassDoesNotPreventIndependentStages() {
        AnyDoorRunDto request = request(Target.class, "call", Collections.emptyMap());
        request.setClassName("missing.Type");
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticService().diagnose(request);
        assertEquals("FAIL", check(report, "class.loaded").getStatus());
        assertEquals("SKIP", check(report, "method.skipped").getStatus());
        assertNotNull(check(report, "agent.protocol"));
    }

    @Test public void reportsOverloadsAndRequiresExactSignature() {
        AnyDoorRunDto request = request(Overloaded.class, "run", Collections.singletonMap("args0", 1));
        request.setParameterTypes(null);
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticService().diagnose(request);
        assertTrue(check(report, "method.candidates").getSummary().startsWith("2 candidate"));
        assertEquals("FAIL", check(report, "method.selected").getStatus());
    }

    @Test public void validatesPositionalBindingAndMissingPrimitive() {
        AnyDoorRunDto request = request(Target.class, "primitive", Collections.emptyMap());
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticService().diagnose(request);
        assertTrue(check(report, "parameters.binding").getSummary().contains("argsN"));
        assertEquals("FAIL", check(report, "parameters.0").getStatus());
    }

    @Test public void reportsMultipleAndStaleSpringSelectionsWithoutResolvingBean() {
        AnyDoorRunDto request = request(Target.class, "call", Collections.singletonMap("args0", "value"));
        request.setBeanName("deleted"); request.setBeanContextId("stale");
        List<Map<String,String>> beans = new ArrayList<>();
        beans.add(bean("one","ctx-1")); beans.add(bean("two","ctx-2"));
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticService().diagnose(request,
                Collections.<Class<?>>singletonList(Target.class), beans, "test", null);
        assertEquals("FAIL", check(report, "spring.selection").getStatus());
        assertEquals(0, Target.constructions);
    }

    @Test public void reportJsonContainsStableProtocolAndNoRequestValues() {
        AnyDoorRunDto request = request(Target.class, "call", Collections.singletonMap("args0", "password=top-secret"));
        String json = JsonUtil.toJson(new AnyDoorDiagnosticService().diagnose(request));
        assertTrue(json.contains("\"protocolVersion\":\"1\""));
        assertFalse(json.contains("top-secret"));
    }

    @Test public void httpControllerAcceptsNonExecutableDiagnosticSignature() {
        AnyDoorRunDto request = request(Target.class, "call", Collections.singletonMap("args0", "value"));
        request.setOperation("diagnose"); request.setDiagnosticMethodName("call"); request.setDiagnosticParameterTypes(request.getParameterTypes());
        request.setMethodName(null); request.setParameterTypes(null);
        AnyDoorDiagnosticReport report = new AnyDoorController().diagnose(request);
        assertEquals("PASS", check(report, "method.selected").getStatus());
        assertEquals(0, Target.constructions); assertEquals(0, Target.invocations);
    }

    @Test public void springInspectionFailureDoesNotStopMethodAndParameterChecks() {
        AnyDoorRunDto request = request(Target.class, "call", Collections.singletonMap("args0", "value"));
        AnyDoorDiagnosticReport report = new AnyDoorDiagnosticService().diagnose(request,
                Collections.<Class<?>>singletonList(Target.class), Collections.<Map<String,String>>emptyList(), "test", null,
                new IllegalStateException("inactive context"));
        assertEquals("FAIL", check(report, "spring.beans").getStatus());
        assertEquals("PASS", check(report, "method.selected").getStatus());
        assertNotNull(check(report, "parameters.binding"));
    }

    private static AnyDoorRunDto request(Class<?> type, String method, Object content) {
        AnyDoorRunDto request = new AnyDoorRunDto(); request.setClassName(type.getName()); request.setMethodName(method);
        request.setContent(content); request.setSync(true); request.setConcurrent(false);
        if ("primitive".equals(method)) request.setParameterTypes(Collections.singletonList("int"));
        else request.setParameterTypes(Collections.singletonList("java.lang.String"));
        return request;
    }
    private static Map<String,String> bean(String name,String context) { Map<String,String> bean=new HashMap<>();bean.put("beanName",name);bean.put("beanContextId",context);bean.put("contextName",context);bean.put("className",Target.class.getName());bean.put("proxyType","none");bean.put("classLoaderId","loader");return bean; }
    private static AnyDoorDiagnosticReport.Check check(AnyDoorDiagnosticReport report,String id){for(AnyDoorDiagnosticReport.Stage stage:report.getStages())for(AnyDoorDiagnosticReport.Check check:stage.getChecks())if(id.equals(check.getId()))return check;fail("Missing check "+id);return null;}

    public static class Target {
        static int constructions,invocations;
        public Target(){constructions++;}
        public String call(String value){invocations++;return value;}
        public int primitive(int value){invocations++;return value;}
    }
    public static class Overloaded { public String run(String value){return value;} public int run(int value){return value;} }
}
