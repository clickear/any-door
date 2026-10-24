package io.github.lgp547.anydoor.test;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorAopUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorBeanResolver;
import io.github.lgp547.anydoor.common.util.AnyDoorClassUtil;
import io.github.lgp547.anydoor.core.AnyDoorService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.GenericApplicationContext;

import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class AnyDoorClassLoadingTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void lookupUsesContextLoaderAndDoesNotInitializeClasses() throws Exception {
        Path root = compile("child.Lookup", "package child; public class Lookup { static { System.setProperty(\"anydoor.loader.test.initialized\", \"true\"); } }");
        try (URLClassLoader loader = child(root)) {
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(loader);
                Class<?> type = AnyDoorClassUtil.forName("child.Lookup");
                assertSame(loader, type.getClassLoader());
                assertNull(System.getProperty("anydoor.loader.test.initialized"));
                assertSame(type, AnyDoorClassUtil.forName("child.Lookup", loader));
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
                System.clearProperty("anydoor.loader.test.initialized");
            }
        }
    }

    @Test
    public void overloadParametersAreResolvedInTheDeclaringLoader() throws Exception {
        Path root = compile("child.Service", "package child; public class Service { public static class Payload {} public void run(Payload value) {} public void run(String value) {} }");
        try (URLClassLoader loader = child(root)) {
            Class<?> type = Class.forName("child.Service", false, loader);
            Method method = AnyDoorClassUtil.getMethod(type, "run", Collections.singletonList("child.Service$Payload"));
            assertSame(loader, method.getParameterTypes()[0].getClassLoader());
            assertEquals(String[][].class, AnyDoorClassUtil.forName("java.lang.String[][]", loader));
            assertEquals(int[].class, AnyDoorClassUtil.forName("int[]", loader));
        }
    }

    @Test
    public void inheritedParentMethodUsesSelectedChildResourcesAndRestoresContext() throws Exception {
        Path root = childBean();
        Files.write(root.resolve("application-marker.txt"), "selected-child".getBytes(StandardCharsets.UTF_8));
        try (URLClassLoader loader = child(root)) {
            ParentHandler bean = (ParentHandler) Class.forName("child.Handler", true, loader).newInstance();
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Object result = new AnyDoorService().run("{\"className\":\"child.Handler\",\"methodName\":\"run\",\"content\":{},\"sync\":true}",
                    ParentHandler.class.getMethod("run"), bean, () -> assertSame(loader, Thread.currentThread().getContextClassLoader()), () -> { });
            assertEquals("selected-child", result);
            assertSame(previous, Thread.currentThread().getContextClassLoader());
            assertSame(loader, bean.observed);
        }
    }

    @Test
    public void privateParentMethodUsesSelectedChildAndRestoresContextOnFailure() throws Exception {
        Path root = childBean();
        Files.write(root.resolve("application-marker.txt"), "private-child".getBytes(StandardCharsets.UTF_8));
        try (URLClassLoader loader = child(root)) {
            ParentHandler bean = (ParentHandler) Class.forName("child.Handler", true, loader).newInstance();
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            AnyDoorRunDto dto = invocation(true);
            AnyDoorService service = new AnyDoorService();
            assertEquals("private-child", service.doRun(dto, ParentHandler.class.getDeclaredMethod("hidden"), bean, () -> { }, () -> { }));
            try {
                service.doRun(dto, ParentHandler.class.getMethod("fail"), bean, () -> { }, () -> { });
                fail("Expected invocation failure");
            } catch (RuntimeException expected) {
                assertSame(loader, bean.observed);
            }
            assertSame(previous, Thread.currentThread().getContextClassLoader());
        }
    }

    @Test
    public void asynchronousInvocationUsesBeanLoader() throws Exception {
        Path root = childBean();
        Files.write(root.resolve("application-marker.txt"), "async-child".getBytes(StandardCharsets.UTF_8));
        try (URLClassLoader loader = child(root)) {
            ParentHandler bean = (ParentHandler) Class.forName("child.Handler", true, loader).newInstance();
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            CountDownLatch completed = new CountDownLatch(1);
            new AnyDoorService().doRun(invocation(false), ParentHandler.class.getMethod("run"), bean, () -> { }, completed::countDown);
            assertTrue(completed.await(10, TimeUnit.SECONDS));
            assertSame(loader, bean.observed);
            assertSame(previous, Thread.currentThread().getContextClassLoader());
        }
    }

    @Test
    public void jdkProxyDefinedInParentStillUsesItsChildTargetLoader() throws Exception {
        Path root = childBean();
        Files.write(root.resolve("application-marker.txt"), "proxied-child".getBytes(StandardCharsets.UTF_8));
        try (URLClassLoader loader = child(root)) {
            ParentHandler bean = (ParentHandler) Class.forName("child.Handler", true, loader).newInstance();
            ProxyFactory factory = new ProxyFactory(bean);
            factory.setInterfaces(ResourceReader.class);
            Object proxy = factory.getProxy(getClass().getClassLoader());
            assertNotSame(loader, proxy.getClass().getClassLoader());
            assertSame(loader, AnyDoorClassUtil.executionClassLoader(proxy, ResourceReader.class.getMethod("run"), null));
            assertEquals("proxied-child", new AnyDoorService().doRun(invocation(true), ResourceReader.class.getMethod("run"), proxy, () -> { }, () -> { }));
        }
    }

    @Test
    public void independentSpringCopyCanBeQueriedAndItsProxyUnwrapped() throws Exception {
        Path root = compile("foreign.App", "package foreign; public class App {"
                + " public interface Api { String run(); }"
                + " public static class Service implements Api { public String run() { return \"foreign\"; } }"
                + " public static Object context() {"
                + " org.springframework.context.support.GenericApplicationContext c = new org.springframework.context.support.GenericApplicationContext();"
                + " org.springframework.aop.framework.ProxyFactory p = new org.springframework.aop.framework.ProxyFactory(new Service());"
                + " p.setInterfaces(Api.class); c.setClassLoader(App.class.getClassLoader()); c.getBeanFactory().registerSingleton(\"service\", p.getProxy(App.class.getClassLoader())); c.refresh(); return c; } }");
        List<URL> urls = new ArrayList<>();
        urls.add(root.toUri().toURL());
        for (String entry : classpath().split(File.pathSeparator)) {
            if (new File(entry).getName().startsWith("spring-") && entry.endsWith(".jar")) urls.add(new File(entry).toURI().toURL());
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader().getParent())) {
            Class<?> app = Class.forName("foreign.App", true, loader);
            Object context = app.getMethod("context").invoke(null);
            try {
                assertFalse(context instanceof GenericApplicationContext);
                Class<?> type = Class.forName("foreign.App$Service", false, loader);
                List<Map<String, String>> candidates = AnyDoorBeanResolver.list(type, new Object[]{context}, new Object[0]);
                assertEquals(1, candidates.size());
                assertEquals("JDK", candidates.get(0).get("proxyType"));
                assertEquals(AnyDoorClassUtil.classLoaderId(loader), candidates.get(0).get("classLoaderId"));
                Object proxy = AnyDoorBeanResolver.resolve(type, "service", null, new Object[]{context}, new Object[0]);
                assertSame(type, AnyDoorAopUtil.getTargetClass(proxy));
                assertTrue(type.isInstance(AnyDoorAopUtil.getTargetObject(proxy)));
            } finally {
                context.getClass().getMethod("close").invoke(context);
            }
        }
    }

    private Path childBean() throws Exception {
        return compile("child.Handler", "package child; public class Handler extends " + ParentHandler.class.getCanonicalName() + " {}");
    }

    private Path compile(String name, String source) throws Exception {
        Path root = temporary.newFolder().toPath();
        Path file = root.resolve(name.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.write(file, source.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-source", "8", "-target", "8", "-g",
                "-classpath", classpath(), "-d", root.toString(), file.toString()));
        return root;
    }

    private static String classpath() { return System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")); }
    private URLClassLoader child(Path root) throws Exception { return new URLClassLoader(new URL[]{root.toUri().toURL()}, getClass().getClassLoader()); }
    private static AnyDoorRunDto invocation(boolean sync) {
        AnyDoorRunDto dto = new AnyDoorRunDto(); dto.setContent(Collections.emptyMap()); dto.setSync(sync); return dto;
    }

    public interface ResourceReader { String run(); }
    public static class ParentHandler implements ResourceReader {
        volatile ClassLoader observed;
        public String run() {
            observed = Thread.currentThread().getContextClassLoader();
            URL resource = observed.getResource("application-marker.txt");
            if (resource == null) return "resource-missing";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            } catch (Exception e) { throw new IllegalStateException(e); }
        }
        private String hidden() { return run(); }
        public String fail() { observed = Thread.currentThread().getContextClassLoader(); throw new IllegalStateException("expected"); }
    }
}
