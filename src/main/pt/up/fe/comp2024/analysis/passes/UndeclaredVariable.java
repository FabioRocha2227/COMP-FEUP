package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.List;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.METHOD_CALL_ON_OBJECT_EXPR, this::visitMethodCallOnObject);

        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode n, SymbolTable s) {
        return null;
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        // Check if exists a parameter or variable declaration with the same name as the variable reference
        var varRefName = varRefExpr.get("name");

        // Var is a field, return
        if (table.getFields().stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a parameter, return
        if (table.getParameters(currentMethod).stream()
                .anyMatch(param -> param.getName().equals(varRefName))) {
            return null;
        }

        // Var is a declared variable, return
        if (table.getLocalVariables(currentMethod).stream()
                .anyMatch(varDecl -> varDecl.getName().equals(varRefName))) {
            return null;
        }

        //print all imports
        List<List<String>> imports = ((JmmSymbolTable)table).getImportsList();
        for (List<String> imp : imports) {
            if(imp.get(imp.size()-1).equals(varRefName)){
                return null;
            }
        }

        // Create error report
        var message = String.format("Variable/Object '%s' does not exist.", varRefName);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                message,
                null)
        );

        return null;
    }

    private Void visitMethodCallOnObject(JmmNode methodCallOnObject, SymbolTable table) {
        try {
            var et = TypeUtils.getExprType(methodCallOnObject.getChild(0), table);

            if (et.getName().equals("imported")) {
                return null;
            } else if (et.getName().equals(table.getClassName())) {
                var methods = table.getMethods();
                for (String method : methods) {
                    if (method.equals(methodCallOnObject.get("method"))) {
                        return null;
                    }
                }
                if (!table.getSuper().isEmpty()) {
                    return null;
                }
                var message = String.format("Method '%s' does not exist in class '%s'.", methodCallOnObject.get("method"), table.getClassName());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCallOnObject),
                        NodeUtils.getColumn(methodCallOnObject),
                        message,
                        null)
                );
            } else {
                var obj =methodCallOnObject.getChild(0);
                var imports = ((JmmSymbolTable)table).getImportsList();
                for (var imps : imports) {
                    var imp = imps.get(imps.size() - 1);
                    if (imp.equals(et.getName())) {
                        return null;
                    }
                    if(obj.getChild(0).getKind().equals(Kind.VAR_REF_EXPR.toString())){
                        var varName = obj.getChild(0).get("name");
                        if(varName.equals(imp)){
                            return null;
                        }
                    }
                }
            }
            var message = String.format("Method '%s' does not exist in class '%s'.", methodCallOnObject.get("method"), et.getName());
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCallOnObject),
                    NodeUtils.getColumn(methodCallOnObject),
                    message,
                    null)
            );
            return null;
        }catch(Exception e){
            return null;
        }
    }
}
