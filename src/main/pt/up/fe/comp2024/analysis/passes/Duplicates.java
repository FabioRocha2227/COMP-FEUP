package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

public class Duplicates extends AnalysisVisitor {

    //list of imports
    List<String> imports = new ArrayList<String>();

    @Override
    protected void buildVisitor() {
        addVisit(IMP, this::visitImp);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        //addVisit(MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(CLASS_DECL, this::visitClassDecl);
        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode node, SymbolTable table){
        return null;
    }

    private Void visitImp(JmmNode imp, SymbolTable table){
        var importName = imp.get("name");
        String[] parts = importName.split(",");
        importName = parts[parts.length-1];
        importName = importName.substring(0, importName.length() - 1);
        for(var imps:imports){
            var pImp= imps.split("\\.");
            var nImp = pImp[pImp.length-1];
            if(nImp.equals(importName)){
                var message = "Import " + importName + " is duplicated.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(imp),
                        NodeUtils.getColumn(imp),
                        message,
                        null)
                );
                return null;
            }
        }
        imports.add(importName);
        return null;
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table){
        var methodName = methodDecl.get("name");
        var params = table.getParameters(methodName);

        var locals = table.getLocalVariables(methodName);
        for (int i=0;i<locals.size()-1;i++) {
            var local1=locals.get(i);
            for(int j=i+1;i<locals.size();i++){
                var local2=locals.get(j);
                if(local1.getName().equals(local2.getName())){
                    var message = "Method " + methodName + " has two local variables with the same name.";
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
            for(var param:params){
                if(local1.getName().equals(param.getName())){
                    var message = "Method " + methodName + " has a local variable with the same name as a parameter.";
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


        for (int i=0;i<params.size()-1;i++) {
            var param1=params.get(i);
            for(int j=i+1;i<params.size();i++){
                var param2=params.get(j);
                if(param1.getName().equals(param2.getName())){
                    var message = "Method " + methodName + " has two parameters with the same name.";
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

        return null;
    }

    private Void visitClassDecl(JmmNode classDecl, SymbolTable table){
        var className = classDecl.get("name");

        var fields = table.getFields();
        for (int i=0;i<fields.size()-1;i++) {
            var field1=fields.get(i);
            for(int j=i+1;i<fields.size();i++){
                var field2=fields.get(j);
                if(field1.getName().equals(field2.getName())){
                    var message = "Class " + className + " has two fields with the same name. "+field1.getName()+" and "+field2.getName();
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(classDecl),
                            NodeUtils.getColumn(classDecl),
                            message,
                            null)
                    );
                    return null;
                }
            }
        }

        var methods = table.getMethods();
        for (int i=0;i<methods.size()-1;i++) {
            var method1=methods.get(i);
            for(int j=i+1;i<methods.size();i++){
                var method2=methods.get(j);
                if(method1.equals(method2)){
                    var message = "Class " + className + " has two methods with the same name.";
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(classDecl),
                            NodeUtils.getColumn(classDecl),
                            message,
                            null)
                    );
                    return null;
                }
            }
        }


        return null;
    }

}
