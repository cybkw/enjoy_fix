package com.enjoy.enjoyfix;

import android.app.Application;
import android.content.Context;

import com.enjoy.fix.patch.EnjoyFix;

import java.io.File;


public class MyApplication extends Application {


    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        EnjoyFix.init(this);

        // 写到 SplashActivity/WelcomActivity 中
        // 1、请求服务器
        // 2、下载补丁包
        EnjoyFix.installPatch(this, new File("/sdcard/fix.dex"));


    }
}
