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
public class InvalidParameters extends AnalysisVisitor {

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_CALL_ON_OBJECT_EXPR, this::visitMethodCallOnObject);

        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode n, SymbolTable s){return null;}

    private Void visitMethodCallOnObject(JmmNode methodCall, SymbolTable table){
        try{
            var e=methodCall.getChild(0);
            var eType=TypeUtils.getExprType(methodCall.getChild(0), table);
            if(eType.getName().equals("imported")){
                return null;
            }
            var imports=((JmmSymbolTable)table).getImportsList();
            for(var ii:imports){
                var i=ii.get(ii.size()-1);
                if(i.equals(eType.getName())){
                    return null;
                }
                if (e.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
                    if (e.get("name").equals(i)) {
                        return null;
                    }
                }
            }

        var methodName = methodCall.get("method");
        var params = methodCall.getChildren(Kind.EXPR);
        var decParams = table.getParameters(methodName);
        if(decParams == null && params.size()==1) return null;
        if(decParams == null){
            var message = "Method " + methodName + " has no parameters, but was called with " + params.size() + " parameters.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall),
                    NodeUtils.getColumn(methodCall),
                    message,
                    null)
            );
            return null;
        }
        var ii=0;
        if(params.size()<=decParams.size()){
            var message = "Method " + methodName + " has " + decParams.size() + " parameters, but was called with " + (params.size()-1) + " parameters.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall),
                    NodeUtils.getColumn(methodCall),
                    message,
                    null)
            );
            return null;
        }
        for(int i = 1; i < params.size(); i++){

            var param = params.get(i);
            var paramType = TypeUtils.getExprType(param, table);
            var methodParam = decParams.get(ii);
            if(methodParam.getType().hasAttribute("varArg")){
                if(TypeUtils.areTypesAssignable(paramType, methodParam.getType(), table)){
                    return null;
                }
                var message = "Parameter " + i + " of method " + methodName + " is of type " + paramType + ", but should be of type " + methodParam.getType() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(param),
                        NodeUtils.getColumn(param),
                        message,
                        null)
                );
                return null;
            }else ii=i;
            if(!TypeUtils.areTypesAssignable(paramType, methodParam.getType(), table)){
                var message = "Parameter " + i + " of method " + methodName + " is of type " + paramType + ", but should be of type " + methodParam.getType() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(param),
                        NodeUtils.getColumn(param),
                        message,
                        null)
                );
            }
        }

        return null;
        }catch(Exception e){
            return null;
        }
    }

}
