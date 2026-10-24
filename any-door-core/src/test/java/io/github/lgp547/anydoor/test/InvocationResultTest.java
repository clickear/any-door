package io.github.lgp547.anydoor.test;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.core.AnyDoorService;
import io.github.lgp547.anydoor.util.JsonUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class InvocationResultTest {
    @Test public void serializesStringsNullAndObjectsWithoutLosingTheirType() {
        assertEquals("\"text\"", JsonUtil.toJson("text"));
        assertEquals("null", JsonUtil.toJson(null));
        assertEquals("[1,\"two\"]", JsonUtil.toJson(Arrays.asList(1, "two")));
    }

    @Test public void synchronousBatchReturnsAllValuesInInputOrder() {
        AnyDoorRunDto dto = request("[{\"value\":\"one\"},{\"value\":\"two\"}]");
        dto.setConcurrent(false);
        Object result = new AnyDoorService().run(dto);
        assertEquals(Arrays.asList("one", "two"), result);
    }

    @Test public void concurrentBatchReturnsAllValuesInInputOrder() {
        AnyDoorRunDto dto = request("[{\"value\":\"one\"},{\"value\":\"two\"}]");
        dto.setConcurrent(true);
        Object result = new AnyDoorService().run(dto);
        assertEquals(Arrays.asList("one", "two"), result);
    }

    @Test public void asyncResultCompletesWithBusinessValue() throws Exception {
        AnyDoorRunDto dto = request("{\"value\":\"async\"}");
        dto.setSync(false);
        assertEquals("async", new AnyDoorService().runForResult(dto).get(3, TimeUnit.SECONDS));
    }

    @Test public void asyncResultCompletesExceptionally() throws Exception {
        AnyDoorRunDto dto = request("{\"value\":\"explode\"}");
        dto.setMethodName("fail");
        dto.setSync(false);
        try {
            new AnyDoorService().runForResult(dto).get(3, TimeUnit.SECONDS);
            fail("Expected invocation failure");
        } catch (ExecutionException expected) {
            Throwable cause = expected;
            while (cause.getCause() != null) cause = cause.getCause();
            assertTrue(cause.getMessage().contains("boom"));
        }
    }

    private static AnyDoorRunDto request(String content) {
        AnyDoorRunDto dto = new AnyDoorRunDto();
        dto.setClassName(ResultTarget.class.getName());
        dto.setMethodName("echo");
        dto.setParameterTypes(Arrays.asList(String.class.getName()));
        dto.setContent(content);
        dto.setSync(true);
        return dto;
    }

    public static class ResultTarget {
        public String echo(String value) { return value; }
        public String fail(String value) { throw new IllegalStateException("boom: " + value); }
    }
}
