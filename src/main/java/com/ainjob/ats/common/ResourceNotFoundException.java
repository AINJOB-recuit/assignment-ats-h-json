package com.ainjob.ats.common;

/** 요청한 리소스가 존재하지 않는 경우 → 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, long id) {
        super(resource + "(" + id + ")을(를) 찾을 수 없습니다.");
    }
}
