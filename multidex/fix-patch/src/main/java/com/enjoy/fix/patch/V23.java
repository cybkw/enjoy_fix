package com.enjoy.fix.patch;

import android.content.SharedPreferences;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import dalvik.system.PathClassLoader;

public class V23 {

    /**
     *
     * @param classLoader
     * @param dexFiles  加载dex的文件 (sdcard)
     * @param optFile   优化后的dex保存的目录
     */
    public static void install(ClassLoader classLoader, List<File> dexFiles,File optFile){


        try {
            // 找到 DexPathList 这个属性
            Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");

            //获得pathList属性的值 （DexPathList对象）
            Object pathList = pathListField.get(classLoader);

            Field dexElementsField = ShareReflectUtil.findField(pathList, "dexElements");
            // 获得 dexElements 数组的对象 :Element的数组
            Object dexElements = dexElementsField.get(pathList);


            // 1、直接反射创建Element对象
            // 2、new一个Dex/PathClassLoader 加载dex文件，再反射获得 new出来的classloader中的 dexElements
            // 3、反射执行 makePathElements 创建 Element数组
            for (File dexFile : dexFiles) {
                PathClassLoader pathClassLoader = new PathClassLoader(dexFile.getAbsolutePath(),
                        null);
//              pathClassLoader: pathList : dexElements 反射获得这个dexElement
            }


            //将这个Element/Element数组 插入到 dexElements 之前

        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
