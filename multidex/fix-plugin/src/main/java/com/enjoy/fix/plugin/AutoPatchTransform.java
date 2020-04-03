package com.enjoy.fix.plugin;

import com.android.SdkConstants;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.google.common.collect.FluentIterable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.gradle.api.Project;
import org.gradle.internal.impldep.com.amazonaws.util.Md5Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.NotFoundException;

/**
 * @author Lance
 * @date 2019/4/3
 */
public class AutoPatchTransform extends Transform {

    private BaseExtension android;
    private Project project;


    public AutoPatchTransform(Project project) {
        this.project = project;
        android = project.getExtensions().getByType(BaseExtension.class);
    }

    @Override
    public String getName() {
        return "dexPatch";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException,
            InterruptedException, IOException {
        super.transform(transformInvocation);
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        outputProvider.deleteAll();

        ClassPool classPool = new ClassPool();

        //把 android.jar 中的内容加入class池
        for (File file : android.getBootClasspath()) {
            try {
                project.getLogger().quiet("add classpath:" + file.getAbsolutePath());
                classPool.insertClassPath(file.getAbsolutePath());
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }


        Collection<TransformInput> inputs = transformInvocation.getInputs();
        List<CtClass> allClass = new ArrayList<>();
        //所有的类
        for (TransformInput input : inputs) {
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
            insertDirectory(directoryInputs, allClass, classPool);

            Collection<JarInput> jarInputs = input.getJarInputs();
            insertJar(jarInputs, allClass, classPool);
        }

        try {
            classPool.insertClassPath(AutoPatchPlugin.hack.getAbsolutePath());
        } catch (NotFoundException e) {
            e.printStackTrace();
        }

        //输出
        String patchPath = this.project.getBuildDir().getAbsolutePath() + "/outputs/fix/";
        this.project.getLogger().quiet("outDir is " + patchPath);
        File jarFile = outputProvider.getContentLocation("main", getOutputTypes(), getScopes(),
                Format.JAR);
        JarOutputStream outStream = new JarOutputStream(new FileOutputStream(jarFile));

        File md5CacheFile = new File(patchPath, "classCache");
        PatchGenerator patchGenerator = new PatchGenerator(md5CacheFile);

        //在所有类的每个方法中执行插桩
        try {
            //系统类、Application、接口、框架不需要插桩
            CtClass application = classPool.get("android.app.Application");
            for (CtClass ctClass : allClass) {
                boolean isApplication = ctClass.subclassOf(application);
//                System.out.println(ctClass.getName());
                if (!ctClass.getName().startsWith("android")
                        && !ctClass.getName().startsWith("com.enjoy.fix.patch")
                        && !isApplication && !ctClass.isInterface()) {
                    //让他可编辑
                    ctClass.defrost();
                    //获得构造函数
                    CtConstructor[] constructors = ctClass.getConstructors();
                    CtConstructor constructor = constructors[0];
                    constructor.insertAfter("if(com.enjoy.fix.patch.ClassVerifier" +
                            ".PREVENT_VERIFY){System.out.println(com.enjoy.fix.hack.AntilazyLoad" +
                            ".class);}");
                    patchGenerator.putClass(ctClass);
                }
                JarEntry jarEntry = new JarEntry(ctClass.getName().replaceAll("\\.", "/") + "" +
                        ".class");
                outStream.putNextEntry(jarEntry);
                outStream.write(ctClass.toBytecode());
                outStream.closeEntry();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        outStream.close();

        //生成补丁包
        try {
            patchGenerator.generatePatch();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //保存新的md5 cache
        patchGenerator.saveCache();
    }


    private void insertDirectory(Collection<DirectoryInput> directoryInputs, List<CtClass>
            allClass, ClassPool classPool) {
        for (DirectoryInput directoryInput : directoryInputs) {
            File dirFile = directoryInput.getFile();
            try {
                project.getLogger().quiet("add directory classpath:" + dirFile.getAbsolutePath());
                classPool.insertClassPath(dirFile.getAbsolutePath());
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
            //得到目录下所有file
            FluentIterable<File> files = FileUtils.getAllFiles(dirFile);
            for (File file : files) {
                //去掉目录之后的文件路径
                String filename = FileUtils.relativePath(file, dirFile);
                //是class文件
                if (filename.endsWith(SdkConstants.DOT_CLASS)) {
                    //去掉.class后缀
                    String className = filename.substring(0, filename.length() -
                            SdkConstants.DOT_CLASS.length());
                    //把 / 变成 .
                    className = className.replaceAll(Matcher.quoteReplacement(File.separator), ".");
                    try {
                        allClass.add(classPool.get(className));
                    } catch (NotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    private void insertJar(Collection<JarInput> jarInputs, List<CtClass>
            allClass, ClassPool classPool) {
        for (JarInput jarInput : jarInputs) {
            File file = jarInput.getFile();
            try {
                project.getLogger().quiet("add jar classpath:" + file.getAbsolutePath());
                classPool.insertClassPath(file.getAbsolutePath());
            } catch (NotFoundException e) {
                e.printStackTrace();
            }

            try {
                JarFile jarFile = new JarFile(file);
                Enumeration<JarEntry> classes = jarFile.entries();
                while (classes.hasMoreElements()) {
                    JarEntry libClass = classes.nextElement();
                    String className = libClass.getName();
                    if (className.endsWith(SdkConstants.DOT_CLASS)) {
                        className = className.substring(0, className.length() - SdkConstants
                                .DOT_CLASS.length()).replaceAll("/", ".");
                        try {
                            allClass.add(classPool.get(className));
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public static void main(String[] args) throws NotFoundException {

    }

}
