package com.aicontext.maven.graph;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClassDependencyAnalyzerTest {

    @Test
    void findUsedProjectTypes_detectsInjectedField() {
        String source = "package com.example;\n"
                + "import javax.inject.Inject;\n"
                + "public class GreetResource {\n"
                + "    @Inject\n"
                + "    GreetService greetService;\n"
                + "}\n";
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow();
        ClassOrInterfaceDeclaration cls = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        Set<String> projectClasses = Set.of("GreetResource", "GreetService");

        Set<String> used = ClassDependencyAnalyzer.findUsedProjectTypes(cu, cls, projectClasses);

        assertThat(used).containsExactly("GreetService");
    }

    @Test
    void findUsedProjectTypes_detectsPlainField() {
        String source = "package com.example;\n"
                + "public class Foo {\n"
                + "    private Bar bar;\n"
                + "}\n";
        CompilationUnit cu = new JavaParser().parse(source).getResult().orElseThrow();
        ClassOrInterfaceDeclaration cls = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        Set<String> projectClasses = Set.of("Foo", "Bar");

        Set<String> used = ClassDependencyAnalyzer.findUsedProjectTypes(cu, cls, projectClasses);

        assertThat(used).containsExactly("Bar");
    }
}
