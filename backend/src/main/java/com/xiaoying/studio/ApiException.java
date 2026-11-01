package com.xiaoying.studio;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public static ApiException missing(String kind) { return new ApiException(404,kind+"_NOT_FOUND","资源不存在或不可访问"); }
    public static ApiException invalid(String message) { return new ApiException(400,"VALIDATION_ERROR",message); }
}
