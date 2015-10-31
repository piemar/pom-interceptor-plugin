package com.baracuda.piepet.pominterceptor;

/**
 * Created by FPPE12 on 2015-10-31.
 */
public class POMElement {
    private String name;
    private String value;

    public POMElement(String name, String value) {
        this.name = name.toUpperCase();
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
