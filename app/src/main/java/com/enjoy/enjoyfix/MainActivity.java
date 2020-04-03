package com.enjoy.enjoyfix;

import android.app.Activity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;


import com.enjoy.fix.patch.PatchCallBack;
import com.enjoy.fix.patch.PatchExecutor;

import java.io.IOException;
import java.util.Enumeration;

import dalvik.system.DexFile;


public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //PathClassLoader
        ClassLoader classLoader = getClassLoader();

        //classLoader： element=》[classes.dex，fix.dex]


        //BootClassLoader
        ClassLoader classLoader1 = Activity.class.getClassLoader();

        System.out.println("getClassLoader:"+classLoader);
        System.out.println("getClassLoader 的父亲 :"+classLoader.getParent());
        System.out.println("Activity.class :"+classLoader1);


//        try {
//            DexFile dexFile = new DexFile("/xxx/xx.dex");
//            Enumeration<String> entries = dexFile.entries();
//            //遍历  dex中所有的Class
//            while (entries.hasMoreElements()){
//                String clsName = entries.nextElement();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        // 演示classloader
//
//        try {
//            Class<?> aClass = classLoader.loadClass("com.enjoy.enjoyfix.BugPatch");
//            System.out.println(aClass);
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        try {
//            classLoader.loadClass("android.app.Activity");
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        /**
//         * 1、dex路径
//         */
//        PathClassLoader pathClassLoader = new PathClassLoader("/sdcard/fix.dex", getClassLoader());
//
//        // /data/data/packagename : 私有目录
//        // 2: dex优化为odex之后保存的目录，必须是私有目录，不能是sd卡的目录
//        DexClassLoader dexClassLoader = new DexClassLoader("/sdcard/fix.dex", getCodeCacheDir().getAbsolutePath(), null, getClassLoader());
//
//
//        try {
//            Class<?> aClass = pathClassLoader.loadClass("com.enjoy.enjoyfix.BugPatch");
//            System.out.println(aClass);
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }
//
//        try {
//            Class<?> aClass = dexClassLoader.loadClass("com.enjoy.enjoyfix.BugPatch");
//            System.out.println(aClass);
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//        }


    }

    public void test(View view) {
        Bug.test();
    }

    public void fix(View view) {
        PatchExecutor.patch(this, "/sdcard/fix.dex", new PatchCallBack() {
            @Override
            public void onPatchResult(boolean result, String patch) {
                System.out.println("修复结果:"+result);
            }
        });
    }


}
