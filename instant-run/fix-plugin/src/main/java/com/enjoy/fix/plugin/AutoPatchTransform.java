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
import com.android.ddmlib.Log;
import com.android.utils.FileUtils;
import com.enjoy.fix.plugin.utils.AnnotationUtils;
import com.enjoy.fix.plugin.utils.ClassUtils;
import com.google.common.collect.FluentIterable;
import com.squareup.javapoet.MethodSpec;

import org.codehaus.groovy.tools.StringHelper;
import org.gradle.api.Project;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.lang.model.element.ExecutableElement;

import groovyjarjarantlr.StringUtils;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;
import javassist.bytecode.Descriptor;

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
        //所有的输入
        for (TransformInput input : inputs) {
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
            insertDirectory(directoryInputs, allClass, classPool);

            Collection<JarInput> jarInputs = input.getJarInputs();
            insertJar(jarInputs, allClass, classPool);
        }


        /**
         * 先生成补丁包再插桩
         */
        autoPatch(allClass);


        File jarFile = outputProvider.getContentLocation("main", getOutputTypes(), getScopes(),
                Format.JAR);
        JarOutputStream outStream = new JarOutputStream(new FileOutputStream(jarFile));

        //在所有类的每个方法中执行插桩
        try {
            CtClass proxyClass = classPool.get("com.enjoy.fix.patch.Proxy");
            //系统类和框架不需要插桩
            for (CtClass ctClass : allClass) {
                if (!ctClass.getPackageName().startsWith("android") &&
                        !ctClass.getPackageName().startsWith("com.enjoy.fix.patch")) {

                    CtField ctField = new CtField(proxyClass, "proxy", ctClass);
                    ctField.setModifiers(AccessFlag.PUBLIC | AccessFlag.STATIC);
                    ctClass.addField(ctField);
                    //将方法逻辑修改
                    CtMethod[] methods = ctClass.getDeclaredMethods();
                    for (CtMethod method : methods) {

                        project.getLogger().quiet("执行插桩:" + method.getLongName());
                        CtClass returnType = method.getReturnType();
                        //是否static
                        boolean isStatic = (method.getModifiers() & AccessFlag.STATIC) != 0;


                        StringBuilder body = new StringBuilder();


                        body.append("if(proxy != null)");
                        body.append("{");
                        body.append("Object argThis = null;");
                        if (!isStatic) {
                            // $0 表示 this
                            body.append("argThis = $0;");
                        }

                        if ("void".equals(returnType.getName())) {
                            body.append(" proxy.proxy");

                            body.append("(\"");
                            body.append(method.getName());
                            body.append("\",");
                            body.append("$args");
                            body.append(");");

                            body.append("return;");
                        } else if ("java.lang.Void".equals(returnType.getName())) {
                            body.append(" proxy.proxy");

                            body.append("(\"");
                            body.append(method.getName());
                            body.append("\",");
                            body.append("$args");
                            body.append(");");

                            body.append("return null;");
                        } else {
                            body.append("return (");
                            body.append(method.getReturnType().getName());
                            body.append(")");
                            body.append(" proxy.proxy");
                            //todo 参数
                            body.append("(\"");
                            body.append(method.getName());
                            body.append("\",");
                            body.append("$args");
                            body.append(");");
                        }
                        body.append("}");
                        method.insertBefore(body.toString());
                    }
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

    }

    private void autoPatch(List<CtClass> allClass) {
        //获得输出目录
        File buildDir = project.getBuildDir();
        String patchPath = buildDir.getAbsolutePath() + File.separator + "outputs/fix" + File
                .separator;

        try {
            FileUtils.cleanOutputDir(new File(patchPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //读取注解
        List<CtClass> modifies = AnnotationUtils.readAnnotation(allClass);
        try {
            generatPatch(allClass.get(0).getClassPool(), modifies, patchPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void generatPatch(ClassPool classPool, List<CtClass> modifies, String
            patchPath) throws Exception {
        File jarFile = new File(patchPath, "fix.jar");
        FileUtils.deleteIfExists(jarFile);
        JarOutputStream outStream = new JarOutputStream(new FileOutputStream(jarFile));


        CtClass proxyClass = classPool.get("com.enjoy.fix.patch.Proxy");

        for (CtClass bugClass : modifies) {
            /**
             * 创建Proxy的实现类,并在类中插入对应的属性
             */
            String patchClassFullName = bugClass.getName() + "Patch";
            CtClass proxyImplClass = classPool.makeClass(patchClassFullName);
            proxyImplClass.getClassFile().setMajorVersion(ClassFile.JAVA_7);
            //实现 Proxy接口
            proxyImplClass.addInterface(proxyClass);

            /**
             * 把需要修复的类中的方法都复制到新类中
             */
            ClassUtils.clone(bugClass, proxyImplClass);


            /**
             * 需要实现的方法
             */

            //为了区分重载方法 所以value是list集合
            CtMethod[] declaredMethods = proxyImplClass.getDeclaredMethods();
            Map<String, List<CtMethod>> methods = new HashMap<>();
            for (CtMethod declaredMethod : declaredMethods) {
                List<CtMethod> ctMethods = methods.get(declaredMethod.getName());
                if (ctMethods == null) {
                    ctMethods = new ArrayList<>();
                    methods.put(declaredMethod.getName(), ctMethods);
                }
                ctMethods.add(declaredMethod);
            }

            StringBuilder body = new StringBuilder("{");

            /**
             * 用于判断重载方法
             */
            body.append("String[] types = new String[$2.length];");
            body.append("for(int i = 0; i < $2.length;i++){");
            body.append("types[i] = $2[i].getClass().getName();");
            body.append("}");

            for (String methodName : methods.keySet()) {

                body.append(" if($1.equals(\"");
                body.append(methodName);
                body.append("\")){");

                List<CtMethod> ctMethods = methods.get(methodName);

                //重载方法则判断参数数量与参数类型
                for (CtMethod ctMethod : ctMethods) {
                    CtClass[] parameterTypes = ctMethod.getParameterTypes();


                    //首先判断参数数量
                    body.append(" if($2.length == ");
                    body.append(parameterTypes.length);
                    body.append("){  ");


                    //命中则判断参数类型  todo 基本数据类型和数组需要注意 types只用于equals
                    String[] types = new String[parameterTypes.length];
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (parameterTypes[i].isArray()) {
//                        [F
//                        [Ljava.lang.String;
                            //todo 获得是数组的类型  不考虑数组嵌套数组
                            CtClass componentType = parameterTypes[i].getComponentType();
                            if (componentType.isPrimitive()) {
                                //基本数据类型
                                String name = "[" + componentType.getName().substring(0, 1)
                                        .toUpperCase();
                                types[i] = name;
                            } else {
                                types[i] = "[L" + componentType.getName() + ";";
                            }

                        } else {
                            types[i] = parameterTypes[i].getName();
                        }
                    }


                    if (types.length != 0) {
                        body.append(" if(");
                    }
                    for (int i = 0; i < types.length; i++) {
                        body.append("types[");
                        body.append(i);
                        body.append("].equals(");
                        body.append("\"");
                        body.append(ClassUtils.boxClassName(types[i]));
                        body.append("\"");
                        body.append(")");
                        body.append(" && ");
                    }
                    if (types.length != 0) {
                        body.delete(body.length() - 4, body.length());
                        body.append("){ ");
                    }

                    CtClass returnType = ctMethod.getReturnType();
                    boolean isVoid = returnType.getName().equals("void");


                    //将变量类型转为匹配的
                    for (int i = 0; i < types.length; i++) {
                        String type = ClassUtils.boxClassName(parameterTypes[i].getName());
                        body.append(type);
                        body.append(" i");
                        body.append(i);
                        body.append(" = (");
                        body.append(type);
                        body.append(")");
                        body.append("$2[");
                        body.append(i);
                        body.append("];");
                    }


                    if (!isVoid) {
                        body.append("return ");
                    }
                    body.append(methodName);
                    body.append("(");
                    for (int i = 0; i < types.length; i++) {
                        body.append("i");
                        body.append(i);
                        //todo 你不得不这么做
                        //如果是基本数据类型 调用xxValue
                        if (parameterTypes[i].isPrimitive()) {
                            body.append(".");
                            body.append(types[i]);
                            body.append("Value()");
                        }
                        body.append(",");
                    }


                    if (types.length != 0) {
                        //去掉最后一个,
                        body.deleteCharAt(body.length() - 1);
                    }
                    body.append(");");

                    if (types.length != 0) {
                        body.append(" } ");
                    }

                    body.append("}");
                }

                body.append("}");
            }
            body.append("return null;");

            body.append("}");


            System.out.println(body.toString());

            // 我们明确 接口只有一个方法要实现
            CtMethod method = proxyClass.getMethod("proxy", "(Ljava/lang/String;" +
                    "[Ljava/lang/Object;" +
                    ")Ljava/lang/Object;");
            //todo
            CtMethod newMethod = CtNewMethod.make(method.getReturnType(), method.getName(), method
                            .getParameterTypes()
                    , method.getExceptionTypes(), body.toString(),
                    proxyImplClass);

            proxyImplClass.addMethod(newMethod);


            proxyImplClass.writeFile(patchPath);

            JarEntry jarEntry = new JarEntry(proxyImplClass.getName().replaceAll("\\.", "/") + "" +
                    ".class");
            outStream.putNextEntry(jarEntry);
            outStream.write(proxyImplClass.toBytecode());
        }
        outStream.close();


        Process process = Runtime.getRuntime().exec("java -jar " + AutoPatchPlugin.dx
                .getAbsolutePath() + " " +
                "--dex --output=" + patchPath + "/fix.dex " + jarFile.getAbsolutePath());

        InputStream stderr = process.getErrorStream();
        InputStreamReader isr = new InputStreamReader(stderr);
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
            project.getLogger().quiet(line);
        }
        int exitVal = process.waitFor();
        project.getLogger().quiet("exec dx :" + exitVal);
        process.destroy();

    }


    private void insertDirectory(Collection<DirectoryInput> directoryInputs, List<CtClass>
            allClass, ClassPool classPool) {
        for (DirectoryInput directoryInput : directoryInputs) {
            File dirFile = directoryInput.getFile();
            try {
                project.getLogger().quiet("add classpath:" + dirFile.getAbsolutePath());
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
                project.getLogger().quiet("add classpath:" + file.getAbsolutePath());
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



}
