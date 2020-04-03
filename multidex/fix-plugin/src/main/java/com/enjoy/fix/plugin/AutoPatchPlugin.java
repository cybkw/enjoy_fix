package com.enjoy.fix.plugin;

import com.android.build.gradle.BaseExtension;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Lance
 * @date 2019/4/3
 */
public class AutoPatchPlugin implements Plugin<Project> {


    private String toolDir;
    public static File dx;
    public static File hack;

    @Override
    public void apply(Project project) {
        //保存各种需要的文件
        toolDir = new File(project.getProjectDir(), "fix").getAbsolutePath();
        //将工具拷贝进入目录
        dx  = copyJarToFix("dx.jar");
        hack = copyJarToFix("hack.jar");

        BaseExtension android = project.getExtensions().getByType(BaseExtension.class);
        android.registerTransform(new AutoPatchTransform(project));

    }


    File copyJarToFix(String jarName) {
        File targetDir = new File(toolDir);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        InputStream inputStream = getClass().getResourceAsStream("/libs/" + jarName);
        File file = new File(toolDir, jarName);
        try {
            FileUtils.copyInputStreamToFile(inputStream, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
}
