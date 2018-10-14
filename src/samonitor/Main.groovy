package samonitor

import org.codehaus.groovy.control.CompilerConfiguration

class Main {

    static main(def args)
    {
        def project_root = "."
        def sourceCodeDir = "SmartApp"

        CompilerConfiguration cc = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
        SmartAppMonitor sam = new SmartAppMonitor()
        cc.addCompilationCustomizers(sam)
        GroovyShell gshell = new GroovyShell(cc)

        new File(sourceCodeDir).eachFile { f ->
            try {
                sam.createOutputFile("${f.getName()}", f.text)
                sam.resetVariables()
                gshell.evaluate(f)
            } catch (MissingMethodException mme) {
                def missingMethod = mme.toString()
                if (!missingMethod.contains("definition"))
                    println "missing method: " + missingMethod
            }
        }
    }
}
