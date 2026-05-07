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

import io.github.lgp547.anydoor.common.util.AnyDoorSpringUtil;
import org.springframework.util.ClassUtils;

import java.util.Map;

public class AnyDoorExpressionContext {

    private final Object bean;
    private final Object[] args;
    private final Map<String, Object> argsByName;
    private final Object currentValue;
    private final Class<?> targetType;
    private final String rootParamName;
    private final String jsonPath;

    public AnyDoorExpressionContext(Object bean,
                                    Object[] args,
                                    Map<String, Object> argsByName,
                                    Object currentValue,
                                    Class<?> targetType,
                                    String rootParamName,
                                    String jsonPath) {
        this.bean = bean;
        this.args = args;
        this.argsByName = argsByName;
        this.currentValue = currentValue;
        this.targetType = targetType;
        this.rootParamName = rootParamName;
        this.jsonPath = jsonPath;
    }

    public Object bean() {
        return bean;
    }

    public Object[] args() {
        return args;
    }

    public Map<String, Object> argsByName() {
        return argsByName;
    }

    public Object currentValue() {
        return currentValue;
    }

    public Class<?> targetType() {
        return targetType;
    }

    public String rootParamName() {
        return rootParamName;
    }

    public String jsonPath() {
        return jsonPath;
    }

    public <T> T bean(Class<T> requiredType) {
        return AnyDoorSpringUtil.getBean(requiredType);
    }

    public Object bean(String className) {
        try {
            Class<?> requiredType = ClassUtils.forName(className, bean != null ? bean.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader());
            return AnyDoorSpringUtil.getBean(requiredType);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Bean class not found: " + className, e);
        }
    }
}
