package com.enjoy.fix.patch;

import android.content.Context;
import android.os.Parcelable;
import android.text.TextUtils;


import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * @author Lance
 * @date 2019/4/3
 */
public class PatchExecutor extends Thread {


    public static void patch(Context context, String patch, PatchCallBack patchCallBack){
        DexClassLoader classLoader = new DexClassLoader(patch, context.getCacheDir
                ().getAbsolutePath(), null, context.getClass().getClassLoader());
        try {
            DexFile dexFile = new DexFile(patch);
            Enumeration<String> entries = dexFile.entries();
            while (entries.hasMoreElements()) {
                // 修复用的class 代理实现class
                String patchName = entries.nextElement();
                // 需要修复的class
                String className = patchName.substring(0, patchName.length() - "Patch".length());

                Class<?> fixClass = classLoader.loadClass(className);
                Field[] fields = fixClass.getDeclaredFields();
                Field proxyField = null;
                for (Field field : fields) {
                    //找到Proxy的属性
                    if (TextUtils.equals(field.getType().getCanonicalName(), Proxy
                            .class.getCanonicalName())) {
                        proxyField = field;
                        break;
                    }
                }
                if (proxyField == null) {
                    continue;
                }
                //
                Class<?> patchClass = classLoader.loadClass(patchName);
                Object patchObject = patchClass.newInstance();
                proxyField.setAccessible(true);
                proxyField.set(null, patchObject);
            }
            dexFile.close();
            patchCallBack.onPatchResult(true, patch);
        } catch (Exception e) {
            e.printStackTrace();
            patchCallBack.onPatchResult(false, patch);
        }
    }

}
