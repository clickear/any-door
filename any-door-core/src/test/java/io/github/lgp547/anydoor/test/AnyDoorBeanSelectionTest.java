package io.github.lgp547.anydoor.test;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorBeanResolver;
import io.github.lgp547.anydoor.common.util.AnyDoorSpringUtil;
import io.github.lgp547.anydoor.core.AnyDoorService;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AnyDoorBeanSelectionTest {
    private static final List<GenericApplicationContext> contexts = new ArrayList<>();

    @BeforeClass
    public static void initContexts() {
        AnyDoorSpringUtil.initApplicationContexts(() -> contexts.toArray(new ApplicationContext[0]), null);
    }

    @Before
    public void clearContexts() { contexts.clear(); }

    @After
    public void closeContexts() {
        for (GenericApplicationContext context : contexts) context.close();
        contexts.clear();
    }

    @Test
    public void discoveryListsNamesWithoutInvokingBusinessMethods() {
        EchoBean one = new EchoBean("one"), two = new EchoBean("two");
        context("app", "first", one, "second", two);
        List<Map<String, String>> beans = AnyDoorSpringUtil.listBeans(EchoBean.class);
        assertEquals(2, beans.size());
        assertEquals("first", beans.get(0).get("beanName"));
        assertEquals("second", beans.get(1).get("beanName"));
        assertEquals("none", beans.get(0).get("proxyType"));
        assertEquals(0, one.calls + two.calls);
    }

    @Test
    public void namedSelectionInvokesOnlyTheSelectedInstance() {
        EchoBean one = new EchoBean("one"), two = new EchoBean("two");
        context("app", "first", one, "second", two);
        assertEquals("two:value", invoke(EchoBean.class, "second", null, "echo"));
        assertEquals(0, one.calls);
        assertEquals(1, two.calls);
    }

    @Test
    public void abstractParentDiscoversAllImplementationsAndCallsInheritedTemplateOnSelection() {
        FirstHandler first = new FirstHandler();
        SecondHandler second = new SecondHandler();
        context("app", "firstHandler", first, "secondHandler", second);
        List<Map<String, String>> beans = AnyDoorSpringUtil.listBeans(AbstractHandler.class);
        assertEquals(2, beans.size());
        assertEquals(FirstHandler.class.getName(), beans.get(0).get("className"));
        assertEquals(SecondHandler.class.getName(), beans.get(1).get("className"));
        expectFailure("Multiple Spring beans", () -> invoke(AbstractHandler.class, null, null, "execute"));
        assertEquals("second:value", invoke(AbstractHandler.class, "secondHandler", null, "execute"));
        assertEquals(0, first.calls);
        assertEquals(1, second.calls);
    }

    @Test
    public void abstractMethodInvokesTheSelectedSubclassOverride() {
        FirstHandler first = new FirstHandler();
        SecondHandler second = new SecondHandler();
        context("app", "firstHandler", first, "secondHandler", second);
        assertEquals("first-override:value", invoke(AbstractHandler.class, "firstHandler", null, "echo", Collections.singletonMap("args0", "value")));
        assertEquals(1, first.calls);
        assertEquals(0, second.calls);
    }

    @Test
    public void abstractParentSelectionSupportsDifferentProxiedImplementations() {
        FirstHandler first = new FirstHandler();
        SecondHandler second = new SecondHandler();
        ProxyFactory cglib = new ProxyFactory(first);
        cglib.setProxyTargetClass(true);
        cglib.addAdvice((MethodInterceptor) invocation -> "advised:" + invocation.proceed());
        ProxyFactory jdk = new ProxyFactory(second);
        jdk.setInterfaces(Echo.class);
        jdk.addAdvice((MethodInterceptor) invocation -> "advised:" + invocation.proceed());
        context("app", "firstHandler", cglib.getProxy(), "secondHandler", jdk.getProxy());
        assertEquals(2, AnyDoorSpringUtil.listBeans(AbstractHandler.class).size());
        assertEquals("advised:first-override:value", invoke(AbstractHandler.class, "firstHandler", null, "echo", Collections.singletonMap("args0", "value")));
        assertEquals("advised:second-override:value", invoke(AbstractHandler.class, "secondHandler", null, "echo", Collections.singletonMap("args0", "value")));
        assertEquals(1, first.calls);
        assertEquals(1, second.calls);
    }

    @Test
    public void ambiguityIsAnErrorInsteadOfCreatingAnUnmanagedInstance() {
        EchoBean one = new EchoBean("one"), two = new EchoBean("two");
        context("app", "first", one, "second", two);
        expectFailure("Multiple Spring beans", () -> invoke(EchoBean.class, null, null, "echo"));
        assertEquals(0, one.calls + two.calls);
    }

    @Test
    public void staleOrWrongTypeSelectionNeverFallsBack() {
        EchoBean bean = new EchoBean("one");
        context("app", "first", bean, "wrongType", "not an EchoBean");
        expectFailure("Spring bean not found", () -> invoke(EchoBean.class, "deleted", null, "echo"));
        expectFailure("Spring bean not found", () -> invoke(EchoBean.class, "wrongType", null, "echo"));
        expectFailure("Spring bean not found", () -> invoke(EchoBean.class, "first", "stale-context", "echo"));
        assertEquals(0, bean.calls);
    }

    @Test
    public void equalNamesInDifferentContextsRequireContextSelection() {
        context("app-one", "service", new EchoBean("one"));
        context("app-two", "service", new EchoBean("two"));
        expectFailure("Multiple Spring beans", () -> invoke(EchoBean.class, "service", null, "echo"));
        List<Map<String, String>> beans = AnyDoorSpringUtil.listBeans(EchoBean.class);
        assertNotEquals(beans.get(0).get("beanContextId"), beans.get(1).get("beanContextId"));
        String second = beans.stream().filter(bean -> "app-two".equals(bean.get("contextName")))
                .findFirst().get().get("beanContextId");
        assertEquals("two:value", invoke(EchoBean.class, "service", second, "echo"));
    }

    @Test
    public void configuredContextRestrictionRemainsInEffect() {
        context("app-one", "service", new EchoBean("one"));
        context("app-two", "service", new EchoBean("two"));
        String key = "anydoor.spring.core.context.name";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "app-two");
            assertEquals(1, AnyDoorSpringUtil.listBeans(EchoBean.class).size());
            assertEquals("two:value", invoke(EchoBean.class, null, null, "echo"));
        } finally {
            if (previous == null) System.clearProperty(key); else System.setProperty(key, previous);
        }
    }

    @Test
    public void singleBeanAndNonSpringObjectsStillRunWithoutSelection() {
        context("app", "only", new EchoBean("spring"));
        assertEquals("spring:value", invoke(EchoBean.class, null, null, "echo"));
        assertEquals("plain:value", invoke(PlainBean.class, null, null, "echo"));
        expectFailure("Spring bean not found", () -> invoke(PlainBean.class, "missing", null, "echo"));
    }

    @Test
    public void aliasSelectsTheCanonicalBean() {
        GenericApplicationContext app = context("app", "canonical", new EchoBean("selected"));
        app.registerAlias("canonical", "alias");
        assertEquals("selected:value", invoke(EchoBean.class, "alias", null, "echo"));
        assertEquals(1, AnyDoorSpringUtil.listBeans(EchoBean.class).size());
    }

    @Test
    public void jdkProxyRetainsAdviceAndOriginalParameterMapping() {
        EchoBean target = new EchoBean("jdk");
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setInterfaces(Echo.class);
        proxy.addAdvice((MethodInterceptor) invocation -> "advised:" + invocation.proceed());
        context("app", "jdk", proxy.getProxy());
        assertEquals("JDK", AnyDoorSpringUtil.listBeans(EchoBean.class).get(0).get("proxyType"));
        assertEquals("advised:jdk:value", invoke(EchoBean.class, "jdk", null, "echo"));
        assertEquals(1, target.calls);
        assertEquals("jdk:private", invoke(EchoBean.class, "jdk", null, "hidden"));
        assertEquals(1, target.calls);
    }

    @Test
    public void cglibProxyRetainsAdviceAndPrivateMethodsUseSelectedTarget() {
        EchoBean target = new EchoBean("cglib");
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice((MethodInterceptor) invocation -> "advised:" + invocation.proceed());
        context("app", "proxy", proxy.getProxy(), "other", new EchoBean("other"));
        Map<String, String> bean = AnyDoorSpringUtil.listBeans(EchoBean.class).stream()
                .filter(item -> "proxy".equals(item.get("beanName"))).findFirst().get();
        assertEquals("CGLIB", bean.get("proxyType"));
        assertEquals("advised:cglib:value", invoke(EchoBean.class, "proxy", null, "echo"));
        assertEquals("cglib:private", invoke(EchoBean.class, "proxy", null, "hidden"));
    }

    @Test
    public void proxyExceptionDoesNotInvokeTheMethodTwice() {
        AtomicInteger calls = new AtomicInteger();
        ProxyFactory proxy = new ProxyFactory(new EchoBean("failing"));
        proxy.setInterfaces(Echo.class);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            calls.incrementAndGet();
            throw new IllegalStateException("expected failure");
        });
        context("app", "proxy", proxy.getProxy());
        expectFailure("expected failure", () -> invoke(EchoBean.class, "proxy", null, "echo"));
        assertEquals(1, calls.get());
    }

    @Test
    public void discoveryOperationDoesNotNeedAMethod() {
        EchoBean bean = new EchoBean("one");
        context("app", "first", bean);
        AnyDoorRunDto query = new AnyDoorRunDto();
        query.setClassName(EchoBean.class.getName());
        query.setOperation("listBeans");
        Object result = new AnyDoorService().run(query);
        assertEquals(1, ((List<?>) result).size());
        assertEquals(0, bean.calls);
    }

    @Test
    public void parentFactoriesAndNativeFactoryDiscoveryAreDeduplicated() {
        GenericApplicationContext parent = context("parent", "parentBean", new EchoBean("parent"));
        GenericApplicationContext child = new GenericApplicationContext(parent);
        child.setDisplayName("child");
        child.refresh();
        contexts.add(child);
        assertEquals(1, AnyDoorBeanResolver.list(EchoBean.class,
                new ApplicationContext[]{child, parent}, new BeanFactory[]{parent, child, parent.getBeanFactory(), child.getBeanFactory()}).size());
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        EchoBean selected = new EchoBean("factory");
        factory.registerSingleton("factoryBean", selected);
        assertSame(selected, AnyDoorBeanResolver.resolve(EchoBean.class, "factoryBean", null,
                new ApplicationContext[0], new BeanFactory[]{factory, factory}));
    }

    @Test
    public void closedContextsAreIgnored() {
        GenericApplicationContext closed = context("closed", "old", new EchoBean("old"));
        closed.close();
        context("active", "new", new EchoBean("new"));
        assertEquals(1, AnyDoorSpringUtil.listBeans(EchoBean.class).size());
        assertEquals("new:value", invoke(EchoBean.class, null, null, "echo"));
    }

    @Test
    public void discoveryDoesNotInstantiateLazyBeansAndSelectionCreatesOnlyOne() {
        AtomicInteger first = new AtomicInteger(), second = new AtomicInteger();
        GenericApplicationContext app = new GenericApplicationContext();
        app.registerBean("first", EchoBean.class, () -> { first.incrementAndGet(); return new EchoBean("one"); }, definition -> definition.setLazyInit(true));
        app.registerBean("second", EchoBean.class, () -> { second.incrementAndGet(); return new EchoBean("two"); }, definition -> definition.setLazyInit(true));
        app.refresh();
        contexts.add(app);
        assertEquals(2, AnyDoorSpringUtil.listBeans(EchoBean.class).size());
        assertEquals(0, first.get() + second.get());
        assertEquals("two:value", invoke(EchoBean.class, "second", null, "echo"));
        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    public void attachDtoCarriesSelectionAndDiscoveryOperation() {
        AnyDoorRunDto dto = AnyDoorRunDto.parseObj("{\"className\":\"example.Bean\",\"beanName\":\"second\",\"beanContextId\":\"abc\",\"classLoaderId\":\"loader\",\"operation\":\"listBeans\",\"jarPaths\":[]}");
        assertEquals("second", dto.getBeanName());
        assertEquals("abc", dto.getBeanContextId());
        assertEquals("loader", dto.getClassLoaderId());
        assertTrue(dto.isListBeans());
        assertTrue(dto.verifyPassByAttach());
    }

    @Test
    public void attachDtoPreservesEscapedBeanNamesExactly() {
        String name = "订单 \"west\"\\route";
        String json = io.github.lgp547.anydoor.util.JsonUtil.toStrNotExc(Collections.singletonMap("beanName", name));
        assertEquals(name, AnyDoorRunDto.parseObj(json).getBeanName());
        assertEquals("abc", AnyDoorRunDto.parseObj("{\"beanName\" : \"\\u0061bc\"}").getBeanName());
    }

    private static GenericApplicationContext context(String name, Object... entries) {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setDisplayName(name);
        for (int i = 0; i < entries.length; i += 2) context.getBeanFactory().registerSingleton((String) entries[i], entries[i + 1]);
        context.refresh();
        contexts.add(context);
        return context;
    }

    private static Object invoke(Class<?> type, String beanName, String contextId, String method) {
        return invoke(type, beanName, contextId, method, Collections.singletonMap("value", "value"));
    }

    private static Object invoke(Class<?> type, String beanName, String contextId, String method, Map<String, String> content) {
        AnyDoorRunDto dto = new AnyDoorRunDto();
        dto.setClassName(type.getName());
        dto.setMethodName(method);
        dto.setBeanName(beanName);
        dto.setBeanContextId(contextId);
        dto.setContent(content);
        dto.setSync(true);
        return new AnyDoorService().run(dto);
    }

    private static void expectFailure(String message, Runnable action) {
        try {
            action.run();
            fail("Expected failure containing: " + message);
        } catch (RuntimeException exception) {
            assertTrue(exception.toString(), exception.toString().contains(message));
        }
    }

    public interface Echo { String echo(String value); }

    public abstract static class AbstractHandler implements Echo {
        int calls;
        protected abstract String name();
        @Override public abstract String echo(String value);
        public String execute(String value) { calls++; return name() + ":" + value; }
    }

    public static class FirstHandler extends AbstractHandler {
        @Override protected String name() { return "first"; }
        @Override public String echo(String firstInput) { calls++; return "first-override:" + firstInput; }
    }

    public static class SecondHandler extends AbstractHandler {
        @Override protected String name() { return "second"; }
        @Override public String echo(String secondInput) { calls++; return "second-override:" + secondInput; }
    }

    public static class EchoBean implements Echo {
        private final String name;
        int calls;
        public EchoBean() { this("unmanaged"); }
        public EchoBean(String name) { this.name = name; }
        @Override public String echo(String value) { calls++; return name + ":" + value; }
        private String hidden() { return name + ":private"; }
    }

    public static class PlainBean {
        public String echo(String value) { return "plain:" + value; }
    }
}
