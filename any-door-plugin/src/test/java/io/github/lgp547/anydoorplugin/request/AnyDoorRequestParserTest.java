package io.github.lgp547.anydoorplugin.request;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnyDoorRequestParserTest {
    @Test void parsesTargetAndJsonBody() {
        AnyDoorRequest request = AnyDoorRequestParser.parse("### create order\n@pid = 1234\nRUN demo.OrderService#createOrder(CreateRequest)\n{\"id\":1}");
        assertEquals("create order", request.name()); assertEquals(1234L, request.pid());
        assertEquals("demo.OrderService", request.className()); assertEquals("createOrder", request.methodName()); assertEquals("{\"id\":1}", request.json());
    }
    @Test void parsesIndependentBlocksAndDirectives() {
        var requests = AnyDoorRequestParser.parseAll("### @name one\n@timeout = 5s\nRUN demo.A#one\n{}\n### @name two\n@mode = dry-run\nRUN demo.B#two\n{}");
        assertEquals(2, requests.size()); assertEquals(5, requests.get(0).timeoutSeconds()); assertEquals("dry-run", requests.get(1).mode());
    }
    @Test void keepsBeanSelectionLocalToEachRequestBlock() {
        var requests = AnyDoorRequestParser.parseAll("### one\n@beanName = secondaryService\n@beanContextId = abc\n@classLoaderId = loader\nRUN demo.A#one\n{}\n### two\nRUN demo.A#two\n{}");
        assertEquals("secondaryService", requests.get(0).beanName());
        assertEquals("abc", requests.get(0).beanContextId());
        assertEquals("loader", requests.get(0).classLoaderId());
        assertEquals(null, requests.get(1).beanName());
        assertEquals(null, requests.get(1).beanContextId());
        assertEquals(null, requests.get(1).classLoaderId());
    }
}
