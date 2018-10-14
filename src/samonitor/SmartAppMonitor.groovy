package samonitor

import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class SmartAppMonitor extends CompilationCustomizer{

    OutFile of
    int numberOfLineAdded

    Set<String> deviceNames
    Set<String> inputDeviceNames
    Set<String> outputDeviceNames
    Set<String> eventNames
    Set<String> handlerMethodNames
    List<Map> insertCodeMap
    Set<Map> deviceNames2
    Set<Map> device_handlerPair


    boolean skipMethod
    boolean inIfStat
    boolean inHandler


    public SmartAppMonitor()
    {
        super(CompilePhase.SEMANTIC_ANALYSIS)
        numberOfLineAdded = 0
        deviceNames = new HashSet<String>()
        inputDeviceNames = new HashSet<String>()
        outputDeviceNames = new HashSet<String>()
        eventNames = new HashSet<String>()
        handlerMethodNames = new HashSet<String>()
        insertCodeMap = new ArrayList<Map>()
        deviceNames2 = new HashSet<Map>()

        device_handlerPair = new HashSet<Map>()

        skipMethod = true
        inIfStat = false
        inHandler = false

    }
    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        MyCodeVisitor mcv = new MyCodeVisitor()
        classNode.visitContents(mcv)
        insertCodeMap.sort({m1, m2 -> m1.lineNumber <=> m2.lineNumber})
        for(Map m : insertCodeMap) {
            codeInsert(m.get("code"), m.get("lineNumber"), m.get("addedLine"))
        }
        println of.getText()
    }

    class MyCodeVisitor extends ClassCodeVisitorSupport{

        @Override
        void visitMethod(MethodNode meth)
        {
            def methName = meth.getName()
            def param
            def deviceN
            
            meth.getParameters().each { p ->
				param = p.getAt("name")
			}
			for(Map m : device_handlerPair) {
				if(m.get("handler").equals(methName)) {
					deviceN = m.get("device")
				}
			}
			
            if(!methName.equals("main") && !methName.equals("run") && !methName.equals("installed") && !methName.equals("updated") && !methName.equals("initialize")) {
                skipMethod = false
                for(String s : handlerMethodNames) {
					if(methName.equals(s)) {
						inHandler = true
						
						String code = "\t//Inserted Code\n"
                        if(deviceN.toString().equals("app") || deviceN.toString().equals("location")) {
                            code += "\tsmartAppMonitor.setData(app.getName(), \"\${" + param + ".value}\", \"event\", \""+ deviceN + "\", \"event\")"
                        }
                        else {
                            code += "\tsmartAppMonitor.setData(app.getName(), \"\${" + param + ".value}\", \"event\", \"\${"+ param + ".getDevice()}\", \"event\")"
                        }
						insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2])
						
						code = "\t//Inserted Code\n"
						code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"handlerMethod\", \"this\", \"handlerMethod\")"
						insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2])
						break
					}
					else {
						inHandler = false
					}
                }
                if(inHandler == false) {
					String code = "\t//Inserted Code\n"
					code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"methodCall\", \"this\", \"methodCall\")"
					insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2])
                }
            }
            else if(methName.equals("run")) {
                numberOfLineAdded = 0
                skipMethod = true
            }
            else if(methName.equals("installed")) {
                String code = "\t//Inserted Code\n"
                code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"methodCall\", \"this\", \"methodCall\")"
                insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2])
            }
            super.visitMethod(meth)
        }

        @Override
        void visitMethodCallExpression(MethodCallExpression mce) {
            def methText = mce.getMethodAsString()


            if(methText.equals("preferences")){
                String code = "\t//Inserted Code\n"
                code += "\tsection(\"Select SmartAppMonitor\") {\n" + "\t\tinput \"smartAppMonitor\", \"capability.execute\"\n" + "\t}"
                insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber()+1, "addedLine": 4])
            }

            if(methText.equals("input") || methText.equals("ifSet")) {
                def args = mce.getArguments()
                if(args.getAt("text").toString().contains("capability") || args.getAt("text").toString().contains("device") || args.getAt("text").toString().contains("attribute")) {
                    Map ma2 = [:]
                    args.each { arg ->
                        if(arg instanceof ConstantExpression) {
                            String text = arg.getText()
                            if(text.contains("capability") || text.contains("device") || text.contains("attribute")) {
                                if(text.contains("capability")) {
                                    ma2 += ["capability": arg.getText().substring(11)]
                                }
                                else if(text.contains("device")) {
                                    ma2 += ["capability": arg.getText().substring(7)]
                                }
                                else if(text.contains("attribute")) {
                                    ma2 += ["capability": arg.getText().substring(10)]
                                }
                            }
                            else {
                                ma2 += ["name" : arg.getText()]
                            }
                        }

                        if (arg instanceof MapExpression) {
                            //println arg
                            Map ma = [:]
                            arg.getMapEntryExpressions().each { m ->
                                if (m.getKeyExpression().getText().equals("name")) {
                                    deviceNames.add(m.getValueExpression().getText())
                                    ma = ["name": m.getValueExpression().getText()]
                                }
                                if(m.getKeyExpression().getText().equals("type")) {
                                    def text = m.getValueExpression().getText()
                                    if(text.contains("capability.") || text.contains("device") || text.contains("attribute")) {
                                        if(text.contains("capability") ){
                                            ma += ["capability": text.substring(11)]
                                        }
                                        else if(text.contains("device") ){
                                            ma += ["capability": text.substring(7)]
                                        }
                                        else if(text.contains("attribute") ){
                                            ma += ["capability": text.substring(10)]
                                        }
                                        deviceNames2.add(ma)
                                    }
                                }

                            }
                        }
                    }
                    deviceNames2.add(ma2)
                }

            }

            if(methText.equals("subscribe")) {
                def args = mce.getArguments()
                def deviceN
                def handlerN
                
                if(args[0] instanceof VariableExpression) {
                    VariableExpression argvex0 = (VariableExpression) args[0]
                    inputDeviceNames.add(argvex0.getName())
                    deviceN = argvex0.getName()
                }
                
                if(args[1] instanceof VariableExpression) {
					VariableExpression argvex1 = (VariableExpression) args[1]
					handlerMethodNames.add(argvex1.getName())
					handlerN = argvex1.getName()
                }
                
                if(args[2] instanceof VariableExpression) {
					VariableExpression argvex2 = (VariableExpression) args[2]
					handlerMethodNames.add(argvex2.getName())
					handlerN = argvex2.getName()
                }
                
                device_handlerPair.add([device:deviceN, handler:handlerN])
            }

            if(!skipMethod) {
                // if action -> code insert
                // if send message -> code insert

                if(methText.toLowerCase().contains("state") || methText.toLowerCase().contains("value") || methText.toLowerCase().contains("event"))
                {

                }
                else if(methText.contains("mappings")) {

                }
                else if(methText.contains("find") || methText.contains("collect")) {

                }
                else if(methText.contains("size") || methText.contains("count")) {

                }
                else if(methText.contains("each") || methText.contains("eachWithIndex")) {
                    def recver = mce.getReceiver()
                    mce.getAt("arguments").each { a ->
                        if(a instanceof ClosureExpression) {
                            a.getParameters().each { a_p ->
                                if (recver instanceof VariableExpression) {
                                    VariableExpression recvex = (VariableExpression) recver
                                    def capa
                                    for (Map m : deviceNames2) {
                                        if (recvex.getName().equals(m.get("name"))) {
                                            outputDeviceNames.add(a_p.getAt("name"))
                                            capa = m.get("capability")
                                        }
                                    }
                                    deviceNames2.add(["name": a_p.getAt("name"), "capability": capa])
                                }
                            }
                        }
                    }
                }
                else {
                    def recver = mce.getReceiver()

                    if (recver instanceof VariableExpression) {
                        VariableExpression recvex = (VariableExpression) recver

                        for (Map m : deviceNames2) {
                            if (recvex.getName().equals(m.get("name"))) {
                                outputDeviceNames.add(m.get("name"))
                                String code = "\t//Inserted Code\n"
                                code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"" + m.get("capability") + "\", \"\${" + m.get("name") + ".getName()}\", \"action\")"
                                insertCodeMap.add(["code": code, "lineNumber": recvex.getLineNumber(), "addedLine": 2])
                            }
                        }
                    }
                    if (methText.equals("sendSms") || methText.equals("sendPush") || methText.equals("sendNotificationToContacts") || methText.equals("sendNotification")) {
                        String code = "\t//Inserted Code\n"
                        code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"send\", \"this\", \"action\")"
                        insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber(), "addedLine": 2])

                    }
                    if(methText.equals("setLocationMode")) {
                        String code = "\t//Inserted Code\n"
                        code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"setLocationMode\", \"this\", \"action\")"
                        insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber(), "addedLine": 2])
                    }
                }
            }


            super.visitMethodCallExpression(mce)

        }

        @Override
        void visitIfElse(IfStatement ifElse) {
            Statement ifStat = ifElse.getIfBlock()
            Statement elseStat = ifElse.getElseBlock()
            if(!ifStat.getText().contains("{")) {
                inIfStat = true
                insertCodeMap.add(["code": "\t{", "lineNumber": ifStat.getLineNumber(), "addedLine": 1])
                insertCodeMap.add(["code": "\t}", "lineNumber": ifStat.getLineNumber()+1, "addedLine": 1])
            }
            if(!elseStat.getAt("class").toString().contains("EmptyStatement")) {
                if(!elseStat.getText().contains("{")) {
                    if(elseStat.getAt("ifBlock") != null) {

                    }
                    else {
                        if (elseStat.getColumnNumber() != -1) {
                            insertCodeMap.add(["code": "\t{", "lineNumber": elseStat.getLineNumber(), "addedLine": 1])
                            insertCodeMap.add(["code": "\t}", "lineNumber": elseStat.getLineNumber() + 1, "addedLine": 1])
                        }
                    }
                }
            }
            super.visitIfElse(ifElse)
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return null;
        }
    }

    private void codeInsert(String code, int lineNum, int addLine) {
        if(lineNum >= 0) {
            def f = of.getFile()
            def lines = f.readLines()
            lines = lines.plus(lineNum + numberOfLineAdded, code)
            f.text = lines.join('\n')
            numberOfLineAdded += addLine
        }
    }
    def summarize()
    {

    }

    void createOutputFile(String filename, String fileText) {
        String name = "output/" + filename
        of = new OutFile(name)
        of.removeFileText()
        of.append(fileText)

    }

    void resetVariables() {
        numberOfLineAdded = 0
        deviceNames = new HashSet<String>()
        inputDeviceNames = new HashSet<String>()
        outputDeviceNames = new HashSet<String>()
        eventNames = new HashSet<String>()
        handlerMethodNames = new HashSet<String>()
        insertCodeMap = new ArrayList<Map>()
        deviceNames2 = new HashSet<Map>()

        device_handlerPair = new HashSet<Map>()

        skipMethod = true
        inIfStat = false
        inHandler = false
    }
}
