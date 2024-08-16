package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

public class LengthChecker extends AnalysisVisitor {
    @Override
    protected void buildVisitor() {
        addVisit(Kind.LENGTH_CALL_EXPR, this::visitLengthCall);
    }

    private Void visitLengthCall(JmmNode exprStmt, SymbolTable table){
        var l = exprStmt.get("length");
        if(!l.equals("length")){
            var message = "Invalid length call. Got " + l + " instead of 'length'.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(exprStmt),
                    NodeUtils.getColumn(exprStmt),
                    message,
                    null)
            );
        }
        return null;
    }
}
