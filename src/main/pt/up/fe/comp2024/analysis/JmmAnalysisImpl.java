package pt.up.fe.comp2024.analysis;

import org.antlr.v4.runtime.misc.Pair;
import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.passes.*;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTableBuilder;

import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JmmAnalysisImpl implements JmmAnalysis {


    private final List<AnalysisPass> analysisPasses;

    public JmmAnalysisImpl() {

        this.analysisPasses = List.of(new UndeclaredVariable(), new Types(), new Varargs(), new InvalidParameters(), new ReturnChecker(), new Duplicates(), new Statics(), new LengthChecker());
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        JmmNode rootNode = parserResult.getRootNode();

        SymbolTable table = JmmSymbolTableBuilder.build(rootNode);

        List<Report> reports = new ArrayList<>();

        // Visit all nodes in the AST
        for (var analysisPass : analysisPasses) {
            try {
                var passReports = analysisPass.analyze(rootNode, table);
                reports.addAll(passReports);
            } catch (Exception e) {
                reports.add(Report.newError(Stage.SEMANTIC,
                        -1,
                        -1,
                        "Problem while executing analysis pass '" + analysisPass.getClass() + "'",
                        e)
                );
            }

        }
        System.out.println("Reports: "+reports);

        // Transform varargs in arrays in method calls
        transformVarargs(rootNode, table);

        // Optimize the code if the flag is set
        if(parserResult.getConfig().containsKey("optimize")){
            var changedF = true;
            var changedP = true;
            while(changedF || changedP){
                var cp = new ConstProp();
                changedF = constantFolding(rootNode);
                cp.analyze(rootNode, table);
                changedP = cp.hasChanged();
            }
        }

        return new JmmSemanticsResult(parserResult, table, reports);
    }

    private void transformVarargs(JmmNode node, SymbolTable table){

        var methods = node.getDescendants().stream().filter(n -> n.getKind().equals("MethodCallOnObjectExpr")).toArray();
        for (var method : methods){
            var methodNode = (JmmNode) method;
            var methodName = methodNode.get("method");
            var varArgIndex = getVarargsIndex(methodName, table);
            if (varArgIndex == -1){
                continue;
            }
            var args = methodNode.getChildren().subList(1, methodNode.getNumChildren());
            var newNode = new JmmNodeImpl("ArrayValuesExpr");
            for(int i = varArgIndex; i < args.size(); i++){
                var arg = args.get(i);
                newNode.add(arg);
            }
            // Remove the arguments from the method call
            for(int i = args.size(); i >= varArgIndex + 1; i--){
                methodNode.removeChild(i);
            }
            // Add the new node to the method call
            methodNode.add(newNode);
        }
    }

    private int getVarargsIndex(String methodName, SymbolTable table){
        var params = table.getParameters(methodName);
        if (params == null){
            return -1;
        }
        for (int i = 0; i < params.size(); i++){
            if (params.get(i).getType().hasAttribute("varArg")){
                return i;
            }
        }
        return -1;
    }

    private boolean constantFolding(JmmNode node){
        var changed = false;
        for (var n : node.getDescendants()) {
            if (n.getKind().equals("BinaryExpr")) {
                var left = n.getChild(0);
                var op = n.get("op");
                if (op.equals("!")) {
                    if (left.getKind().equals("BooleanLiteral")) {
                        var value = left.get("value");
                        if (value.equals("true")) value = "false";
                        else value = "true";
                        JmmNodeImpl newBoolean = new JmmNodeImpl("BooleanLiteral");
                        newBoolean.put("value", value);
                        n.replace(newBoolean);
                        changed = true;
                    }
                    continue;
                }
                var right = n.getChild(1);
                switch (op) {
                    case "+" -> {
                        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                            var leftValue = Integer.parseInt(left.get("value"));
                            var rightValue = Integer.parseInt(right.get("value"));
                            var result = leftValue + rightValue;
                            JmmNodeImpl newInt = new JmmNodeImpl("IntegerLiteral");
                            System.out.println(Integer.toString(result));
                            newInt.put("value", Integer.toString(result));
                            n.replace(newInt);
                            changed = true;
                        }
                    }
                    case "-" -> {
                        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                            var leftValue = Integer.parseInt(left.get("value"));
                            var rightValue = Integer.parseInt(right.get("value"));
                            var result = leftValue - rightValue;
                            JmmNodeImpl newInt = new JmmNodeImpl("IntegerLiteral");
                            newInt.put("value", Integer.toString(result));
                            n.replace(newInt);
                            changed = true;
                        }
                    }
                    case "*" -> {
                        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                            var leftValue = Integer.parseInt(left.get("value"));
                            var rightValue = Integer.parseInt(right.get("value"));
                            var result = leftValue * rightValue;
                            JmmNodeImpl newInt = new JmmNodeImpl("IntegerLiteral");
                            newInt.put("value", Integer.toString(result));
                            n.replace(newInt);
                            changed = true;
                        }
                    }
                    case "/" -> {
                        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                            var leftValue = Integer.parseInt(left.get("value"));
                            var rightValue = Integer.parseInt(right.get("value"));
                            var result = leftValue / rightValue;
                            JmmNodeImpl newInt = new JmmNodeImpl("IntegerLiteral");
                            newInt.put("value", Integer.toString(result));
                            n.replace(newInt);
                            changed = true;
                        }
                    }
                    case "<" -> {
                        if (left.getKind().equals("IntegerLiteral") && right.getKind().equals("IntegerLiteral")) {
                            var leftValue = Integer.parseInt(left.get("value"));
                            var rightValue = Integer.parseInt(right.get("value"));
                            var result = leftValue < rightValue;
                            JmmNodeImpl newBoolean = new JmmNodeImpl("BooleanLiteral");
                            newBoolean.put("value", Boolean.toString(result));
                            n.replace(newBoolean);
                            changed = true;
                        }
                    }
                    case "&&" -> {
                        if (left.getKind().equals("BooleanLiteral") && right.getKind().equals("BooleanLiteral")) {
                            var leftValue = left.get("value");
                            var rightValue = right.get("value");
                            var result = Boolean.parseBoolean(leftValue) && Boolean.parseBoolean(rightValue);
                            JmmNodeImpl newBoolean = new JmmNodeImpl("BooleanLiteral");
                            newBoolean.put("value", Boolean.toString(result));
                            n.replace(newBoolean);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private boolean constantPropagation(JmmNode node, SymbolTable table){
        var changed = false;
        // Create map with all the variables and their values
        var variables = new HashMap<String, String>();
        for (var n : node.getDescendants()) {
            if(n.getKind().equals("AssignStmt")){
                var varName = n.get("name");
                var value = n.getChild(0);
                //System.out.println("AssignExpr: "+varName+" "+value.getKind());
                if(value.getKind().equals("IntegerLiteral") || value.getKind().equals("BooleanLiteral")){
                    variables.put(varName, value.get("value"));
                }
            }

            if(n.getKind().equals("VarRefExpr")){
                //System.out.println("VarRefExpr: "+n.get("name")+" "+n.getParent().getKind() + " Variables " + variables);
                var varName = n.get("name");
                if(variables.containsKey(varName)){
                    var value = variables.get(varName);
                    if(value.equals("true") || value.equals("false")) {
                        JmmNodeImpl newBoolean = new JmmNodeImpl("BooleanLiteral");
                        newBoolean.put("value", value);
                        // Replace the variable with the value
                        n.replace(newBoolean);
                    }
                    else {
                        JmmNodeImpl newInt = new JmmNodeImpl("IntegerLiteral");
                        newInt.put("value", value);
                        n.replace(newInt);
                    }
                    //remove from map
                    variables.remove(varName);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
