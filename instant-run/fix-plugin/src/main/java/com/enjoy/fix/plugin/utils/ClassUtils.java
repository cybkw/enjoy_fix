package com.enjoy.fix.plugin.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * @author Lance
 * @date 2019/4/4
 */
public class ClassUtils {


    public static void clone(CtClass src, CtClass dst) throws Exception {
        for (CtField field : src.getDeclaredFields()) {
            dst.addField(new CtField(field, dst));
        }


        //自己的方法都复制到新类
        for (CtMethod method : src.getDeclaredMethods()) {


            CtMethod newCtMethod = CtNewMethod.make(method.getModifiers(), method.getReturnType(),
                    method.getName(), method.getParameterTypes(),
                    method.getExceptionTypes(), null, dst);


            CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
            newCtMethod.getMethodInfo().setCodeAttribute(codeAttribute);

            dst.addMethod(newCtMethod);


        }

        //todo 要等dst有了方法后才好修改
        //修改src会影响到dst,应该是使用了同一份CodeAttribute (使用dst来 instrument 会出错)
        //但是不会影响到输出的src文件，因为我没有writeFile，后面再操作src的class也是重新load的
        for (CtMethod method : src.getDeclaredMethods()) {
            method.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    super.edit(m);
                    if (m.getClassName().equals(src.getName())) {
                        try {
                            System.out.println(m.getSignature());
                            if (Modifier.isStatic(m.getMethod().getModifiers())) {
                                //返回值不一样，写法不一样
                                if (m.getMethod().getReturnType() == CtClass.voidType) {
                                    m.replace(dst.getName() + "." + m.getMethodName() + "" +
                                            "($$);");
                                } else {
                                    m.replace("$_ =  " + dst.getName() + "." + m.getMethodName()
                                            + "" +
                                            "($$);");
                                }
                            } else {
                                System.out.println("调用自己方法：" + m.getMethodName());
                            }
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }


    }


    public static String boxClassName(String clsName) {
        switch (clsName) {
            case "int":
                return "java.lang.Integer";
            case "boolean":
                return "java.lang.Boolean";
            case "byte":
                return "java.lang.Byte";
            case "float":
                return "java.lang.Float";
            case "double":
                return "java.lang.Double";
            case "short":
                return "java.lang.Short";
            case "char":
                return "java.lang.Character";
            case "long":
                return "java.lang.Long";
            default:
                return clsName;
        }
    }

    static class A {
        String a(int a) {
            System.out.println(a);
            return "2";
        }
    }


    public static void main(String[] args) throws Exception {
        ClassPool classPool = ClassPool.getDefault();
        CtClass src = classPool.getCtClass("com.enjoy.fix.plugin.B");

        CtClass proxyClass = classPool.get("com.enjoy.fix.plugin.Proxy");

        String patchClassFullName = src.getName() + "Patch";
        CtClass proxyImplClass = classPool.makeClass(patchClassFullName);
        proxyImplClass.getClassFile().setMajorVersion(ClassFile.JAVA_7);

        clone(src, proxyImplClass);

        test(proxyClass, proxyImplClass, src);

        proxyImplClass.writeFile("/Users/xiang/enjoy/EnjoyFix/fix-plugin");


        proxyImplClass.defrost();
        CtClass ctClass1 = proxyImplClass.getClassPool().get("[Ljava.lang.String;");
        CtMethod mainMethod = CtNewMethod.make(Modifier.PUBLIC | Modifier.STATIC, CtClass
                        .voidType, "main", new CtClass[]{ctClass1}
                , null, "{ float[] a = new float[]{1, 2}; new com.enjoy.fix.plugin.BPatch().proxy" +
                        "(\"a\",new Object[]{a}); }",
                proxyImplClass);

        proxyImplClass.addMethod(mainMethod);
        proxyImplClass.writeFile("/Users/xiang/enjoy/EnjoyFix/fix-plugin");

    }

    private static void test(CtClass proxyClass, CtClass proxyImplClass, CtClass ctClass) throws
            NotFoundException, CannotCompileException {
        // 我们明确 接口只有一个方法要实现
        CtMethod method = proxyClass.getMethod("proxy", "(Ljava/lang/String;" +
                "[Ljava/lang/Object;" +
                ")Ljava/lang/Object;");


        //为了区分重载方法 所以value是list集合
        CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
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


                //命中则判断参数类型  todo 基本数据类型和数组需要注意 只用于equals
                String[] types = new String[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i].isArray()) {
//                        [F
//                        [Ljava.lang.String;
                        //获得是数组的类型 todo 不考虑数组嵌套数组
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
        //todo
        CtMethod newMethod = CtNewMethod.make(method.getReturnType(), method.getName(), method
                        .getParameterTypes()
                , method.getExceptionTypes(), body.toString(),
                proxyImplClass);

        proxyImplClass.addMethod(newMethod);


    }

}
