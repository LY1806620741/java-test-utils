package io.github.ly1806620741.testsupport.coverage;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
public class JacocoSkip {

    public static String basePackage = "io.ly1806620741";

    public void doCover() throws IOException, ClassNotFoundException {
        List<Class<?>> classes = listAllLoanClasses();
        classes.forEach(clazz -> {
            for (Field declaredField : clazz.getDeclaredFields()) {
                if ("$jacocoData".equals(declaredField.getName())) {
                    try {
                        declaredField.setAccessible(true);
                        Object o = declaredField.get(clazz);
                        if (o != null) {
                            boolean[] data = (boolean[]) o;
                            Arrays.fill(data, true);
                            log.info("强制覆盖 {} {}", clazz.getName(), data);
                        } else {
                            boolean init = false;
                            for (Method declaredMethod : clazz.getDeclaredMethods()) {
                                if (declaredMethod.getName().equals("$jacocoInit")) {
                                    declaredMethod.setAccessible(true);
                                    declaredMethod.invoke(clazz);
                                    declaredField.setAccessible(true);
                                    o = declaredField.get(clazz);
                                    if (o != null) {
                                        boolean[] data = (boolean[]) o;
                                        Arrays.fill(data, true);
                                        init = true;
                                        log.info("强制初始化覆盖 {} {}", clazz.getName(), data);
                                    }
                                }
                            }
                            if (!init) {
                                log.info("跳过覆盖 {} {}", clazz.getName(), o);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        log.error("反射异常", e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

        });

    }

    public List<Class<?>> listAllLoanClasses() throws IOException, ClassNotFoundException {
        String basePath = basePackage.replace('.', '/');

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = JacocoSkip.class.getClassLoader();
        }

        Set<String> classNames = new HashSet<>();

        String modulePath = System.getProperty("user.dir");

        // 1. 从 ClassLoader 获取该包对应的所有资源 URL
        Enumeration<URL> resources = cl.getResources(basePath);
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol) && url.getPath().contains(modulePath)) {
                // 目录
                File dir = new File(url.getPath());
                scanDirectory(dir, basePackage, classNames);
            }
//            } else if ("jar".equals(protocol)) {
//                // jar:file:/...!/com/kucoin/out/loan
//                JarURLConnection jarConn = (JarURLConnection) url.openConnection();
//                try (JarFile jarFile = jarConn.getJarFile()) {
//                    scanJar(jarFile, basePath, classNames);
//                }
//            }
        }

        // 2. 过滤测试类并加载
        List<Class<?>> productClasses = new ArrayList<>();
        for (String className : classNames) {
            if (className.endsWith("Test") || className.endsWith("Tests")) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className);
                productClasses.add(clazz);
            } catch (Throwable e) {
                log.error("{}", className, e);
            }
        }

        return productClasses;

//        System.out.println("product 类数量: " + productClasses.size());
//        productClasses.forEach(c -> System.out.println(c.getName()));
    }

    private void scanDirectory(File dir, String packageName, Set<String> classNames) {
        if (!dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f, packageName + "." + f.getName(), classNames);
            } else if (f.getName().endsWith(".class")) {
                String simpleName = f.getName().substring(0, f.getName().length() - 6);
                classNames.add(packageName + "." + simpleName);
            }
        }
    }

    private void scanJar(JarFile jarFile, String basePath, Set<String> classNames) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!name.startsWith(basePath) || !name.endsWith(".class") || entry.isDirectory()) {
                continue;
            }
            String className = name.replace('/', '.').substring(0, name.length() - 6);
            classNames.add(className);
        }
    }
}
