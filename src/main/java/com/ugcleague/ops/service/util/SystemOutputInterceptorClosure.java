package com.ugcleague.ops.service.util;

import groovy.lang.Closure;

import java.util.Arrays;

// here's where the magic happens allowing the service to capture the output while the script runs
public class SystemOutputInterceptorClosure extends Closure {

    StringBuffer stringBuffer = new StringBuffer();

    public SystemOutputInterceptorClosure(Object owner) {
        super(owner);
    }

    @Override
    public Object call(Object params) {
        stringBuffer.append(params);
        return false;
    }

    @Override
    public Object call(Object... args) {
        stringBuffer.append(Arrays.toString(args));
        return false;
    }

    public StringBuffer getStringBuffer() {
        return this.stringBuffer;
    }
}
