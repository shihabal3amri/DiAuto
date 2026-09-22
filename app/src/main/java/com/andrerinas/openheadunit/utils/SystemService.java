package com.andrerinas.openheadunit.utils;

import android.app.Service;
import android.content.Context;
import android.os.IBinder;

import java.lang.reflect.Method;

public class SystemService {

    public static IBinder get(String name) throws Exception {
        final Class<?> localClass = Class.forName("android.os.ServiceManager");
        final Method getService = localClass.getMethod("getService", String.class);
        final Object result = getService.invoke(localClass, name);

        return (IBinder) result;
    }
}
