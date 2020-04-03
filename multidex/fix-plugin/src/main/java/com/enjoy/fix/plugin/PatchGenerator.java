package com.enjoy.fix.plugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javassist.CannotCompileException;
import javassist.CtClass;

/**
 * @author Lance
 * @date 2019/4/12
 */
public class PatchGenerator {

    private boolean needGenerate;
    private File md5CacheFile;
    private Gson gson = new Gson();
    private Map<String, String> oldMd5Map;
    private Map<String, String> newMd5Map = new HashMap<>();
    private JarOutputStream patchJarOs;
    private File patchJarFile;

    public PatchGenerator(File md5CacheFile) {
        this.md5CacheFile = md5CacheFile;
        needGenerate = md5CacheFile.exists();
        if (needGenerate) {
            try {
                String content = FileUtils.readFileToString(md5CacheFile);
                oldMd5Map = gson.fromJson(content, new TypeToken<Map<String,
                        String>>() {
                }.getType());
                patchJarFile = new File(md5CacheFile.getParentFile(), "patch.jar");
                patchJarOs = new JarOutputStream(new FileOutputStream(patchJarFile));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public void putClass(CtClass ctClass) throws IOException, CannotCompileException {
        byte[] byteCode = ctClass.toBytecode();
        String md5 = DigestUtils.md5Hex(byteCode);
        newMd5Map.put(ctClass.getName(), md5);
        System.out.println(ctClass.getName()+" md5 is "+ md5);
        JarEntry jarEntry = new JarEntry(ctClass.getName().replaceAll("\\.", "/") + "" +
                ".class");
        if (needGenerate) {
            if (oldMd5Map.containsKey(ctClass.getName())) {
                String oldMd5 = oldMd5Map.get(ctClass.getName());
                if (!md5.equals(oldMd5)) {
                    patchJarOs.putNextEntry(jarEntry);
                    patchJarOs.write(byteCode);
                    patchJarOs.closeEntry();
                }
            } else {
                //不存在 表示新增的
                patchJarOs.putNextEntry(jarEntry);
                patchJarOs.write(byteCode);
                patchJarOs.closeEntry();
            }
        }
    }

    public void generatePatch() throws Exception {
        if (needGenerate) {
            try {
                patchJarOs.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            Process process = Runtime.getRuntime().exec("java -jar " + AutoPatchPlugin.dx
                    .getAbsolutePath() + " " +
                    "--dex --output=" + patchJarFile.getParentFile().getAbsolutePath() + "/fix" +
                    ".dex " + patchJarFile.getAbsolutePath());

            process.waitFor();
            int exitVal = process.exitValue();
            process.destroy();
            if (exitVal != 0) {
                throw new Exception("generate patch error!");
            }
            System.err.println("patch generated in : "+patchJarFile.getParentFile().getAbsolutePath() + "/fix" +
                    ".dex ");
        }

    }

    public void saveCache() {
        String content = gson.toJson(newMd5Map);
        try {
            com.android.utils.FileUtils.writeToFile(md5CacheFile, content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
