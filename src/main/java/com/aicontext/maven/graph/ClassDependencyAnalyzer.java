package com.aicontext.maven.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Analyzes a class to extract project types it uses (fields, parameters, return types).
 * Includes injected fields (e.g. {@code @Inject GreetService greetService}) by scanning
 * all member declarations, not only a convenience field list.
 * Used for suggested graph generation and graph validation.
 */
public final class ClassDependencyAnalyzer {

    /**
     * Collects simple names of project classes used by the given class
     * (fields including injected, constructor params, method params, return types, including generics).
     *
     * @param cu                  compilation unit containing the class
     * @param cls                 the class to analyze
     * @param projectClassSimpleNames set of simple names of classes in the project (to filter)
     * @return set of project class simple names that this class references
     */
    public static Set<String> findUsedProjectTypes(
            CompilationUnit cu,
            ClassOrInterfaceDeclaration cls,
            Set<String> projectClassSimpleNames) {
        Set<String> used = new HashSet<>();
        String thisClassName = cls.getNameAsString();

        // Fields (use getMembers() so we include all fields, e.g. @Inject GreetService greetService)
        for (BodyDeclaration<?> member : cls.getMembers()) {
            if (member instanceof FieldDeclaration) {
                FieldDeclaration fd = (FieldDeclaration) member;
                Type t = fd.getCommonType();
                collectClassSimpleNames(t, projectClassSimpleNames, thisClassName, used);
            }
        }
        // Constructors - parameters
        for (ConstructorDeclaration ctor : cls.getConstructors()) {
            for (Parameter p : ctor.getParameters()) {
                collectClassSimpleNames(p.getType(), projectClassSimpleNames, thisClassName, used);
            }
        }
        // Methods - parameters and return type
        for (MethodDeclaration m : cls.getMethods()) {
            for (Parameter p : m.getParameters()) {
                collectClassSimpleNames(p.getType(), projectClassSimpleNames, thisClassName, used);
            }
            if (m.getType() != null) {
                collectClassSimpleNames(m.getType(), projectClassSimpleNames, thisClassName, used);
            }
        }

        return used;
    }

    private static void collectClassSimpleNames(
            Type type,
            Set<String> projectClassSimpleNames,
            String thisClassName,
            Set<String> out) {
        if (type == null) return;
        if (type instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType coit = (ClassOrInterfaceType) type;
            String name = coit.getNameAsString();
            String simple = simpleName(name);
            if (!simple.equals(thisClassName) && projectClassSimpleNames.contains(simple)) {
                out.add(simple);
            }
            // Type arguments (e.g. List<Foo>)
            coit.getTypeArguments().ifPresent(args -> args.forEach(arg ->
                    collectClassSimpleNames(arg, projectClassSimpleNames, thisClassName, out)));
            // Scope (e.g. Outer.Inner) - inner type simple name
            coit.getScope().ifPresent(scope ->
                    collectClassSimpleNames(scope, projectClassSimpleNames, thisClassName, out));
        }
    }

    private static String simpleName(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    /**
     * Builds the set of all class simple names declared in the given compilation unit.
     */
    public static Set<String> getClassSimpleNamesInCu(CompilationUnit cu) {
        return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .collect(Collectors.toSet());
    }

    private ClassDependencyAnalyzer() {
    }
}
