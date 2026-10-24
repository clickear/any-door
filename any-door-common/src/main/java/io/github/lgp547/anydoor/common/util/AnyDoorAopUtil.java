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
 
package io.github.lgp547.anydoor.common.util;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

public class AnyDoorAopUtil {
    private static final String SPRING_PROXY = "org.springframework.aop.SpringProxy";
    private static final String TARGET_CLASS_AWARE = "org.springframework.aop.TargetClassAware";
    private static final String ADVISED = "org.springframework.aop.framework.Advised";
    private static final String TARGET_SOURCE = "org.springframework.aop.TargetSource";
    
    public static Class<?> getTargetClass(Object candidate) {
        Objects.requireNonNull(candidate, "Candidate object must not be null");
        if (AnyDoorReflectionUtil.isInstance(candidate, TARGET_CLASS_AWARE)) {
            Class<?> type = (Class<?>) AnyDoorReflectionUtil.call(candidate, TARGET_CLASS_AWARE, "getTargetClass");
            if (type != null) return type;
        }
        return isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass();
    }
    
    public static boolean isCglibProxy(Object object) {
        return AnyDoorReflectionUtil.isInstance(object, SPRING_PROXY) && object.getClass().getName().contains("$$");
    }
    
    public static boolean isAopProxy(Object object) {
        return AnyDoorReflectionUtil.isInstance(object, SPRING_PROXY)
                && (Proxy.isProxyClass(object.getClass()) || isCglibProxy(object));
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T getTargetObject(Object candidate) {
        Objects.requireNonNull(candidate, "Candidate must not be null");
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        while (isAopProxy(candidate) && AnyDoorReflectionUtil.isInstance(candidate, ADVISED)) {
            if (!visited.add(candidate)) throw new IllegalStateException("Circular Spring proxy target");
            Object source = AnyDoorReflectionUtil.call(candidate, ADVISED, "getTargetSource");
            Object target = AnyDoorReflectionUtil.call(source, TARGET_SOURCE, "getTarget");
            if (target == null) throw new IllegalStateException("Selected Spring proxy has no target object");
            candidate = target;
        }
        return (T) candidate;
    }
}
