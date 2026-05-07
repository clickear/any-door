package io.github.lgp547.anydoor.core;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnyDoorExpressionModeTest {

    @Test
    void evaluatesCurrentValueForNestedField() throws Exception {
        Path tempDir = Files.createTempDirectory("any-door-expression-test");
        Path resultFile = tempDir.resolve("expression-result.json");

        AnyDoorRunDto dto = new AnyDoorRunDto();
        dto.setClassName(ExpressionTestBean.class.getName());
        dto.setMethodName("updateCity");
        dto.setContent("{\"user\":{\"name\":\"Alice\",\"address\":{\"city\":\"Beijing\"}}}");
        dto.setParameterTypes(Collections.singletonList(ExpressionTestUser.class.getName()));
        dto.setSync(true);
        dto.setProjectBasePath(tempDir.toString());
        dto.setExpressionMode(true);
        dto.setExpression("currentValue + \"-verified\"");
        dto.setExpressionResultPath(resultFile.toString());
        dto.setExpressionRootParamName("user");
        dto.setExpressionJsonPath("user.address.city");

        Object result = new AnyDoorService().run(dto);

        assertEquals("\"Beijing-verified\"", result);
        assertEquals("{\"success\":true,\"value\":\"Beijing-verified\"}", new String(Files.readAllBytes(resultFile), StandardCharsets.UTF_8));
    }

    @Test
    void exposesArgsByNameAndJsonPath() throws Exception {
        Path tempDir = Files.createTempDirectory("any-door-expression-test");
        Path resultFile = tempDir.resolve("expression-result.json");

        AnyDoorRunDto dto = new AnyDoorRunDto();
        dto.setClassName(ExpressionTestBean.class.getName());
        dto.setMethodName("updateCity");
        dto.setContent("{\"user\":{\"name\":\"Alice\",\"address\":{\"city\":\"Beijing\"}}}");
        dto.setParameterTypes(Collections.singletonList(ExpressionTestUser.class.getName()));
        dto.setSync(true);
        dto.setProjectBasePath(tempDir.toString());
        dto.setExpressionMode(true);
        dto.setExpression("((ExpressionTestUser) argsByName.get(\"user\")).getName() + \":\" + jsonPath");
        dto.setExpressionResultPath(resultFile.toString());
        dto.setExpressionRootParamName("user");
        dto.setExpressionJsonPath("user.address.city");

        Object result = new AnyDoorService().run(dto);

        assertEquals("\"Alice:user.address.city\"", result);
        assertTrue(Files.exists(resultFile));
    }
}
