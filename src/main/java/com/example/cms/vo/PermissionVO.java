package com.example.cms.vo;

import java.io.Serializable;

/**
 * 权限点视图对象。
 */
public class PermissionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String permName;
    private String permCode;
    private String uri;
    private String method;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPermName() {
        return permName;
    }

    public void setPermName(String permName) {
        this.permName = permName;
    }

    public String getPermCode() {
        return permCode;
    }

    public void setPermCode(String permCode) {
        this.permCode = permCode;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}
