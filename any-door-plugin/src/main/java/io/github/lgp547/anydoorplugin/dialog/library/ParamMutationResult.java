package io.github.lgp547.anydoorplugin.dialog.library;

public record ParamMutationResult(boolean success, String message) {

    public static ParamMutationResult ok() {
        return new ParamMutationResult(true, null);
    }

    public static ParamMutationResult failure(String message) {
        return new ParamMutationResult(false, message);
    }
}
