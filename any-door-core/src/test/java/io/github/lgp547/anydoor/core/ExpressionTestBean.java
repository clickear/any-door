package io.github.lgp547.anydoor.core;

public class ExpressionTestBean {

    public String updateCity(ExpressionTestUser user) {
        return user == null ? null : user.getName();
    }
}
