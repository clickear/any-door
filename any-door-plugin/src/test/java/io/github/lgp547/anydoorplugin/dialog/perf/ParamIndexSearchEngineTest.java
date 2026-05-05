package io.github.lgp547.anydoorplugin.dialog.perf;

import io.github.lgp547.anydoorplugin.data.domain.ParamIndexData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamIndexSearchEngineTest {

    @Test
    void returnsEmptyWhenKeywordIsBlank() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(sample("SaveUser", "demo.UserService#save(java.lang.String)")));

        assertTrue(engine.search("", 20).isEmpty());
    }

    @Test
    void matchesNameAndQualifiedMethodCaseInsensitively() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(
                sample("SaveUser", "demo.UserService#save(java.lang.String)"),
                sample("DeleteUser", "demo.UserService#delete(java.lang.Long)")
        ));

        assertEquals(1, engine.search("saveuser", 20).size());
        assertEquals(1, engine.search("DELETE(JAVA.LANG.LONG)", 20).size());
    }

    @Test
    void keepsOriginalInsertionOrderWhenFiltering() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(
                sample("A", "demo.S#run1()"),
                sample("B", "demo.S#run2()"),
                sample("C", "demo.S#run3()")
        ));

        List<ParamIndexData> result = engine.search("run", 3);

        assertEquals("A", result.get(0).getName());
        assertEquals("B", result.get(1).getName());
        assertEquals("C", result.get(2).getName());
    }

    @Test
    void respectsResultLimit() {
        ParamIndexSearchEngine engine = new ParamIndexSearchEngine();
        engine.rebuild(List.of(
                sample("A1", "demo.S#run1()"),
                sample("A2", "demo.S#run2()"),
                sample("A3", "demo.S#run3()")
        ));

        assertEquals(2, engine.search("run", 2).size());
    }

    private static ParamIndexData sample(String name, String qualifiedMethodName) {
        ParamIndexData data = new ParamIndexData();
        data.setName(name);
        data.setQualifiedMethodName(qualifiedMethodName);
        return data;
    }
}
