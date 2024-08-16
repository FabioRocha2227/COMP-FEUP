package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

public class Statics extends AnalysisVisitor {
    @Override
    protected void buildVisitor() {
        addVisit(MAIN_METHOD_DECL, this::visitMethodDecl);

        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode node, SymbolTable table) {
        return null;
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table) {
        try{
            var methodName = methodDecl.get("name");
            if(!methodDecl.get("name").equals("main")){
                var message = "Only method main can be static void.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null)
                );
            }

            if (methodDecl.getDescendants().stream().anyMatch(node -> node.getKind().equals(THIS_EXPR.toString()))) {
                var message = "Method " + methodName + " is static but has a reference to this.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null)
                );
                return null;
            }

            var fields = table.getFields();
            var varRefs=methodDecl.getDescendants().stream().filter(node -> node.getKind().equals(VAR_REF_EXPR.toString())).toList();
            //get left side of assignment
            var leftSides=methodDecl.getDescendants().stream().filter(node -> node.getKind().equals(ASSIGN_STMT.toString())).toList();
            var leftsideA=methodDecl.getDescendants().stream().filter(node -> node.getKind().equals(ASSIGN_ARRAY_STMT.toString())).toList();

            for(var varRef:varRefs){
                for(var f:fields){
                    System.out.println("Field: "+f.getName()+" VarRef: "+varRef.get("name"));
                    if(f.getName().equals(varRef.get("name"))){
                        System.out.println("Field: "+f.getName()+" VarRef: "+varRef.get("name"));

                        //if that varRef is a local variable
                        if(table.getLocalVariables(methodName).stream().anyMatch(field -> field.getName().equals(varRef.get("name")))){
                            return null;
                        }
                        //if that varRef is a parameter
                        if(table.getParameters(methodName).stream().anyMatch(field -> field.getName().equals(varRef.get("name")))){
                            return null;
                        }

                        var message = "Method " + methodName + " is static but has a reference to a field.";
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodDecl),
                                NodeUtils.getColumn(methodDecl),
                                message,
                                null)
                        );
                        return null;
                    }
                }
            }
            for(var left:leftSides){
                for(var f:fields){
                    System.out.println("Field: "+f.getName()+" VarRef: "+left.get("name"));
                    if(f.getName().equals(left.get("name"))){
                        System.out.println("Field: "+f.getName()+" VarRef: "+left.get("name"));

                        //if that varRef is a local variable
                        if(table.getLocalVariables(methodName).stream().anyMatch(field -> field.getName().equals(left.get("name")))){
                            return null;
                        }
                        //if that varRef is a parameter
                        if(table.getParameters(methodName).stream().anyMatch(field -> field.getName().equals(left.get("name")))){
                            return null;
                        }

                        var message = "Method " + methodName + " is static but has a reference to a field.";
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodDecl),
                                NodeUtils.getColumn(methodDecl),
                                message,
                                null)
                        );
                        return null;
                    }
                }
            }
            for (var left:leftsideA){
                for(var f:fields){
                    System.out.println("Field: "+f.getName()+" VarRef: "+left.get("name"));
                    if(f.getName().equals(left.get("name"))){
                        System.out.println("Field: "+f.getName()+" VarRef: "+left.get("name"));

                        //if that varRef is a local variable
                        if(table.getLocalVariables(methodName).stream().anyMatch(field -> field.getName().equals(left.get("name")))){
                            return null;
                        }
                        //if that varRef is a parameter
                        if(table.getParameters(methodName).stream().anyMatch(field -> field.getName().equals(left.get("name")))){
                            return null;
                        }

                        var message = "Method " + methodName + " is static but has a reference to a field.";
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodDecl),
                                NodeUtils.getColumn(methodDecl),
                                message,
                                null)
                        );
                        return null;
                    }
                }
            }

            /*if (varRefs.stream().anyMatch(node -> fields.stream().anyMatch(field -> field.getName().equals(node.get("name"))))) {

                //if that varRef is a local variable
                if(varRefs.stream().anyMatch(node -> table.getLocalVariables(methodName).stream().anyMatch(field -> field.getName().equals(node.get("name"))))){
                    return null;
                }
                //if that varRef is a parameter
                if(varRefs.stream().anyMatch(node -> table.getParameters(methodName).stream().anyMatch(field -> field.getName().equals(node.get("name"))))){
                    return null;
                }
                var message = "Method " + methodName + " is static but has a reference to a field.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodDecl),
                        NodeUtils.getColumn(methodDecl),
                        message,
                        null)
                );
            }*/

        return null;
        }catch (Exception e){
            return null;
        }
    }
}
