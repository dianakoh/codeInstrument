package samonitor

import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException

class Main {

    static main(def args)
    {
        def project_root = "."
        def sourceCodeDir = "SmartApp"

        def argText = args[0]
        def parser = new JsonSlurper()
        def json = parser.parseText(argText);

        CompilerConfiguration cc = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
        SmartAppMonitor sam = new SmartAppMonitor(json)
        cc.addCompilationCustomizers(sam)
        GroovyShell gshell = new GroovyShell(cc)
        sam.setActionSet()
        new File(sourceCodeDir).eachFile { f ->
            try {
                sam.createOutputFile("${f.getName()}", f.text)
                //println(f.getName())
                sam.resetVariables()
                gshell.evaluate(f)
            } catch (MissingMethodException mme) {
                def missingMethod = mme.toString()
                if (!missingMethod.contains("definition")) {
                    def message = "missing method: " + missingMethod
                    //println "missing method: " + missingMethod
                }
            } catch(MultipleCompilationErrorsException mcee) {
                mcee.printStackTrace()
            }
        }
    }
}
