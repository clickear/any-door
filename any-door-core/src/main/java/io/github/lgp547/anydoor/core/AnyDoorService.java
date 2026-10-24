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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
            Class<?> clazz = AnyDoorClassUtil.forName(anyDoorDto.getClassName());
            if (anyDoorDto.getClassLoaderId() != null && !anyDoorDto.getClassLoaderId().isEmpty()
                    && !anyDoorDto.getClassLoaderId().equals(AnyDoorClassUtil.classLoaderId(clazz.getClassLoader()))) {
                throw new IllegalArgumentException("Application class not found in selected classLoaderId: " + anyDoorDto.getClassLoaderId());
            }
            if (anyDoorDto.isListBeans()) return AnyDoorSpringUtil.listBeans(clazz);
            Method method = AnyDoorClassUtil.getMethod(clazz, anyDoorDto.getMethodName(), anyDoorDto.getParameterTypes());

            Object bean = AnyDoorSpringUtil.resolveBean(clazz, anyDoorDto.getBeanName(), anyDoorDto.getBeanContextId());
            if (bean == null) {
                bean = AnyDoorBeanUtil.instantiate(clazz);
            }
            if (!Modifier.isPublic(method.getModifiers())) bean = AnyDoorAopUtil.getTargetObject(bean);
            return doRun(anyDoorDto, method, bean, () -> {
            }, () -> {
            });
        } catch (Exception e) {
            System.err.println("anyDoorService run exception. param [" + anyDoorDto + "]");
            throw new RuntimeException(e);
        }
    }

    /** Result-aware transport API. Async invocations complete this future after the business method finishes. */
    public CompletableFuture<Object> runForResult(AnyDoorRunDto anyDoorDto) {
        try {
            anyDoorDto.verify();
            JsonUtil.applyConfig(anyDoorDto.getJsonTimezone(), anyDoorDto.getJsonDateTimeFormat());
            Class<?> clazz = AnyDoorClassUtil.forName(anyDoorDto.getClassName());
            if (anyDoorDto.getClassLoaderId() != null && !anyDoorDto.getClassLoaderId().isEmpty()
                    && !anyDoorDto.getClassLoaderId().equals(AnyDoorClassUtil.classLoaderId(clazz.getClassLoader()))) {
                throw new IllegalArgumentException("Application class not found in selected classLoaderId: " + anyDoorDto.getClassLoaderId());
            }
            Method method = AnyDoorClassUtil.getMethod(clazz, anyDoorDto.getMethodName(), anyDoorDto.getParameterTypes());
            Object bean = AnyDoorSpringUtil.resolveBean(clazz, anyDoorDto.getBeanName(), anyDoorDto.getBeanContextId());
            if (bean == null) bean = AnyDoorBeanUtil.instantiate(clazz);
            if (!Modifier.isPublic(method.getModifiers())) bean = AnyDoorAopUtil.getTargetObject(bean);
            return doRunForResult(anyDoorDto, method, bean, () -> { }, () -> { });
        } catch (Throwable error) { return failedFuture(error); }
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
            throw throwable instanceof RuntimeException ? (RuntimeException) throwable : new RuntimeException(throwable);
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    public CompletableFuture<Object> runForResult(String anyDoorDtoStr, Method method, Object bean,
                                                   Runnable startRun, Runnable endRun) {
        try {
            if (anyDoorDtoStr == null || anyDoorDtoStr.isEmpty() || method == null || bean == null) {
                throw new IllegalArgumentException("anyDoor invocation parameters must not be empty");
            }
            AnyDoorRunDto dto = JsonUtil.toJavaBean(anyDoorDtoStr, AnyDoorRunDto.class);
            JsonUtil.applyConfig(dto.getJsonTimezone(), dto.getJsonDateTimeFormat());
            return doRunForResult(dto, method, bean, startRun, endRun);
        } catch (Throwable error) {
            try { endRun.run(); } catch (Throwable ignored) { }
            return failedFuture(error);
        }
    }

    private CompletableFuture<Object> doRunForResult(AnyDoorRunDto dto, Method method, Object bean,
                                                      Runnable startRun, Runnable endRun) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getRunClassLoader(method, bean, original));
            String content = JsonUtil.toStrNotExc(dto.getContent());
            AnyDoorHandlerMethod handler = new AnyDoorHandlerMethod(bean, method);
            List<Map<String, Object>> values;
            int count = dto.getNum();
            if (JsonUtil.isJsonArray(content)) { values = JsonUtil.toMaps(content); count = values.size(); }
            else { values = new ArrayList<>(); values.add(JsonUtil.toMap(content)); }
            if (count < 1) throw new IllegalArgumentException("num must be greater than zero");

            CompletableFuture<Object> invocation;
            if (count == 1) {
                if (Objects.equals(dto.getSync(), true)) {
                    try { invocation = CompletableFuture.completedFuture(handler.invokeSync(startRun, values.get(0))); }
                    catch (Throwable error) { invocation = failedFuture(error); }
                } else invocation = handler.invokeAsync(startRun, values.get(0));
            } else if (Boolean.TRUE.equals(dto.getConcurrent())) {
                List<CompletableFuture<Object>> futures = handler.concurrentInvokeAsync(startRun, values, count,
                        resultLogConsumer(method.getName()), excLogConsumer(method.getName()));
                invocation = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .thenApply(ignored -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
            } else if (Objects.equals(dto.getSync(), true)) {
                try { invocation = CompletableFuture.completedFuture(handler.parallelInvokeSync(startRun, values, count, resultLogConsumer(method.getName()))); }
                catch (Throwable error) { invocation = failedFuture(error); }
            } else {
                final int runCount = count;
                invocation = CompletableFuture.supplyAsync(() -> handler.parallelInvokeSync(startRun, values, runCount, resultLogConsumer(method.getName())));
            }
            return invocation.whenComplete((result, error) -> {
                futureResultLogConsumer(method.getName()).accept(result, error);
                endRun.run();
            });
        } catch (Throwable error) {
            try { endRun.run(); } catch (Throwable ignored) { }
            return failedFuture(error);
        } finally { Thread.currentThread().setContextClassLoader(original); }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>(); future.completeExceptionally(error); return future;
    }

    private static ClassLoader getRunClassLoader(Method method, Object bean, ClassLoader oldClassLoader) {
        return AnyDoorClassUtil.executionClassLoader(bean, method, oldClassLoader);
    }
    
    public Object doRun(AnyDoorRunDto anyDoorDto, Method method, Object bean, Runnable startRun, Runnable endRun) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getRunClassLoader(method, bean, original));
            String content = JsonUtil.toStrNotExc(anyDoorDto.getContent());
            return handleAndRun(anyDoorDto, method, bean, startRun, endRun, content, method.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
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
                try {
                    Object result = handlerMethod.invokeSync(startRun, contentMaps.get(0));
                    System.out.println(ANY_DOOR_RUN_MARK + methodName + " return: " + JsonUtil.toContentStrNotExc(result));
                    return result;
                } finally { endRun.run(); }
            } else {
                handlerMethod.invokeAsync(startRun, contentMaps.get(0)).whenComplete(futureResultLogConsumer(methodName)).whenComplete((result, throwable) -> endRun.run());
                return null;
            }
        } else {
            if (Objects.equals(anyDoorDto.getSync(), true)) {
                try {
                    if (Boolean.TRUE.equals(anyDoorDto.getConcurrent())) {
                        List<CompletableFuture<Object>> futures = handlerMethod.concurrentInvokeAsync(startRun, contentMaps, num,
                                resultLogConsumer(methodName), excLogConsumer(methodName));
                        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
                    }
                    return handlerMethod.parallelInvokeSync(startRun, contentMaps, num, resultLogConsumer(methodName));
                } finally { endRun.run(); }
            } else if (Boolean.TRUE.equals(anyDoorDto.getConcurrent())) {
                List<CompletableFuture<Object>> completableFutures =
                        handlerMethod.concurrentInvokeAsync(startRun, contentMaps, num, resultLogConsumer(methodName), excLogConsumer(methodName));
                CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0])).whenComplete((result, throwable) -> endRun.run());
            } else {
                CompletableFuture<Void> voidCompletableFuture = handlerMethod.parallelInvokeAsync(startRun, contentMaps, num, resultLogConsumer(methodName));
                voidCompletableFuture.whenComplete((result, throwable) -> endRun.run());
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
