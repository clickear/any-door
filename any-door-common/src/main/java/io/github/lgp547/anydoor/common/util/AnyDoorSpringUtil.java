package io.github.lgp547.anydoor.common.util;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AnyDoorSpringUtil {

    private static List<ApplicationContext> applicationContextList;

    private static Supplier<ApplicationContext[]> applicationContextsSupplier;

    private static volatile boolean isInit = false;

    public static void initApplicationContexts(Supplier<ApplicationContext[]> applicationContextsSupplier) {
        if (!isInit) {
            synchronized (AnyDoorSpringUtil.class) {
                if (!isInit) {
                    AnyDoorSpringUtil.applicationContextsSupplier = applicationContextsSupplier;
                    updateApplicationContextList();
                    setClassLoader();
                    isInit = true;
                }
            }
        }
    }

    /**
     * 由于没有set进去，会从执行线程获取类加载器
     * 当处于懒加载期间，会有获取到 AnyDoorClassloader 的情况，这里兼容处理
     */
    private static void setClassLoader() {
        for (ApplicationContext applicationContext : applicationContextList) {
            if (applicationContext instanceof DefaultResourceLoader) {
                ((DefaultResourceLoader) applicationContext).setClassLoader(applicationContext.getClassLoader());
            }
        }
    }

    private static List<ApplicationContext> sortList(List<ApplicationContext> curApplicationContextList) {
        if (curApplicationContextList.size() > 1) {
            String contextName = System.getProperty("anydoor.spring.core.context.name");
            if (StringUtils.hasLength(contextName)) {
                Optional<ApplicationContext> any = curApplicationContextList.stream().filter(item -> item.getDisplayName().contains(contextName)).findAny();
                if (any.isPresent()) {
                    curApplicationContextList.clear();
                    curApplicationContextList.add(any.get());
                } else {
                    throw new IllegalArgumentException("not found context name: " + contextName);
                }
            } else {
                ArrayList<String> contextNames = new ArrayList<>();
                contextNames.add("WebServerApplicationContext");
                contextNames.add("ReactiveWebApplicationContext");
                contextNames.add("WebApplicationContext");
                contextNames.add("ConfigurableApplicationContext");
                curApplicationContextList.sort(Comparator.comparing(ApplicationContext::getDisplayName, (name1, name2) -> comparingSort(contextNames, name1, name2)));
            }
        }
        return curApplicationContextList;
    }

    private static int comparingSort(List<String> sort, String name1, String name2) {
        for (String s : sort) {
            if (name1.contains(s)) {
                return -1;
            }
            if (name2.contains(s)) {
                return 1;
            }
        }
        return 0;
    }

    private static List<ApplicationContext> updateApplicationContextList() {
        synchronized (AnyDoorSpringUtil.class) {
            ApplicationContext[] array = applicationContextsSupplier.get();
            if (array == null || array.length == 0) {
                AnyDoorSpringUtil.applicationContextList = Collections.emptyList();
            } else {
                AnyDoorSpringUtil.applicationContextList = Arrays.stream(array).collect(Collectors.toList());
            }
            return sortList(AnyDoorSpringUtil.applicationContextList);
        }
    }

    public static <T> T getBean(Class<T> requiredType) throws BeansException {
        synchronized (AnyDoorSpringUtil.class) {
            Objects.requireNonNull(applicationContextList);

            try {
                return doGetBean(requiredType, applicationContextList);
            } catch (Exception exception) {
                return doGetBean(requiredType, updateApplicationContextList());
            }
        }
    }

    private static <T> T doGetBean(Class<T> requiredType, List<ApplicationContext> applicationContextList1) {
        for (ApplicationContext applicationContext : applicationContextList1) {
            try {
                return applicationContext.getBean(requiredType);
            } catch (BeansException ignored) {
            }
        }
        throw new NoSuchBeanDefinitionException("No bean of type " + requiredType + " is defined");
    }

    public static boolean containsBean(Class<?> requiredType) {
        try {
            getBean(requiredType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
