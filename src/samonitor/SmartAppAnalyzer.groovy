package samonitor

import javafx.beans.binding.MapExpression
import jdk.internal.org.objectweb.asm.tree.analysis.Analyzer
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.transform.GroovyASTTransformation

import javax.lang.model.element.VariableElement

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class SmartAppAnalyzer extends CompilationCustomizer {

    Set<Map> options
    SmartAppAnalyzer(){
        super(CompilePhase.SEMANTIC_ANALYSIS)
        options = new HashSet<Map>()
    }
    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode){
        Analyzer1 analyzer1 = new Analyzer1()
        classNode.visitContents(analyzer1)
        summary()
    }

    class Analyzer1 extends ClassCodeVisitorSupport {

        @Override
        void visitMethod(MethodNode meth) {

            super.visitMethod(meth)
        }
        @Override
        void visitMethodCallExpression(MethodCallExpression mce) {
            def meth = mce.getMethodAsString();
            if(meth.equals("input")) {
                def args = mce.getArguments()
                args.each { arg ->
                    if (arg instanceof ConstantExpression) {
                        def input = arg.getText()
                        if(!input.contains(".")) options += ["type": "input", "name": input]
                    }
                    if (arg instanceof MapExpression) {
                        arg.getMapEntryExpressions().each { m ->
                            if (m.getKeyExpression().getText().equals("name")) {
                                println m.getValueExpression().getText()
                            }
                        }
                    }
                }
            }
            if(meth.equals("subscribe")) {
                def args = mce.getArguments()
                if(args[2] instanceof VariableExpression) {
                    def arg2 = (VariableExpression) args[2]
                    options += ["type": "eventHandler", "name": arg2.getName()]
                }
            }

            else {
                if(meth.equals("runScript") || meth.equals("definition") || meth.equals("preferences") || meth.equals("section") || meth.equals("input") || meth.equals("initialize") || meth.equals("unsubscribe")) {

                }
                else if(meth.equals("debug") || meth.equals("currentValue")) {

                }
                else {
                    println mce.getReceiver() + "." + meth
                }
            }

            super.visitMethodCallExpression(mce)
        }
        @Override
        protected SourceUnit getSourceUnit() {
            return null
        }
    }

    def summary() {
        for(Map m : options) {
            println m;
        }
    }

}
