package samonitor

import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TernaryExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.transform.GroovyASTTransformation

import java.sql.*;
import groovy.sql.Sql

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class SmartAppMonitor extends CompilationCustomizer{

    OutFile of
    int numberOfLineAdded
    List<Map> closureDeviceNames

    Set<String> deviceNames
    Set<String> inputDeviceNames
    Set<String> outputDeviceNames
    Set<String> eventNames
    Set<String> handlerMethodNames
    List<Map> insertCodeMap
    Set<Map> deviceNames2
    Set<Map> device_handlerPair
    Set<Map> method_returnPair

    Set<String> actionSet
    Set<Map> ternaryLineNumber

    List<String> pageNames
    Set<String> scheduleMethodNames
    Set<Map> scheduleTimeandMethod

    boolean skipMethod
    boolean inIfStat
    boolean inHandler
    boolean isTernary
    int preferenceStartLine
    boolean isPage


    public SmartAppMonitor()
    {
        super(CompilePhase.SEMANTIC_ANALYSIS)
        numberOfLineAdded = 0
        closureDeviceNames = new ArrayList<Map>()

        deviceNames = new HashSet<String>()
        inputDeviceNames = new HashSet<String>()
        outputDeviceNames = new HashSet<String>()
        eventNames = new HashSet<String>()
        handlerMethodNames = new HashSet<String>()
        insertCodeMap = new ArrayList<Map>()
        deviceNames2 = new HashSet<Map>()

        device_handlerPair = new HashSet<Map>()
        method_returnPair = new HashSet<Map>()

        actionSet = new HashSet<String>()
        ternaryLineNumber = new HashSet<Map>()

        pageNames = new ArrayList<String>()
        scheduleMethodNames = new HashSet<String>()
        scheduleTimeandMethod = new HashSet<Map>()


        skipMethod = true
        inIfStat = false
        inHandler = false
        isTernary = false
        preferenceStartLine = 0
        isPage = false

    }
    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        MethodDecVisitor mdv = new MethodDecVisitor()
        classNode.visitContents(mdv)

        MyCodeVisitor mcv = new MyCodeVisitor()
        classNode.visitContents(mcv)
        insertCodeMap.sort({m1, m2 -> m1.lineNumber <=> m2.lineNumber})
        for(Map m : insertCodeMap) {
            codeInsert(m.get("code"), m.get("lineNumber"), m.get("addedLine"), m.get("exception"))
        }
        println of.getText()
    }
    class MethodDecVisitor extends ClassCodeVisitorSupport {

        // store method information that return devices in method_returnPair
        @Override
        void visitMethod(MethodNode meth)
        {
            def methName = meth.getName()
            if(!methName.equals("main") && !methName.equals("run") && !methName.equals("installed") && !methName.equals("updated") && !methName.equals("initialize")) {

                def f = of.getFile()
                def lines = f.readLines()
                def lastLines = lines.get(meth.getLastLineNumber()-1)
                if(!deviceNames2.isEmpty()) {
                    for (Map m : deviceNames2) {
                        if (lastLines != null && m.containsKey("name")) {
                            if (lastLines.contains(m.get("name"))) {
                                method_returnPair.add(["method": methName, "return": m.get("name")])
                            }
                        }
                    }
                }

            }
            else {

            }
            super.visitMethod(meth)
        }

        @Override
        void visitMethodCallExpression(MethodCallExpression mce) {
            def methText = mce.getMethodAsString()

  /*         if(methText.equals("definition")) {
                mce.getArguments().each { arg ->
                    arg.getAt("mapEntryExpressions").each { mee ->
                        ConstantExpression key = mee.getAt("keyExpression")
                        if(key.getText().equals("namespace")) {
                            ConstantExpression value = mee.getAt("valueExpression")
                            //println(value.getText())
                        }

                    }
                }
            } */

            // if the smart app has page structure -> store the first page's name in pageNames (ArrayList)
            if(methText.equals("page")) {
                isPage = true
                def args = mce.getArguments()
                args.getAt("expressions").each { exarg ->
                    if(exarg.getAt("text").toString().contains("name")) {
                        List<String> temp = exarg.getAt("text").toString().tokenize('[,]')
                        temp.each {
                            if(it.contains("name")) {
                                pageNames.add(it.tokenize(': ')[1])
                            }
                        }

                    }

                }
            }

            // input part -> store the input devices in deviceNames2 (HashSet<Map>)
            if (methText.equals("input") || methText.equals("ifSet")) {
                def args = mce.getArguments()
                if (args.getAt("text").toString().contains("capability") || args.getAt("text").toString().contains("device") || args.getAt("text").toString().contains("attribute") || args.getAt("text").toString().contains("hub")) {
                    Map ma2 = [:]
                    args.each { arg ->
                        if (arg instanceof ConstantExpression) {
                            String text = arg.getText()
                            if (text.contains("capability") || text.contains("device") || text.contains("attribute")) {
                                if (text.contains("capability")) {
                                    ma2 += ["capability": arg.getText().substring(11)]
                                } else if (text.contains("device")) {
                                    ma2 += ["capability": arg.getText().substring(7)]
                                } else if (text.contains("attribute")) {
                                    ma2 += ["capability": arg.getText().substring(10)]
                                }
                            }
                            else if(text.contains("hub")) {
                                ma2 += ["name": text, "capability": text]
                            }
                            else {
                                ma2 += ["name": arg.getText()]
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
                                if (m.getKeyExpression().getText().equals("type")) {
                                    def text = m.getValueExpression().getText()
                                    if (text.contains("capability.") || text.contains("device") || text.contains("attribute")) {
                                        if (text.contains("capability")) {
                                            ma += ["capability": text.substring(11)]
                                        } else if (text.contains("device")) {
                                            ma += ["capability": text.substring(7)]
                                        } else if (text.contains("attribute")) {
                                            ma += ["capability": text.substring(10)]
                                        }
                                        if(ma != null && !ma.isEmpty())
                                            deviceNames2.add(ma)
                                    }
                                }

                            }
                        }
                    }
                    if(ma2 != null && !ma2.isEmpty())
                        deviceNames2.add(ma2)
                }
            }

            // if the smart app has schedule methods like schedule, runEveryXMinutes, ... etc, store the method names in scheduleMethodNames (HashSet<String>)
            if(methText.equals("schedule")) {
                String temp = mce.getArguments().getAt("text").toString()
                List<String> temp2 = temp.tokenize(',')
                String time = temp2[0].substring(1)
                List<String> temp3
                if(temp2.size() == 2)
                    temp3 = temp2[1].substring(0, temp2[1].length()-1).tokenize(' ')
                else if(temp2.size() == 3)
                    temp3 = temp2[1].tokenize(' ')
                String handler = temp3[0]
                //scheduleMethodNames.add(temp2[temp2.size()-1])
                scheduleMethodNames.add(handler)
                scheduleTimeandMethod.add(["time": time, "method": handler])
                //println(scheduleTimeandMethod)
            }
            /*if(methText != null && methText.contains("runEvery") && !methText.equals("runScript")) {
                String temp = mce.getArguments().getAt("text").toString()
                //println temp
                //List<String> temp2 = temp.tokenize(',')
                //scheduleMethodNames.add(temp2[1].tokenize(' ()')[0])
               //println
            }*/
            /*if(methText != null && methText.contains("runEvery")) {
                println "runEvery"
            }*/
            if(methText.equals("runOnce")) {
                String temp = mce.getArguments().getAt("text").toString()
                List<String> temp2 = temp.tokenize(',')
                String time = temp2[0].substring(1)
                List<String> temp3
                if(temp2.size() == 2)
                    temp3 = temp2[1].substring(0, temp2[1].length()-1).tokenize(' ')
                else if(temp2.size() == 3)
                    temp3 = temp2[1].tokenize(' ')
                String handler = temp3[0]
                scheduleMethodNames.add(handler)
                scheduleTimeandMethod.add(["time": time, "method": handler])
            }
            super.visitMethodCallExpression(mce)
        }


        @Override
        void visitTernaryExpression(TernaryExpression tre) {
            //println "te = " + tre.getText()
            BooleanExpression be = tre.getBooleanExpression()
            Expression te = tre.getTrueExpression()
            Expression fe = tre.getFalseExpression()

           // println "be prop = " + be.getProperties()
           // println "te prop = " + te.getProperties()
           // println "fe prop = " + fe.getProperties()
            if(be.hasProperty("receiver")) {
                String temp = be.getText()
                def temp2 = temp.tokenize('.(')
               for(String s : actionSet) {
                   if(temp2[1].equals(s)) {
                       Map a = ["lineNumber": tre.getLineNumber(), "colNumber": be.getColumnNumber()]
                       ternaryLineNumber.add(a)
                       //println "be = " + be.getColumnNumber()
                   }
               }
            }
            if(te.hasProperty("receiver")) {
                def temp = te.getText()
                def temp2 = temp.tokenize('.(')
                for(String s : actionSet) {
                    if(temp2[1].equals(s)) {
                        Map a = ["lineNumber": tre.getLineNumber(), "colNumber": te.getColumnNumber()]
                        ternaryLineNumber.add(a)
                    }
                }
            }
            if(fe.hasProperty("receiver")) {
               // println fe.getAt("method")
                //println fe.getProperties()
                def temp = fe.getText()
                def temp2 = temp.tokenize('.(')
                for(String s : actionSet) {
                    if(temp2[1].equals(s)) {
                        Map a = ["lineNumber": tre.getLineNumber(), "colNumber": fe.getColumnNumber()]
                        ternaryLineNumber.add(a)
                    }
                }
            }
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return null;
        }
    }

    class MyCodeVisitor extends ClassCodeVisitorSupport{

        @Override
        void visitMethod(MethodNode meth)
        {
            def methName = meth.getName()
            def param
            def deviceN
            def deviceC

            // get the method's parameters - ex. evt
            meth.getParameters().each { p ->
				param = p.getAt("name")
			}

            // if the method is event handler method, get the device information
			for(Map m : device_handlerPair) {
				if(m.get("handler").equals(methName)) {
					deviceN = m.get("device")
				}
			}
			for(Map m : deviceNames2) {
                if(deviceN == m.get("name")) {
                    deviceC = m.get("capability")

                }
            }
            if(!methName.equals("main") && !methName.equals("run") && !methName.equals("installed") && !methName.equals("updated") && !methName.equals("initialize")) {
                skipMethod = false
                inHandler = false
                for(String s : handlerMethodNames) {
					if(methName.equals(s)) { //if the method is handler method
						inHandler = true
                        if(meth.getLineNumber() == meth.getLastLineNumber()) {
                            break
                        }
                        else {
                            String code = "\t//Inserted Code\n"
                            if (deviceN.toString().equals("app") || deviceN.toString().equals("location")) {
                                code += "\tsmartAppMonitor.setData(app.getName(), \"\${" + param + ".value}\", \"\${" + param + ".value}\", \"" + deviceN + "\", \"event\")"
                            } else {
                                code += "\tsmartAppMonitor.setData(app.getName(), \"\${" + param + ".value}\", \"" + deviceC + "\", \"\${" + param + ".getDevice()}\", \"event\")"
                            }
                            insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2, "exception": 0])

                            code = "\t//Inserted Code\n"
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"handlerMethod\", \"this\", \"handlerMethod\")"
                            insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2, "exception": 0])

                            break
                        }
					}
                }
                if(inHandler == false) { //if the method is not handler method
                    String sche = ""
                    String scheduleTime = ""
                    for(String s : scheduleMethodNames) {
                        if (methName.equals(s)) {
                            sche = "scheduleMethod"
                        }
                        for(Map m : scheduleTimeandMethod) {
                            if(m.get("method").toString().equals(methName)) {
                                scheduleTime = m.get("time").toString()
                            }
                        }
                    }
                    if(meth.getLineNumber() == meth.getLastLineNumber()) { // if the method has only one line
                        String code = "\t//Inserted Code\n"
                        if(sche != "") {
                            code += "\tsmartAppMonitor.setData(app.getName(), \"\${" + scheduleTime + "}\", \"time\", \"this\", \"event\")\n"
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"" + sche + "\", \"this\", \"handlerMethod\")"
                            insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 3, "exception": 2]) //exception: 2
                        }
                        else {
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"methodCall\", \"this\", \"methodCall\")"
                            insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2, "exception": 2]) //exception: 2
                        }
                    }
                    else {
                        String code = "\t//Inserted Code\n"
                        if(sche != "") {
                            code += "\tsmartAppMonitor.setData(app.getName(), \"\${" + scheduleTime + "}\", \"time\", \"this\", \"event\")\n"
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"" + sche + "\", \"this\", \"handlerMethod\")"
                            insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 3, "exception": 0])
                        }
                        else {
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"methodCall\", \"this\", \"methodCall\")"
                            insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2, "exception": 0])
                        }
                    }
                }
            }

            else if(methName.equals("run")) {
                numberOfLineAdded = 0
                skipMethod = true
            }

            // when the smartapp - install
            else if(methName.equals("installed")) {
                String code = "\t//Inserted Code\n"
                code += "\tsmartAppMonitor.setData(app.getName(), \"" + methName + "\", \"methodCall\", \"this\", \"methodCall\")"
                if(meth.getFirstStatement() != null)
                    insertCodeMap.add(["code": code, "lineNumber": meth.getFirstStatement().getLineNumber(), "addedLine": 2, "exception": 0])
                else
                    if(meth.getLineNumber() == meth.getLastLineNumber()) // if there is not any content in installed method
                        insertCodeMap.add(["code": code, "lineNumber": meth.getLineNumber(), "addedLine": 2, "exception": 2])
                    else {
                        insertCodeMap.add(["code": code, "lineNumber": meth.getLineNumber()+1, "addedLine": 2, "exception": 0])
                    }

            }
            super.visitMethod(meth)
        }

        @Override
        void visitMethodCallExpression(MethodCallExpression mce) {
            def methText = mce.getMethodAsString()

            if(methText.equals("preferences")){
                if(isPage == true) { //if the smartapp's preference has page structures
                    String code = "\t//Inserted Code\n"
                    code += "\tpage(name: \"Select SmartApp Monitor Page\", nextPage: \"" + pageNames[0] + "\", uninstall: true) {\n"
                    code += "\t\tsection(\"Select SmartAppMonitor\") {\n" + "\t\t\tinput \"smartAppMonitor\", \"capability.execute\"\n" + "\t\t}"
                    code += "\n\t}"
                    insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber()+1, "addedLine": 6, "exception": 0])
                }
                else { //if the smartapp's preference doesn't have any page structure
                    String code = "\t//Inserted Code\n"
                    code += "\tsection(\"Select SmartAppMonitor\") {\n" + "\t\tinput \"smartAppMonitor\", \"capability.execute\"\n" + "\t}"
                    insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber()+1, "addedLine": 4, "exception": 0])
                }

            }

            // subscribe part - store the input devices and handler methods in device_handlerPair
            if(methText.equals("subscribe") || methText.equals("subscribeToCommand")) {
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
                //println(device_handlerPair)
            }

            if(!skipMethod) {
                // if action -> code insert
                // if send message -> code insert
                if(methText != null ) {
                    if (methText.toLowerCase().contains("state") || methText.toLowerCase().contains("value") || methText.toLowerCase().contains("event")) {

                    } else if (methText.contains("mappings")) {

                    } else if (methText.contains("find") || methText.contains("collect")) {

                    } else if (methText.contains("size") || methText.contains("count")) {

                    } else if (methText.contains("hasCapability")) {

                    } else if (methText.contains("each") || methText.contains("eachWithIndex")) {  // if the smartapp use the devices variable with closure
                        closureDeviceNames = new ArrayList<Map>()
                        def recver = mce.getReceiver()
                        mce.getAt("arguments").each { a ->
                            if (a instanceof ClosureExpression) {
                                if (a.getParameters()) {  // if the closure has parameters
                                    a.getParameters().each { a_p ->
                                        if (recver instanceof VariableExpression) {
                                            VariableExpression recvex = (VariableExpression) recver
                                            def realDevice
                                            def closureDevice
                                            def capa
                                            for (Map m : deviceNames2) {
                                                if (recvex.getName().equals(m.get("name"))) {
                                                    realDevice = m.getAt("name")
                                                    closureDevice = a_p.getAt("name")
                                                    capa = m.get("capability")
                                                }
                                            }
                                            closureDeviceNames.add(["realDevice": realDevice, "closureDevice": closureDevice, "capability": capa])
                                        }
                                    }
                                } else { // it the closure doesn't have any parameters
                                    if (recver instanceof VariableExpression) {
                                        VariableExpression recvex = (VariableExpression) recver
                                        def realDevice
                                        def closureDevice
                                        def capa
                                        for (Map m : deviceNames2) {
                                            if (recvex.getName().equals(m.get("name"))) {
                                                realDevice = m.getAt("name")
                                                closureDevice = "it"
                                                capa = m.get("capability")
                                            }
                                        }
                                        closureDeviceNames.add(["realDevice": realDevice, "closureDevice": closureDevice, "capability": capa])
                                    }
                                }
                            }

                        }
                    } else {
                        def recver = mce.getReceiver()
                        boolean thereisaction = false // 만약 action method 가 존재하는데 action으로 인식하지 못하는 경우를 체크하는 변수
                        if (recver.getClass().toString().contains("MethodCallExpression")) { // if the method call's receiver is a method call expression
                            def deviceN
                            for (Map m : method_returnPair) {  // find the method's return value in method_returnPair
                                if (recver.getAt("methodAsString").equals(m.get("method"))) {
                                    deviceN = m.get("return")
                                }
                            }
                            for (Map m : deviceNames2) { // find the device in deviceNames2
                                if (deviceN.toString().equals(m.get("name"))) {
                                    thereisaction = true;
                                    String code = "\t//Inserted Code\n"
                                    code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"" + m.get("capability") + "\", \"\${" + m.get("name") + ".getName()}\", \"action\")"
                                    insertCodeMap.add(["code": code, "lineNumber": recver.getLineNumber() + 1, "addedLine": 2, "exception" :0])
                                }
                            }
                        }
                        if (recver instanceof VariableExpression) {
                            VariableExpression recvex = (VariableExpression) recver

                            for (Map m : deviceNames2) {
                                if (recvex.getName().equals(m.get("name"))) {
                                    for(Map m2 : ternaryLineNumber) {
                                        if(mce.getLineNumber() == m2.get("lineNumber")) {
                                            isTernary = true
                                            thereisaction = true
                                            outputDeviceNames.add(m.get("name"))
                                            String code = "/*Inserted Code*/ "
                                            code += "smartAppMonitor.setData(app.getName(), \"" + methText + "\", \"" + m.get("capability") + "\", \"\${" + m.get("name") + ".getName()}\", \"action\")"
                                            insertCodeMap.add(["code": code, "lineNumber": recvex.getLineNumber(), "addedLine": 0, "exception": 3])
                                            break
                                        }
                                    }
                                    thereisaction = true
                                    outputDeviceNames.add(m.get("name"))
                                    String code = "\t//Inserted Code\n"
                                    code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"" + m.get("capability") + "\", \"\${" + m.get("name") + ".getName()}\", \"action\")"
                                    insertCodeMap.add(["code": code, "lineNumber": recvex.getLineNumber(), "addedLine": 2, "exception": 0])
                                }
                            }
                            for (Map m : closureDeviceNames) {
                                if (recvex.getName().equals(m.get("closureDevice"))) {
                                    thereisaction = true
                                    String code = "\t//Inserted Code\n"
                                    code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"" + m.get("capability") + "\", \"\${" + m.get("realDevice") + ".getName()}\", \"action\")"
                                    insertCodeMap.add(["code": code, "lineNumber": recvex.getLineNumber(), "addedLine": 2, "exception": 0])
                                }
                            }

                        }
                        if (methText.equals("sendSms") || methText.equals("sendPush") || methText.equals("sendNotificationToContacts") || methText.equals("sendNotification")) {
                            thereisaction = true
                            String code = "\t//Inserted Code\n"
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"send\", \"this\", \"action\")"
                            insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber(), "addedLine": 2, "exception": 0])

                        }
                        if (methText.equals("setLocationMode")) {
                            thereisaction = true
                            String code = "\t//Inserted Code\n"
                            code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"setLocationMode\", \"this\", \"action\")"
                            insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber(), "addedLine": 2, "exception": 0])
                        }

                        if(thereisaction == false) {
                            for(String s : actionSet) {
                                if(s.equals(methText)) { // 만약 action method 가 존재하는데 action으로 인식하지 못하는 경우
                                    String code = "\t//Inserted Code\n"
                                    code += "\tsmartAppMonitor.setData(app.getName(), \"" + methText + "\", \"Capability\", \"Devices\", \"action\")"
                                    insertCodeMap.add(["code": code, "lineNumber": mce.getLineNumber(), "addedLine": 2, "exception": 0])
                                }
                            }
                        }
                    }
                }
            }


            super.visitMethodCallExpression(mce)

        }


        // if there is not any { and } in the if-else block
        @Override
        void visitIfElse(IfStatement ifElse) {
            Statement ifStat = ifElse.getIfBlock()
            Statement elseStat = ifElse.getElseBlock()
            if(!ifStat.getText().contains("{")) {
                if(ifElse.getLineNumber() == ifStat.getLineNumber()) { // if if-Stat doesn't have any { } and the stat has only one line
                    def code = of.getFile().readLines().get(ifStat.getLineNumber()+numberOfLineAdded)
                    def code2 = code.substring(ifStat.getColumnNumber()-1, ifStat.getLastColumnNumber()-1)
                    insertCodeMap.add(["code": code2, "lineNumber": ifStat.getLineNumber(), "addedLine": 1, "exception": 1]) //execption: 1
                }
                else { // if if-Stat doesn't have any { }
                    inIfStat = true
                    insertCodeMap.add(["code": "\t{", "lineNumber": ifStat.getLineNumber(), "addedLine": 1, "exception": 0])
                    insertCodeMap.add(["code": "\t}", "lineNumber": ifStat.getLineNumber() + 1, "addedLine": 1, "exception": 0])
                }
            }

            // i think this part has not completed yet
            if(!elseStat.getAt("class").toString().contains("EmptyStatement")) {
                if(!elseStat.getText().contains("{")) { //if else-stat doesn't have any { }
                    if(elseStat.hasProperty("ifBlock")) { // if the block is ifelse block
                        if (elseStat.getAt("ifBlock") != null) {

                        }
                    }
                    else {
                        if (elseStat.getColumnNumber() != -1) {
                            insertCodeMap.add(["code": "\t{", "lineNumber": elseStat.getLineNumber(), "addedLine": 1, "exception": 0])
                            insertCodeMap.add(["code": "\t}", "lineNumber": elseStat.getLineNumber() + 1, "addedLine": 1, "exception": 0])
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

    private void codeInsert(String code, int lineNum, int addLine, int exceptionBool) {
        if(lineNum >= 0) {
            def f = of.getFile()
            def lines = f.readLines()
            int numberOfLineAdded2 = 0
            if(exceptionBool == 1 || exceptionBool == 2 || exceptionBool == 3) { // case1 or case2 or case3
                if(exceptionBool == 1) { //case1: if if-block dont have { } and have only one line
                    File fileToBeModified = of.getFile()
                    String oldContent = ""
                    BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))
                    String line = reader.readLine()
                    int lineNum2 = 1;
                    while (line != null) {
                        if (lineNum + numberOfLineAdded + 1 == lineNum2) {
                            def temp_line = ""
                            if (line.substring(line.length() - code.length() - 1)[0] != ')') {
                                def temp_line1 = "{\n" + line.substring(line.length() - code.length() - 1) + "\n}"
                                def temp_line2 = line.substring(0, line.length() - code.length() - 1)
                                temp_line = temp_line2 + temp_line1
                            }

                            oldContent = oldContent + temp_line + System.lineSeparator()
                            numberOfLineAdded2 += 2
                        } else {
                            oldContent = oldContent + line + System.lineSeparator()
                        }
                        line = reader.readLine()
                        lineNum2++
                    }
                    String newContent = oldContent
                    FileWriter writer = new FileWriter(fileToBeModified)
                    writer.write(newContent)

                    reader.close()
                    writer.close()
                    numberOfLineAdded += numberOfLineAdded2
                }
                else if(exceptionBool == 2) { //case2: if method have only one line
                    File fileToBeModified = of.getFile()
                    String oldContent = ""
                    BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))
                    String line = reader.readLine()
                    int lineNum2 = 1;
                    while (line != null) {
                        if (lineNum + numberOfLineAdded + 1 == lineNum2) {
                            def ind = line.indexOf('{')
                            def ind2 = line.indexOf('}')
                            def temp_line1 = line.substring(0, ind+1)
                            def temp_line2 = line.substring(ind+1, ind2)
                            def temp_line = temp_line1 + "\n" + code + "\n" + temp_line2 + "\n}"
                            oldContent = oldContent + temp_line + System.lineSeparator()
                            numberOfLineAdded2 += addLine
                            numberOfLineAdded2 += 2
                        } else {
                            oldContent = oldContent + line + System.lineSeparator()
                        }
                        line = reader.readLine()
                        lineNum2++
                    }
                    String newContent = oldContent
                    FileWriter writer = new FileWriter(fileToBeModified)
                    writer.write(newContent)

                    reader.close()
                    writer.close()
                    numberOfLineAdded += numberOfLineAdded2
                }
                /*else if(exceptionBool == 3) {
                    File fileToBeModified = of.getFile()
                    String oldContent = ""
                    BufferedReader reader = new BufferedReader(new FileReader(fileToBeModified))
                    String line = reader.readLine()
                    int lineNum2 = 1;
                    while (line != null) {
                        if (lineNum + numberOfLineAdded + 1 == lineNum2) {
                            println code
                        } else {
                            //oldContent = oldContent + line + System.lineSeparator()
                        }
                        oldContent = oldContent + line + System.lineSeparator()
                        line = reader.readLine()
                        lineNum2++
                    }
                    String newContent = oldContent
                    FileWriter writer = new FileWriter(fileToBeModified)
                    writer.write(newContent)

                    reader.close()
                    writer.close()
                    //println code
                }*/
            }
            else if(exceptionBool == 0) { //general case
                lines = lines.plus(lineNum + numberOfLineAdded, code)
                f.text = lines.join('\n')
                numberOfLineAdded += addLine
            }
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

    // store action Set (get the action information from the database)
    void setActionSet() {
        /*def sql = Sql.newInstance('jdbc:mysql://203.252.195.182:3306/api_smartAppMonitor_test?autoReconnect=true&useSSL=false',
                'root', '1234', 'com.mysql.jdbc.Driver')

        sql.eachRow('select * from Capabilities') {
            tp ->
                if(tp.action != null) {
                    String temp = tp.action
                    def actionSt = temp.split()
                    for(String s : actionSt) {
                        actionSet.add(s)
                    }
                }

        }

        sql.close()*/
        actionSet = ["setLightingMode", "setAirConditionerMode", "both", "off", "siren", "strobe", "setMute", "mute", "unmute",
         "setVolume", "volumeUp", "volumeDown", "setColor", "setHue", "setSaturation", "setColorTemperature", "setDishwasherMode",
         "setMachineState", "close", "open", "setDryerMode", "setMachineState", "close", "open", "setFanSpeed", "setInfraredLevel",
         "lock", "unlock", "setInputSource", "setPlaybackRepeatMode", "setPlaybackShuffle", "setPlaybackStatus", "play", "pause", "stop",
         "fastForward", "rewind", "setOvenMode", "setMachineState", "stop", "setOvenSetpoint", "setRapidCooling", "setRefrigerationSetpoint",
         "setRobotCleanerCleaningMode", "setRobotCleanerMovement", "setRobotCleanerTurboMode", "setLevel", "off", "on", "setCoolingSetpoint",
         "fanAuto", "fanCirculate", "fanOn", "setThermostatFanMode", "setHeatingSetpoint", "auto", "cool", "emergencyHeat", "heat", "off", "setThermostatMode",
         "beep", "setTvChannel", "channelUp", "channelDown", "close", "open", "setWasherMode", "setMachineState", "close", "open", "presetPosition" ]

    }

    //for multiple files -> reset variables for each file
    void resetVariables() {
        numberOfLineAdded = 0
        closureDeviceNames = new ArrayList<Map>()

        deviceNames = new HashSet<String>()
        inputDeviceNames = new HashSet<String>()
        outputDeviceNames = new HashSet<String>()
        eventNames = new HashSet<String>()
        handlerMethodNames = new HashSet<String>()
        insertCodeMap = new ArrayList<Map>()
        deviceNames2 = new HashSet<Map>()

        device_handlerPair = new HashSet<Map>()
        method_returnPair = new HashSet<Map>()
        ternaryLineNumber = new HashSet<Map>()

        pageNames = new ArrayList<String>()
        scheduleMethodNames = new HashSet<String>()
        scheduleTimeandMethod = new HashSet<Map>()

        skipMethod = true
        inIfStat = false
        inHandler = false

        preferenceStartLine = 0

        isPage = false
    }
}
