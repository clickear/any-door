package io.github.lgp547.anydoorplugin.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.ui.EditorTextField;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public final class ParamValidationUtil {

    private static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private ParamValidationUtil() {
    }

    @Nullable
    public static ValidationInfo validate(String text, PsiParameterList parameterList, String dateTimeFormat, EditorTextField editor) {
        if (parameterList == null || parameterList.getParametersCount() == 0 || StringUtils.isBlank(text)) {
            return null;
        }
        JsonNode rootNode;
        try {
            rootNode = JsonUtil.objectMapper.readTree(text);
        } catch (Exception e) {
            return new ValidationInfo("JSON format error: " + e.getMessage(), editor);
        }
        String error = validateRoot(rootNode, parameterList, getDateTimeFormat(dateTimeFormat));
        if (error == null) {
            return null;
        }
        return new ValidationInfo(error, editor);
    }

    @Nullable
    private static String validateRoot(JsonNode rootNode, PsiParameterList parameterList, String dateTimeFormat) {
        if (rootNode == null || rootNode.isNull()) {
            return null;
        }
        if (rootNode.isArray()) {
            for (int i = 0; i < rootNode.size(); i++) {
                JsonNode item = rootNode.get(i);
                if (!item.isObject()) {
                    return "Batch param item [" + i + "] must be JSON object";
                }
                String error = validateObject(item, parameterList, dateTimeFormat, "[" + i + "]", new HashSet<>(), 0);
                if (error != null) {
                    return error;
                }
            }
            return null;
        }
        if (!rootNode.isObject()) {
            return "Param must be JSON object";
        }
        return validateObject(rootNode, parameterList, dateTimeFormat, "", new HashSet<>(), 0);
    }

    @Nullable
    private static String validateObject(JsonNode objectNode, PsiParameterList parameterList, String dateTimeFormat, String pathPrefix,
                                         Set<String> visitedClasses, int depth) {
        for (int i = 0; i < parameterList.getParametersCount(); i++) {
            PsiParameter parameter = Objects.requireNonNull(parameterList.getParameter(i));
            JsonNode valueNode = objectNode.get(parameter.getName());
            String error = validateValue(valueNode, parameter.getType(), joinPath(pathPrefix, parameter.getName()), dateTimeFormat, visitedClasses, depth);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    @Nullable
    private static String validateValue(JsonNode valueNode, PsiType type, String path, String dateTimeFormat,
                                        Set<String> visitedClasses, int depth) {
        if (valueNode == null || valueNode.isNull() || type == null) {
            return null;
        }

        String canonicalText = StringUtils.substringBefore(type.getCanonicalText(), "<");
        if (isDateLike(canonicalText)) {
            return validateDateLike(valueNode, canonicalText, path, dateTimeFormat);
        }

        if (type instanceof PsiArrayType) {
            if (!valueNode.isArray()) {
                return null;
            }
            PsiType componentType = ((PsiArrayType) type).getComponentType();
            for (int i = 0; i < valueNode.size(); i++) {
                String error = validateValue(valueNode.get(i), componentType, path + "[" + i + "]", dateTimeFormat, visitedClasses, depth + 1);
                if (error != null) {
                    return error;
                }
            }
            return null;
        }

        if (!(type instanceof PsiClassType)) {
            return null;
        }

        PsiClassType classType = (PsiClassType) type;
        PsiClass psiClass = classType.resolve();
        if (psiClass == null) {
            return null;
        }

        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }

        if (JsonElementUtil.isCollType(resolveClass(qualifiedName))) {
            if (!valueNode.isArray()) {
                return null;
            }
            PsiType[] parameters = classType.getParameters();
            if (parameters.length == 0) {
                return null;
            }
            for (int i = 0; i < valueNode.size(); i++) {
                String error = validateValue(valueNode.get(i), parameters[0], path + "[" + i + "]", dateTimeFormat, visitedClasses, depth + 1);
                if (error != null) {
                    return error;
                }
            }
            return null;
        }

        if (JsonElementUtil.isMapType(resolveClass(qualifiedName)) || psiClass.isEnum() || psiClass.isInterface() || !valueNode.isObject() || depth > 5) {
            return null;
        }

        if (!visitedClasses.add(qualifiedName)) {
            return null;
        }
        try {
            Iterator<com.intellij.psi.PsiField> iterator = java.util.Arrays.stream(psiClass.getAllFields()).iterator();
            while (iterator.hasNext()) {
                com.intellij.psi.PsiField field = iterator.next();
                if (field.hasModifierProperty("static")) {
                    continue;
                }
                JsonNode childNode = valueNode.get(field.getName());
                String error = validateValue(childNode, field.getType(), joinPath(path, field.getName()), dateTimeFormat, visitedClasses, depth + 1);
                if (error != null) {
                    return error;
                }
            }
            return null;
        } finally {
            visitedClasses.remove(qualifiedName);
        }
    }

    @Nullable
    private static String validateDateLike(JsonNode valueNode, String canonicalText, String path, String dateTimeFormat) {
        if (valueNode.isNumber()) {
            return null;
        }
        if (!valueNode.isTextual()) {
            return "Param [" + path + "] date format error, supported: timestamp, " + dateTimeFormat + ", yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd";
        }
        String text = valueNode.asText();
        if (StringUtils.isBlank(text)) {
            return null;
        }

        if (StringUtils.isNumeric(text)) {
            return null;
        }

        if (isJavaUtilDate(canonicalText)) {
            if (canParseLocalDateTime(text, dateTimeFormat) || canParseLocalDateTime(text, "yyyy-MM-dd'T'HH:mm:ss") || canParseLocalDate(text)) {
                return null;
            }
            return "Param [" + path + "] date format error, supported: timestamp, " + dateTimeFormat + ", yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd";
        }

        if ("java.time.LocalDateTime".equals(canonicalText)) {
            if (canParseLocalDateTime(text, dateTimeFormat) || canParseLocalDateTime(text, "yyyy-MM-dd'T'HH:mm:ss") || canParseLocalDate(text)) {
                return null;
            }
            return "Param [" + path + "] LocalDateTime format error, supported: timestamp, " + dateTimeFormat + ", yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd";
        }

        if ("java.time.LocalDate".equals(canonicalText)) {
            if (canParseLocalDate(text)) {
                return null;
            }
            return "Param [" + path + "] LocalDate format error, supported: yyyy-MM-dd";
        }

        return null;
    }

    private static boolean canParseLocalDateTime(String text, String pattern) {
        try {
            LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canParseLocalDate(String text) {
        try {
            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isDateLike(String canonicalText) {
        return isJavaUtilDate(canonicalText)
                || "java.time.LocalDateTime".equals(canonicalText)
                || "java.time.LocalDate".equals(canonicalText);
    }

    private static boolean isJavaUtilDate(String canonicalText) {
        return Date.class.getName().equals(canonicalText)
                || java.sql.Date.class.getName().equals(canonicalText)
                || Timestamp.class.getName().equals(canonicalText);
    }

    private static Class<?> resolveClass(String qualifiedName) {
        try {
            return Class.forName(qualifiedName);
        } catch (Exception e) {
            return Object.class;
        }
    }

    private static String joinPath(String prefix, String name) {
        if (StringUtils.isBlank(prefix)) {
            return name;
        }
        if (prefix.endsWith("]")) {
            return prefix + "." + name;
        }
        return prefix + "." + name;
    }

    private static String getDateTimeFormat(String dateTimeFormat) {
        return StringUtils.defaultIfBlank(dateTimeFormat, DEFAULT_DATETIME_FORMAT).trim();
    }
}
