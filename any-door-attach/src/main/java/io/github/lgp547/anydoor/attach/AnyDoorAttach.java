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
 
package io.github.lgp547.anydoor.attach;

import com.taobao.arthas.common.FileUtils;
import io.github.lgp547.anydoor.common.dto.AnyDoorRunDto;
import io.github.lgp547.anydoor.common.util.AnyDoorClassUtil;
import io.github.lgp547.anydoor.common.util.AnyDoorClassloader;
import io.github.lgp547.anydoor.common.util.AnyDoorFileUtil;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;

public class AnyDoorAttach {
    
    public static void agentmain(String agentArgs, Instrumentation inst) {
        if (null == agentArgs || agentArgs.isEmpty()) {
            System.err.println("any_door agentmain param is empty");
            return;
        }
        
        if (agentArgs.startsWith("file://")) {
            try {
                agentArgs = AnyDoorFileUtil.getTextFileAsString(new File(URLDecoder.decode(agentArgs.substring(7), "UTF-8")));
            } catch (IOException e) {
                System.err.println("any_door agentmain error. agentArgs[" + agentArgs + "]");
                e.printStackTrace();
                return;
            }
        }
        
        AnyDoorRun(agentArgs, inst);
    }
    
    /**
     * {@code  io.github.lgp547.anydoor.core.AnyDoorService#run(String, Method, Object, Runnable)}
     */
    public static void AnyDoorRun(String anyDoorDtoStr, Instrumentation inst) {
        AnyDoorRunDto anyDoorRunDto = AnyDoorRunDto.parseObj(anyDoorDtoStr);
        if (!anyDoorRunDto.verifyPassByAttach()) {
            System.err.println("any_door agentmain error. anyDoorDtoStr[" + anyDoorDtoStr + "]");
            return;
        }
        
        AnyDoorClassloader anyDoorClassloader = null;
        try {
            anyDoorClassloader = new AnyDoorClassloader(anyDoorRunDto.getJarPaths());
            AnyDoorVmToolUtils.init();
            AnyDoorRuntimeResolver resolver = new AnyDoorRuntimeResolver(inst, anyDoorRunDto.isDiagnose());
            if (anyDoorRunDto.isDiagnose()) {
                try {
                    java.util.List<Class<?>> types = new java.util.ArrayList<>();
                    java.util.List<java.util.Map<String, String>> beans = new java.util.ArrayList<>();
                    String classFailure = null;
                    String springFailure = resolver.getInitializationWarning();
                    try { types = resolver.findTypes(anyDoorRunDto.getClassName(), anyDoorRunDto.getClassLoaderId()); }
                    catch (Throwable error) { classFailure = error.getClass().getName() + ": " + error.getMessage(); }
                    try { if (!types.isEmpty()) beans = resolver.listBeans(anyDoorRunDto); }
                    catch (Throwable error) { springFailure = error.getClass().getName() + ": " + error.getMessage(); }
                    Class<?> diagnosticType = anyDoorClassloader.loadClass("io.github.lgp547.anydoor.core.AnyDoorDiagnosticService");
                    Object diagnostic = diagnosticType.getConstructor().newInstance();
                    Object report = diagnosticType.getMethod("diagnose", String.class, java.util.List.class, java.util.List.class, String.class, String.class, String.class)
                            .invoke(diagnostic, anyDoorDtoStr, types, beans, "PID " + java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0], classFailure, springFailure);
                    Class<?> jsonUtil = anyDoorClassloader.loadClass("io.github.lgp547.anydoor.util.JsonUtil");
                    String json = (String) jsonUtil.getMethod("toJson", Object.class).invoke(null, report);
                    writeJsonResult(anyDoorRunDto.getResultPath(), json);
                } catch (Throwable error) { writeResult(anyDoorRunDto.getResultPath(), null, error); }
                finally { anyDoorClassloader.forceClose(); }
                return;
            }
            if (anyDoorRunDto.isListBeans()) {
                try {
                    // Serialize across the isolated core classloader using only JDK collections.
                    Object candidates = resolver.listBeans(anyDoorRunDto);
                    Class<?> jsonUtil = anyDoorClassloader.loadClass("io.github.lgp547.anydoor.util.JsonUtil");
                    Object json = jsonUtil.getMethod("toStrNotExc", Object.class).invoke(null, candidates);
                    writeResult(anyDoorRunDto.getResultPath(), json, null);
                } finally {
                    anyDoorClassloader.forceClose();
                }
                return;
            }
            Runnable startRun = getStartRunnable(inst, anyDoorRunDto);
            AnyDoorRuntimeResolver.Target target = resolver.resolve(anyDoorRunDto);
            Class<?> clazz = target.type;
            Method method = AnyDoorClassUtil.getMethod(clazz, anyDoorRunDto.getMethodName(), anyDoorRunDto.getParameterTypes());
            Object instance = target.bean;
            if (!Modifier.isPublic(method.getModifiers())) instance = io.github.lgp547.anydoor.common.util.AnyDoorAopUtil.getTargetObject(instance);
            
            Class<?> anyDoorServiceClass = anyDoorClassloader.loadClass("io.github.lgp547.anydoor.core.AnyDoorService");
            Object anyDoorService = anyDoorServiceClass.getConstructor().newInstance();
            Method run = anyDoorServiceClass.getMethod("runForResult", String.class, Method.class, Object.class, Runnable.class, Runnable.class);
            Runnable endRun = () -> { };
            CompletableFuture<?> result = (CompletableFuture<?>) run.invoke(anyDoorService, anyDoorDtoStr, method, instance, startRun, endRun);
            AnyDoorClassloader resultClassloader = anyDoorClassloader;
            result.whenComplete((value, failure) -> {
                try {
                    if (failure != null) writeResult(anyDoorRunDto.getResultPath(), null, failure);
                    else {
                        Class<?> jsonUtil = resultClassloader.loadClass("io.github.lgp547.anydoor.util.JsonUtil");
                        String resultJson = (String) jsonUtil.getMethod("toJson", Object.class).invoke(null, value);
                        writeJsonResult(anyDoorRunDto.getResultPath(), resultJson);
                    }
                } catch (Throwable serializationError) {
                    writeResult(anyDoorRunDto.getResultPath(), null, serializationError);
                } finally { resultClassloader.forceClose(); }
            });
        } catch (Throwable e) {
            if (anyDoorClassloader != null) anyDoorClassloader.forceClose();
            writeResult(anyDoorRunDto.getResultPath(), null, e);
            System.err.println("any_door agentmain error. anyDoorDtoStr[" + anyDoorDtoStr + "]");
            e.printStackTrace();
        }
    }

    private static void writeResult(String resultPath, Object result, Throwable error) {
        if (resultPath == null || resultPath.isEmpty()) return;
        try {
            String status = error == null ? "success" : "error";
            String payload = error == null ? String.valueOf(result) : errorDetails(error);
            String json = "{\"status\":\"" + status + "\",\"time\":" + Instant.now().toEpochMilli() + ",\""
                    + (error == null ? "result" : "error") + "\":\"" + escape(payload) + "\"}";
            writeAtomically(resultPath, json);
        } catch (Exception ignored) { }
    }

    private static void writeJsonResult(String resultPath, String resultJson) {
        if (resultPath == null || resultPath.isEmpty()) return;
        try {
            String json = "{\"status\":\"success\",\"time\":" + Instant.now().toEpochMilli() + ",\"resultJson\":" + resultJson + "}";
            writeAtomically(resultPath, json);
        } catch (Exception ignored) { }
    }

    private static void writeAtomically(String resultPath, String json) throws IOException {
        java.nio.file.Path target = Paths.get(resultPath);
        java.nio.file.Path temporary = Paths.get(resultPath + ".tmp");
        Files.write(temporary, json.getBytes(StandardCharsets.UTF_8));
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static String errorDetails(Throwable error) {
        Throwable root = error;
        while ((root instanceof InvocationTargetException || root.getClass().getName().contains("CompletionException")) && root.getCause() != null) root = root.getCause();
        StringWriter output = new StringWriter();
        root.printStackTrace(new PrintWriter(output));
        String value = output.toString();
        return value.length() > 40000 ? value.substring(0, 40000) + "\n… stack trace truncated" : value;
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 32);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': result.append("\\\\"); break;
                case '"': result.append("\\\""); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20) result.append(String.format("\\u%04x", (int) c)); else result.append(c);
            }
        }
        return result.toString();
    }
    
    private static Runnable getStartRunnable(Instrumentation inst, AnyDoorRunDto anyDoorRunDto) {
        Runnable runnable = () -> {
        };
        try {
            String className = AnyDoorRunDto.AnyDoorInjectedClassName;
            String baseJavaPath = AnyDoorRunDto.dataBaseJavaPath(anyDoorRunDto.getProjectBasePath());
            String javaFilePath = baseJavaPath + className + ".java";
            String classFilePath = baseJavaPath + className + ".class";
            
            File javaFile = new File(javaFilePath);
            if (!javaFile.exists()) {
                return runnable;
            }
            byte[] javaFileBytes = Files.readAllBytes(javaFile.toPath());
            String fileContent = new String(javaFileBytes, StandardCharsets.UTF_8);
            boolean needUpdatePreRun = fileContent.contains("AnyDoorIsUpdatePreRun:true");
            
            Class<?> cls = searchClass(inst, className);
            File classFile = new File(classFilePath);
            if (needUpdatePreRun || !classFile.exists()) {
                compilerJavaFile(javaFilePath);
            }
            
            if (needUpdatePreRun) {
                String newContent = fileContent.replace("AnyDoorIsUpdatePreRun:true", "AnyDoorIsUpdatePreRun:false");
                FileUtils.writeByteArrayToFile(javaFile, newContent.getBytes());
            }
            
            if (cls == null) {
                // 将编译后的class文件加载到内存中
                if (!classFile.exists()) {
                    return runnable;
                }
                URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{new File(baseJavaPath).toURI().toURL()});
                cls = Class.forName(className, true, urlClassLoader);
            }
            
            if (needUpdatePreRun) {
                byte[] bytes = Files.readAllBytes(classFile.toPath());
                inst.redefineClasses(new ClassDefinition(cls, bytes));
            }
            
            // 使用反射调用类的方法
            Object instance = cls.getDeclaredConstructor().newInstance();
            Method method = cls.getMethod("preRun");
            runnable = () -> {
                try {
                    method.invoke(instance);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
        }
        return runnable;
    }
    
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void compilerJavaFile(String javaPath) {
        new File(javaPath.replace(".java", ".class")).delete();
        
        // 获取Java编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("JavaCompiler not found");
        }
        // 编译文件
        int run = compiler.run(null, null, null, "-proc:none", javaPath);
        if (run != 0) {
            throw new RuntimeException("编译失败");
        }
    }
    
    public static Class<?> searchClass(Instrumentation inst, String className) {
        Class<?> targetClass = null;
        for (Class<?> loadedClass : inst.getAllLoadedClasses()) {
            if (loadedClass.getName().equals(className)) {
                targetClass = loadedClass;
                break;
            }
        }
        
        return targetClass;
    }
    
}
