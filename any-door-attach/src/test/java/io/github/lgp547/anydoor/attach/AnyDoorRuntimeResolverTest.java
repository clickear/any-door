package io.github.lgp547.anydoor.attach;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorClassUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.support.GenericApplicationContext;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AnyDoorRuntimeResolverTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void duplicateClassNamesRequireAnExplicitDefiningLoader() throws Exception {
        Path root = compile("public class SharedType { }");
        try (URLClassLoader first = loader(root); URLClassLoader second = loader(root)) {
            Class<?> one = Class.forName("SharedType", false, first);
            Class<?> two = Class.forName("SharedType", false, second);
            assertNotSame(one, two);
            AnyDoorRuntimeResolver resolver = new AnyDoorRuntimeResolver(new Class<?>[]{one, two}, new Object[0], new Object[0]);
            assertEquals(2, resolver.findTypes("SharedType", null).size());
            assertSame(two, resolver.findTypes("SharedType", AnyDoorClassUtil.classLoaderId(second)).get(0));
            try {
                resolver.listBeans(request(null, null));
                fail("Ambiguous non-Spring classes must not select the first definition");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("specify classLoaderId"));
            }
            try {
                resolver.findTypes("SharedType", "stale-loader");
                fail("A stale selection must not fall back");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Application class not found"));
            }
        }
    }

    @Test
    public void beansWithIdenticalNamesAndBinaryClassNamesAreResolvedInTheirOwnLoader() throws Exception {
        Path root = compile("public class SharedType { }");
        try (URLClassLoader first = loader(root); URLClassLoader second = loader(root);
             GenericApplicationContext firstContext = new GenericApplicationContext();
             GenericApplicationContext secondContext = new GenericApplicationContext()) {
            Class<?> one = Class.forName("SharedType", false, first);
            Class<?> two = Class.forName("SharedType", false, second);
            Object firstBean = one.newInstance(), secondBean = two.newInstance();
            firstContext.setClassLoader(first); secondContext.setClassLoader(second);
            firstContext.getBeanFactory().registerSingleton("service", firstBean);
            secondContext.getBeanFactory().registerSingleton("service", secondBean);
            secondContext.registerAlias("service", "secondAlias");
            firstContext.refresh(); secondContext.refresh();
            AnyDoorRuntimeResolver resolver = new AnyDoorRuntimeResolver(new Class<?>[]{one, two},
                    new Object[]{firstContext, secondContext}, new Object[]{firstContext.getBeanFactory(), secondContext});
            List<Map<String, String>> beans = resolver.listBeans(request(null, null));
            assertEquals(2, beans.size());
            assertNotEquals(beans.get(0).get("classLoaderId"), beans.get(1).get("classLoaderId"));
            try {
                resolver.resolve(request("service", null));
                fail("Same name in different applications must be ambiguous");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("Multiple Spring beans"));
            }
            AnyDoorRuntimeResolver.Target selected = resolver.resolve(request("service", AnyDoorClassUtil.classLoaderId(second)));
            assertSame(two, selected.type);
            assertSame(secondBean, selected.bean);
            assertSame(secondBean, resolver.resolve(request("secondAlias", null)).bean);
            AnyDoorRunDto byContext = request("service", null);
            byContext.setBeanContextId(beans.stream().filter(bean -> AnyDoorClassUtil.classLoaderId(first).equals(bean.get("classLoaderId")))
                    .findFirst().get().get("beanContextId"));
            assertSame(firstBean, resolver.resolve(byContext).bean);
        }
    }

    @Test
    public void lazyApplicationTypesCanBeLocatedWithoutInitialization() throws Exception {
        Path root = compile("public class SharedType { static { System.setProperty(\"anydoor.attach.lazy.initialized\", \"true\"); } }");
        try (URLClassLoader loader = loader(root); GenericApplicationContext context = new GenericApplicationContext()) {
            context.setClassLoader(loader);
            context.refresh();
            AnyDoorRuntimeResolver resolver = new AnyDoorRuntimeResolver(new Class<?>[]{Object.class}, new Object[]{context}, new Object[0]);
            assertSame(loader, resolver.findTypes("SharedType", null).get(0).getClassLoader());
            assertNull(System.getProperty("anydoor.attach.lazy.initialized"));
        } finally { System.clearProperty("anydoor.attach.lazy.initialized"); }
    }

    private Path compile(String source) throws Exception {
        Path root = temporary.newFolder().toPath();
        Path file = root.resolve("SharedType.java");
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-source", "8", "-target", "8", "-d", root.toString(), file.toString()));
        return root;
    }

    private static URLClassLoader loader(Path root) throws Exception {
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, ClassLoader.getSystemClassLoader().getParent());
    }

    private static AnyDoorRunDto request(String beanName, String loaderId) {
        AnyDoorRunDto request = new AnyDoorRunDto();
        request.setClassName("SharedType"); request.setBeanName(beanName); request.setClassLoaderId(loaderId); return request;
    }
}
