package com.enjoy.enjoyfix;

import android.util.Log;


import com.enjoy.fix.patch.annotation.Modify;

import java.util.Arrays;



/**
 * @author Lance
 */
@Modify //要修复的类 打上这个，就能产生对应的补丁包
public class Bug {


    public static void test() {
        Log.e("Bug", "***********出Bug了***********");
//        a(1);
//        a(100.0f);
//        a(new int[]{1, 2, 3, 4, 5});
//        Log.e("Bug", "修复Bug咯，哈哈哈哈哈");
    }

    public static void a(int i) {
        System.out.println(i);
    }

    public static void a(float i) {
        System.out.println(i);
    }

    public static void a(int[] i) {
        System.out.println(Arrays.toString(i));
    }


}
