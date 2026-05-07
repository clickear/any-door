/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package io.github.lgp547.anydoor.core;

import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorAopUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorBeanUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorClassUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorSpringUtil;
import io.github.lgp547.anydoor.util.JsonUtil;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class AnyDoorService {
    
    private static final String ANY_DOOR_RUN_MARK = "any-door run ";
    
    public AnyDoorService() {
    }
    
    public Object run(AnyDoorRunDto anyDoorDto) {
        try {
            anyDoorDto.verify();
            JsonUtil.applyConfig(anyDoorDto.getJsonTimezone(), anyDoorDto.getJsonDateTimeFormat());
            Class<?> clazz = Class.forName(anyDoorDto.getClassName());
            Method method = AnyDoorClassUtil.getMethod(clazz, anyDoorDto.getMethodName(), anyDoorDto.getParameterTypes());
            
            boolean containsBean = AnyDoorSpringUtil.containsBean(clazz);
            Object bean;
            if (!containsBean) {
                bean = AnyDoorBeanUtil.instantiate(clazz);
            } else {
                bean = AnyDoorSpringUtil.getBean(clazz);
                if (!Modifier.isPublic(method.getModifiers())) {
                    bean = AnyDoorAopUtil.getTargetObject(bean);
                }
            }
            return doRun(anyDoorDto, method, bean, () -> {
            }, () -> {
            });
        } catch (Exception e) {
            System.err.println("anyDoorService run exception. param [" + anyDoorDto + "]");
            throw new RuntimeException(e);
        }
    }
    
    /**
     * {@code  io.github.lgp547.anydoor.attach.AnyDoorAttach#AnyDoorRun(String)}
     */
    public Object run(String anyDoorDtoStr, Method method, Object bean, Runnable startRun, Runnable endRun) {
        if (null == anyDoorDtoStr || anyDoorDtoStr.isEmpty()) {
            System.err.println("anyDoorService run param exception. anyDoorDtoStr is empty");
            return null;
        }
        if (null == method || null == bean) {
            System.err.println("anyDoorService run param exception. method or bean is null");
            return null;
        }
        
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            AnyDoorRunDto anyDoorRunDto = AnyDoorRunDto.parseObj(anyDoorDtoStr);
            JsonUtil.applyConfig(anyDoorRunDto.getJsonTimezone(), anyDoorRunDto.getJsonDateTimeFormat());
            ClassLoader runClassLoader = getRunClassLoader(method, bean, oldClassLoader);
            Thread.currentThread().setContextClassLoader(runClassLoader);
            return doRun(JsonUtil.toJavaBean(anyDoorDtoStr, AnyDoorRunDto.class), method, bean, startRun, endRun);
        } catch (Throwable throwable) {
            System.err.println("anyDoorService run exception. param [" + anyDoorDtoStr + "]");
            Optional.ofNullable(throwable.getCause()).map(Throwable::getCause).map(Throwable::getCause).orElse(throwable).printStackTrace();
            return null;
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    private static ClassLoader getRunClassLoader(Method method, Object bean, ClassLoader oldClassLoader) {
        ClassLoader classLoader = Optional.ofNullable(method).map(Method::getDeclaringClass).map(Class::getClassLoader).orElse(null);
        if (classLoader != null) {
            return classLoader;
        }
        classLoader = Optional.ofNullable(bean).map(Object::getClass).map(Class::getClassLoader).orElse(null);
        return classLoader != null ? classLoader : oldClassLoader;
    }
    
    public Object doRun(AnyDoorRunDto anyDoorDto, Method method, Object bean, Runnable startRun, Runnable endRun) {
        String methodName = method.getName();
        String content = JsonUtil.toStrNotExc(anyDoorDto.getContent());
        if (Boolean.TRUE.equals(anyDoorDto.getExpressionMode())) {
            return handleExpressionMode(anyDoorDto, method, bean, content);
        }
        return handleAndRun(anyDoorDto, method, bean, startRun, endRun, content, methodName);
    }

    private Object handleExpressionMode(AnyDoorRunDto anyDoorDto, Method method, Object bean, String content) {
        try {
            if (JsonUtil.isJsonArray(content)) {
                throw new IllegalArgumentException("Expression mode does not support array content");
            }
            AnyDoorHandlerMethod handlerMethod = new AnyDoorHandlerMethod(bean, method);
            Map<String, Object> contentMap = JsonUtil.toMap(content);
            Object[] args = handlerMethod.resolveArgs(contentMap);
            Map<String, Object> argsByName = handlerMethod.resolveArgsByName(args);
            enrichExpressionArgsByName(argsByName, method, anyDoorDto.getExpressionRootParamName(), args);
            Class<?> targetType = resolveTargetType(method, anyDoorDto.getExpressionJsonPath());
            Object currentValue = resolveCurrentValue(argsByName, anyDoorDto.getExpressionRootParamName(), anyDoorDto.getExpressionJsonPath());
            AnyDoorExpressionContext context = new AnyDoorExpressionContext(
                    bean,
                    args,
                    argsByName,
                    currentValue,
                    targetType,
                    anyDoorDto.getExpressionRootParamName(),
                    anyDoorDto.getExpressionJsonPath()
            );
            String valueJson = new AnyDoorExpressionEvaluator().evaluate(anyDoorDto, context);
            String resultJson = buildSuccessJson(valueJson);
            writeExpressionResult(anyDoorDto.getExpressionResultPath(), resultJson);
            return valueJson;
        } catch (Exception e) {
            writeExpressionResult(anyDoorDto.getExpressionResultPath(), buildErrorJson(e));
            throw new RuntimeException(e);
        }
    }

    private static void enrichExpressionArgsByName(Map<String, Object> argsByName, Method method, String rootParamName, Object[] args) {
        if (rootParamName == null || rootParamName.isEmpty() || argsByName.containsKey(rootParamName)) {
            return;
        }
        if (method.getParameterCount() == 1 && args != null && args.length == 1) {
            argsByName.put(rootParamName, args[0]);
        }
    }

    private static Class<?> resolveTargetType(Method method, String jsonPath) {
        if (jsonPath == null || jsonPath.isEmpty()) {
            return Object.class;
        }
        String[] parts = jsonPath.split("\\.");
        if (parts.length == 0) {
            return Object.class;
        }
        int parameterCount = method.getParameterCount();
        for (int i = 0; i < parameterCount; i++) {
            MethodParameter parameter = new MethodParameter(method, i);
            parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            if (Objects.equals(parameter.getParameterName(), parts[0])) {
                Class<?> currentType = parameter.getParameterType();
                for (int j = 1; j < parts.length; j++) {
                    try {
                        currentType = findField(currentType, parts[j]).getType();
                    } catch (NoSuchFieldException e) {
                        return Object.class;
                    }
                }
                return currentType;
            }
        }
        if (parameterCount == 1 && parts.length > 1) {
            Class<?> currentType = method.getParameterTypes()[0];
            for (int j = 1; j < parts.length; j++) {
                try {
                    currentType = findField(currentType, parts[j]).getType();
                } catch (NoSuchFieldException e) {
                    return Object.class;
                }
            }
            return currentType;
        }
        return Object.class;
    }

    private static Object resolveCurrentValue(Map<String, Object> argsByName, String rootParamName, String jsonPath) {
        if (jsonPath == null || jsonPath.isEmpty()) {
            return null;
        }
        String[] parts = jsonPath.split("\\.");
        Object current = argsByName.get(rootParamName != null ? rootParamName : parts[0]);
        if (current == null && argsByName.size() == 1) {
            current = argsByName.values().iterator().next();
        }
        for (int i = 1; i < parts.length && current != null; i++) {
            current = readFieldValue(current, parts[i]);
        }
        return current;
    }

    private static Object readFieldValue(Object source, String fieldName) {
        Class<?> currentClass = source.getClass();
        while (currentClass != null) {
            try {
                java.lang.reflect.Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException ignored) {
                currentClass = currentClass.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static void writeExpressionResult(String expressionResultPath, String resultJson) {
        if (expressionResultPath == null || expressionResultPath.isEmpty()) {
            return;
        }
        try {
            File file = new File(expressionResultPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Failed to create result dir");
            }
            Files.write(file.toPath(), (resultJson == null ? "null" : resultJson).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write expression result", e);
        }
    }

    private static String buildErrorJson(Exception e) {
        Throwable root = Optional.ofNullable(e.getCause()).orElse(e);
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return "{\"success\":false,\"error\":" + JsonUtil.toStrNotExc(message) + "}";
    }

    private static String buildSuccessJson(String valueJson) {
        return "{\"success\":true,\"value\":" + (valueJson == null ? "null" : valueJson) + "}";
    }

    private static java.lang.reflect.Field findField(Class<?> currentType, String fieldName) throws NoSuchFieldException {
        Class<?> type = currentType;
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
    
    private static Object handleAndRun(AnyDoorRunDto anyDoorDto, Method method, Object bean, Runnable startRun, Runnable endRun, String content, String methodName) {
        AnyDoorHandlerMethod handlerMethod = new AnyDoorHandlerMethod(bean, method);
        
        Integer num = anyDoorDto.getNum();
        List<Map<String, Object>> contentMaps;
        // 若是json数组，数量按照数组为准
        if (JsonUtil.isJsonArray(content)) {
            contentMaps = JsonUtil.toMaps(content);
            num = contentMaps.size();
        } else {
            ArrayList<Map<String, Object>> list = new ArrayList<>();
            list.add(JsonUtil.toMap(content));
            contentMaps = list;
        }
        
        if (num < 1) {
            System.err.println("anyDoorService run param exception. num < 1");
            return null;
        }
        
        if (num == 1) {
            if (Objects.equals(anyDoorDto.getSync(), true)) {
                Object result = handlerMethod.invokeSync(startRun, contentMaps.get(0));
                System.out.println(ANY_DOOR_RUN_MARK + methodName + " return: " + JsonUtil.toContentStrNotExc(result));
                endRun.run();
                return result;
            } else {
                handlerMethod.invokeAsync(startRun, contentMaps.get(0)).whenComplete(futureResultLogConsumer(methodName)).whenComplete((result, throwable) -> endRun.run());
                return null;
            }
        } else {
            if (anyDoorDto.getConcurrent()) {
                List<CompletableFuture<Object>> completableFutures =
                        handlerMethod.concurrentInvokeAsync(startRun, contentMaps, num, resultLogConsumer(methodName), excLogConsumer(methodName));
                CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).whenComplete((result, throwable) -> endRun.run());
            } else {
                if (Objects.equals(anyDoorDto.getSync(), true)) {
                    handlerMethod.parallelInvokeSync(startRun, contentMaps, num, resultLogConsumer(methodName));
                    endRun.run();
                } else {
                    CompletableFuture<Void> voidCompletableFuture = handlerMethod.parallelInvokeAsync(startRun, contentMaps, num, resultLogConsumer(methodName));
                    voidCompletableFuture.whenComplete((result, throwable) -> endRun.run());
                }
            }
            return null;
        }
    }
    
    private static BiConsumer<Integer, Object> resultLogConsumer(String methodName) {
        return (num, result) -> System.out.println(ANY_DOOR_RUN_MARK + methodName + " " + num + " return: " + JsonUtil.toContentStrNotExc(result));
    }
    
    private static BiConsumer<Integer, Throwable> excLogConsumer(String methodName) {
        return (num, throwable) -> {
            System.err.println(ANY_DOOR_RUN_MARK + methodName + " " + num + " exception: " + throwable.getMessage());
            Optional.ofNullable(throwable.getCause()).map(Throwable::getCause).map(Throwable::getCause).orElse(throwable).printStackTrace();
        };
    }
    
    private static BiConsumer<Object, Throwable> futureResultLogConsumer(String methodName) {
        return (result, throwable) -> {
            if (throwable != null) {
                System.err.println(ANY_DOOR_RUN_MARK + methodName + " exception: " + throwable.getMessage());
                Optional.ofNullable(throwable.getCause()).map(Throwable::getCause).map(Throwable::getCause).orElse(throwable).printStackTrace();
            } else {
                System.out.println(ANY_DOOR_RUN_MARK + methodName + " return: " + JsonUtil.toContentStrNotExc(result));
            }
        };
    }
}
