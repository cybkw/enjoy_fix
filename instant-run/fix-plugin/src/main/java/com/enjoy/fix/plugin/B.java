package com.enjoy.fix.plugin;

import com.android.ddmlib.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Lance
 * @date 2019/4/10
 */
public class B {

    public static void test() {
        a(1);
        a(100.0f);
        Log.e("Bug", "修复Bug咯，哈哈哈哈哈");
    }

    public static void a(int i) {
        System.out.println(i);
    }

    public static void a(float i) {
        System.out.println(i);
    }

    public static void a(float[] i) {
        System.out.println(i);
    }

    public static void a(byte[] i) {
        System.out.println(i);
    }

    public static void a(List<String> i) {
    }


//    public static void main(String[] arg) {
//        float[] a = new float[]{1, 2};
//        int[] b = new int[]{1, 2};
//        byte[] c = new byte[]{1, 2};
//        List<String> i = new ArrayList<>();
//        Object[] o = new Object[]{12, 3};
//        Object[] objects = new Object[]{a, b, c, arg, i, o};
//        for (Object object : objects) {
//            System.out.println(object.getClass().getName());
//        }
//
////        b.proxy("main", new Object[]{c});
//    }

//    public Object proxy(String var1, Object[] var2) {
//        String[] pTypes = new String[var2.length];
//
//        for (int i = 0; i < var2.length; ++i) {
//            System.out.println(var2[i].getClass().getName());
//            pTypes[i] = var2[i].getClass().getName();
//        }
//
//        if (var1.equals("c") && var2.length == 1) {
//            if (pTypes[0].equals("java.lang.float")) {
//                c((float) var2[0]);
//            }
////            if (pTypes[0].equals("java.lang.Integer")) {
////                c((int) var2[0]);
////            }
//        }
//
//        return null;
//    }


}
