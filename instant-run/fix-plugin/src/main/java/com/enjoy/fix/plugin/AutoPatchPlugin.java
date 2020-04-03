package com.enjoy.fix.plugin;

import com.android.build.gradle.BaseExtension;
import com.enjoy.fix.plugin.utils.IOUtils;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Lance
 * @date 2019/4/3
 */
public class AutoPatchPlugin implements Plugin<Project> {


    private String toolDir;
    public static File dx;

    @Override
    public void apply(Project project) {
        //保存各种需要的文件
        toolDir = new File(project.getProjectDir(), "fix").getAbsolutePath();
        //将工具拷贝进入目录
        copyJarToFix("dx.jar");


        BaseExtension android = project.getExtensions().getByType(BaseExtension.class);
        android.registerTransform(new AutoPatchTransform(project));

    }


    void copyJarToFix(String jarName) {
        File targetDir = new File(toolDir);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        InputStream inputStream = getClass().getResourceAsStream("/libs/" + jarName);
        dx = new File(toolDir, jarName);
        try {
            OutputStream outputStream = new FileOutputStream(dx);
            IOUtils.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
