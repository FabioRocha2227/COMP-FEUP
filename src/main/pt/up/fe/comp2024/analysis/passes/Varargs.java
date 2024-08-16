package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

public class Varargs extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);

        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode n, SymbolTable s){return null;}

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table){
        if(varDecl.getChild(0).hasAttribute("varArg")){
            var message = "Variable/Field declaration " + varDecl.get("name") + " cannot be vararg.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varDecl),
                    NodeUtils.getColumn(varDecl),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitMethodDecl(JmmNode methodDecl, SymbolTable table){
        if(methodDecl.getChild(0).hasAttribute("varArg")){
            var message = "Method return type cannot be varargs";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
            return null;
        }

        var sum=0;
        var lastParam=false;

        var params = table.getParameters(methodDecl.get("name"));
        int n = params.size();
        for(int i = 0;i<n; i++){
            if(params.get(i).getType().hasAttribute("varArg")){
                if(i == n - 1) lastParam=true;
                sum++;
            }
        }

        if(sum>1){
            var message = "There can only one vararg parameter in the method declaration " + methodDecl.get("name");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
        }else if(!lastParam && sum==1){
            var message = "A vararg type must be the last parameter in method declaration " + methodDecl.get("name");
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodDecl),
                    NodeUtils.getColumn(methodDecl),
                    message,
                    null)
            );
        }

        return null;
    }

}
