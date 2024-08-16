package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

public class ReturnChecker extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMainMethodDecl);
        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode node, SymbolTable table){
        return null;
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table){
        if(methodDecl.getChildren(Kind.RETURN_STMT).isEmpty()){
            var message = "Method " + methodDecl.get("name") + " does not have a return statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
            return null;
        }
        if(methodDecl.getChildren(Kind.RETURN_STMT).size() > 1){
            var message = "Method " + methodDecl.get("name") + " has more than one return statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
            return null;
        }


        if(!methodDecl.getChildren().get(methodDecl.getNumChildren()-1).getKind().equals(Kind.RETURN_STMT.toString())){
            var message = "Method " + methodDecl.get("name") + " does not have a return statement as the last statement.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
            return null;
        }

        return null;
    }

    private Void visitMainMethodDecl(JmmNode mainMethodDecl, SymbolTable table){
        if(!mainMethodDecl.getChildren(Kind.RETURN_STMT).isEmpty()){
            var message = "Main method cannot have a return statement, as it is void.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(mainMethodDecl),
                    NodeUtils.getColumn(mainMethodDecl),
                    message,
                    null)
            );
        }
        return null;
    }

}
