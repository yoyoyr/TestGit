package com.xk.annotation_compile;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.xk.annotation_lib.BindView;
import com.xk.annotation_lib.BindView1;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.VariableElement;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * javaPreCompile执行
 */
@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Filer filer;
    private Types typeUtils;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        messager.printMessage(Diagnostic.Kind.NOTE, "init");
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        messager.printMessage(Diagnostic.Kind.NOTE, "getSupportedAnnotationTypes");
        //可处理的注解的集合
        HashSet<String> annotations = new HashSet<>();
        annotations.add(BindView.class.getCanonicalName());
        annotations.add(BindView1.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        messager.printMessage(Diagnostic.Kind.NOTE, "supprot verion = " + SourceVersion.latestSupported());
        return SourceVersion.latestSupported();
    }

    /**
     * element详解
     * package com.example;    // PackageElement
     * public class Demo {        // TypeElement
     * private int a;      // VariableElement
     * private Demo other;  // VariableElement
     * public Demo () {}    // ExecuteableElement
     * public void setA (  // ExecuteableElement
     * int newA)   // TypeParameterElement
     * }
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager.printMessage(Diagnostic.Kind.NOTE, "process");
        Map<TypeElement, List<BindViewInfo>> bindViewMap = new HashMap<>();
        /**
         * public TextView textView;
         * TextView textView1;
         */
        for (Element element : roundEnv.getElementsAnnotatedWith(BindView.class)) {
            if (element.getKind() != ElementKind.FIELD) {
                error("BindView must in field", element);
                return false;
            }
//            获取所有有BindView注释的TypeElement
            TypeElement typeElement = (TypeElement) element.getEnclosingElement();
            if (!bindViewMap.containsKey(typeElement)) {
                bindViewMap.put((TypeElement) element.getEnclosingElement(), new ArrayList<>());
            }

            int viewId = element.getAnnotation(BindView.class).value();
            VariableElement viewElement = (VariableElement) element;
            bindViewMap.get(typeElement).add(new BindViewInfo(viewId,
                    viewElement.getSimpleName().toString(), viewElement.asType()));
        }

        generateCodeByJavaPoet(bindViewMap);
        return false;
    }

    private void generateCodeByJavaPoet(Map<TypeElement, List<BindViewInfo>> bindViewMap) {
        bindViewMap.forEach((typeElement, bindViewInfos) -> {
//            利用javapoet框架生产java类
            generateJavaClassByJavaPoet(typeElement, bindViewInfos);
        });
    }

    /**
     * @param typeElement   类的节点（MainActivity那个节点）
     * @param bindViewInfos
     */
    private void generateJavaClassByJavaPoet(TypeElement typeElement, List<BindViewInfo> bindViewInfos) {
        String packageName = elementUtils.getPackageOf(typeElement).getQualifiedName().toString();

        //构造类   public class MainActivity$$ViewBinder<T extends MainActivity> implements ViewBinder<T>
        ClassName t = ClassName.bestGuess("T");
        ClassName viewBinder = ClassName.bestGuess("com.xk.butterknifelib.ViewBinder");
//        ParameterSpec.builder(Typen)
        //方法
        /**
         *   @Override public void bind(T activity) {
         *      activity.textView=activity.findViewById(2131165297);
         *      activity.textView1=activity.findViewById(2131165297);
         *  }
         */
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder("bind")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(void.class)
                .addParameter(t, "activity");
        MethodSpec methodSpec;
        for (BindViewInfo bindViewInfo : bindViewInfos) {
            methodSpecBuilder.addStatement("activity.$L=activity.findViewById($L)", bindViewInfo.name, bindViewInfo.id);
        }
        methodSpec = methodSpecBuilder.build();
        //类
        TypeSpec typeSpec = TypeSpec.classBuilder(typeElement.getSimpleName() + "$$ViewBinder")//设置类名
                .addModifiers(Modifier.PUBLIC)//添加修饰符
                .addTypeVariable(TypeVariableName.get("T", TypeName.get(typeElement.asType())))//添加泛型声明
                .addMethod(methodSpec)//添加方法
                .addSuperinterface(ParameterizedTypeName.get(viewBinder, t))//添加实现接口
                .build();

        messager.printMessage(Diagnostic.Kind.NOTE, typeSpec.toString());
        //通过包名和TypeSpec（类）生成一个java文件
        JavaFile build = JavaFile.builder(packageName, typeSpec).build();
        try {
            //写入到filer中
            build.writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void generateCodeByStringBuffer(Map<TypeElement, List<BindViewInfo>> bindViewMap) {
        bindViewMap.forEach((typeElement, bindViewInfos) -> {
            generateJavaClassBySb(typeElement, bindViewInfos);
        });
    }

    private void generateJavaClassBySb(TypeElement typeElement, List<BindViewInfo> bindViewInfos) {
        try {
            StringBuffer sb = new StringBuffer();
            sb.append("package ");
            sb.append(elementUtils.getPackageOf(typeElement).getQualifiedName() + ";\n");
            sb.append("import com.xk.butterknifelib.ViewBinder;\n");
            sb.append("public class " + typeElement.getSimpleName() + "$$ViewBinder<T extends " + typeElement.getSimpleName() + "> implements ViewBinder<T> {\n");
            sb.append("@Override\n");
            sb.append("public void bind(T activity) {\n");

            for (BindViewInfo bindViewInfo : bindViewInfos) {
                sb.append("activity." + bindViewInfo.name + "=activity.findViewById(" + bindViewInfo.id + ");\n");

            }
            sb.append("}\n}");


            JavaFileObject sourceFile = filer.createSourceFile(typeElement.getQualifiedName().toString() + "$$ViewBinder");
            Writer writer = sourceFile.openWriter();
            writer.write(sb.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void error(String msg, Element e) {
        messager.printMessage(
                Diagnostic.Kind.ERROR,
                String.format(msg),
                e);
    }

    /**
     * 一个被bindview注解的view字段的信息
     */
    class BindViewInfo {
        int id;
        String name;
        TypeMirror typeMirror;

        public BindViewInfo(int id, String name, TypeMirror typeMirror) {
            this.id = id;
            this.name = name;
            this.typeMirror = typeMirror;
        }

        @Override
        public String toString() {
            return "BindViewInfo{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", typeMirror=" + typeMirror +
                    '}';
        }
    }
}
