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
import io.github.lgp547.anydoor.util.JsonUtil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AnyDoorExpressionEvaluator {

    private static final String CLASS_NAME = "AnyDoorExpressionInvoker";

    public String evaluate(AnyDoorRunDto anyDoorRunDto, AnyDoorExpressionContext context) {
        if (anyDoorRunDto.getExpression() == null || anyDoorRunDto.getExpression().trim().isEmpty()) {
            throw new IllegalArgumentException("Expression is empty");
        }

        File javaDir = new File(AnyDoorRunDto.dataBaseJavaPath(anyDoorRunDto.getProjectBasePath()));
        if (!javaDir.exists() && !javaDir.mkdirs()) {
            throw new IllegalStateException("Failed to create expression work dir: " + javaDir.getAbsolutePath());
        }

        File javaFile = new File(javaDir, CLASS_NAME + ".java");
        File classFile = new File(javaDir, CLASS_NAME + ".class");
        writeJavaFile(javaFile, buildSource(anyDoorRunDto.getExpression()));
        compile(javaFile, classFile, currentClassPath(anyDoorRunDto));

        try (URLClassLoader loader = new URLClassLoader(new URL[]{javaDir.toURI().toURL()}, buildExpressionParentClassLoader())) {
            Class<?> invokerClass = Class.forName(CLASS_NAME, true, loader);
            Method method = invokerClass.getMethod("eval", AnyDoorExpressionContext.class);
            Object result = method.invoke(null, context);
            return JsonUtil.toStrNotExc(result);
        } catch (Exception e) {
            throw new IllegalStateException("Expression execution failed", e);
        }
    }

    private static ClassLoader buildExpressionParentClassLoader() {
        final ClassLoader appLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader coreLoader = AnyDoorExpressionContext.class.getClassLoader();
        return new ClassLoader(null) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass != null) {
                        return loadedClass;
                    }
                    try {
                        return super.loadClass(name, resolve);
                    } catch (ClassNotFoundException ignored) {
                        // Continue with application/core loaders for non-JDK classes.
                    }
                    if (appLoader != null) {
                        try {
                            return appLoader.loadClass(name);
                        } catch (ClassNotFoundException ignored) {
                            // Fallback to core loader.
                        }
                    }
                    if (coreLoader != null) {
                        return coreLoader.loadClass(name);
                    }
                    throw new ClassNotFoundException(name);
                }
            }
        };
    }

    private static void writeJavaFile(File javaFile, String source) {
        try {
            Files.write(javaFile.toPath(), source.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write expression java file", e);
        }
    }

    private static void compile(File javaFile, File classFile, String classPath) {
        if (classFile.exists() && !classFile.delete()) {
            throw new IllegalStateException("Failed to delete old expression class file");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JavaCompiler not found");
        }
        List<String> args = new ArrayList<>();
        args.add("-proc:none");
        if (classPath != null && !classPath.isEmpty()) {
            args.add("-classpath");
            args.add(classPath);
        }
        args.add(javaFile.getAbsolutePath());
        int result = compiler.run(null, null, null, args.toArray(new String[0]));
        if (result != 0) {
            throw new IllegalStateException("Expression helper compilation failed");
        }
    }

    private static String currentClassPath(AnyDoorRunDto anyDoorRunDto) {
        Set<String> classPathEntries = new LinkedHashSet<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof URLClassLoader) {
            URLClassLoader urlClassLoader = (URLClassLoader) loader;
            for (URL url : urlClassLoader.getURLs()) {
                classPathEntries.add(new File(url.getFile()).getAbsolutePath());
            }
        }
        if (anyDoorRunDto.getJarPaths() != null) {
            classPathEntries.addAll(anyDoorRunDto.getJarPaths());
        }
        String javaClassPath = System.getProperty("java.class.path");
        if (javaClassPath != null && !javaClassPath.isEmpty()) {
            for (String entry : javaClassPath.split(File.pathSeparator)) {
                if (entry != null && !entry.isEmpty()) {
                    classPathEntries.add(entry);
                }
            }
        }
        return String.join(File.pathSeparator, classPathEntries);
    }

    private static String buildSource(String expression) {
        String normalizedExpression = normalizeExpression(expression);
        return "import io.github.lgp547.anydoor.core.AnyDoorExpressionContext;\n" +
                "import java.util.Map;\n" +
                "public class " + CLASS_NAME + " {\n" +
                "    public static Object eval(AnyDoorExpressionContext context) {\n" +
                "        return " + normalizedExpression + ";\n" +
                "    }\n" +
                "    public static <T> T bean(AnyDoorExpressionContext context, Class<T> requiredType) {\n" +
                "        return context.bean(requiredType);\n" +
                "    }\n" +
                "    public static Object bean(AnyDoorExpressionContext context, String className) {\n" +
                "        return context.bean(className);\n" +
                "    }\n" +
                "}\n";
    }

    private static String normalizeExpression(String expression) {
        String result = expression;
        result = result.replaceAll("\\bbean\\s*\\(", CLASS_NAME + ".bean(context, ");
        result = result.replaceAll("\\bcurrentValue\\b", "context.currentValue()");
        result = result.replaceAll("\\btargetType\\b", "context.targetType()");
        result = result.replaceAll("\\brootParamName\\b", "context.rootParamName()");
        result = result.replaceAll("\\bjsonPath\\b", "context.jsonPath()");
        result = result.replaceAll("\\bargsByName\\b", "context.argsByName()");
        result = result.replaceAll("\\bargs\\b", "context.args()");
        return result;
    }
}
