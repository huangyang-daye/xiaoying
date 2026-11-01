package com.xiaoying.studio;

import java.util.Map;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
public class ApiErrors {
    private ResponseEntity<?> response(int status,String code,String message) {
        return ResponseEntity.status(status).body(Map.of("errorCode",code,"message",message,"traceId",UUID.randomUUID().toString()));
    }
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e) { return response(e.status,e.code,e.getMessage()); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,ServletRequestBindingException.class})
    ResponseEntity<?> invalid(Exception e) { return response(400,"VALIDATION_ERROR","请求参数缺失、类型错误或超出允许范围"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> large(Exception e) { return response(413,"FILE_TOO_LARGE","文件超过上传大小限制"); }
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<?> missing(Exception e) { return response(404,"RESOURCE_NOT_FOUND","接口或资源不存在"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) ResponseEntity<?> method(Exception e) { return response(405,"METHOD_NOT_ALLOWED","请求方法不被支持"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class) ResponseEntity<?> media(Exception e) { return response(415,"UNSUPPORTED_MEDIA_TYPE","请求内容类型不被支持"); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected(Exception e) {
        LoggerFactory.getLogger(getClass()).error("Unhandled request error",e);
        return response(500,"INTERNAL_ERROR","服务暂时不可用，请稍后重试");
    }
}
