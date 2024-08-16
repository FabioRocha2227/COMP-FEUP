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

import java.util.Objects;

public class Types extends AnalysisVisitor {

    private String currentMethod;
    private Type currentMethodType;

    @Override
    protected void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.IF_STMT, this::visitConditional);
        addVisit(Kind.WHILE_STMT, this::visitConditional);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.EXPR_STMT, this::visitExprStmt);
        addVisit(Kind.ARRAY_VALUES_EXPR, this::visitArrayValuesExpr);
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(Kind.NEW_INT_EXPR, this::visitNewIntExpr);
        addVisit(Kind.ASSIGN_ARRAY_STMT, this::visitAssignArrayStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode node, SymbolTable table) {
        return null;
    }

    private Void visitAssignArrayStmt(JmmNode assignStmt, SymbolTable table){
        try {
            var leftExpr = assignStmt.get("name");
            var indexExpr = assignStmt.getChildren().get(0);
            var rightExpr = assignStmt.getChildren().get(1);
            //find left type
            var leftType = TypeUtils.getVarType(leftExpr,assignStmt, table);
            if(!leftType.isArray()){
                var message = "Invalid array assignment. Expected array got " + leftType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        message,
                        null)
                );
            }


            var indexType = TypeUtils.getExprType(indexExpr, table);
            if(!TypeUtils.areTypesAssignable(indexType,TypeUtils.getIntType(),table)){
                var message = "Invalid array index. Expected int got " + indexType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        message,
                        null)
                );
                return null;
            }

            var rightType = TypeUtils.getExprType(rightExpr, table);
            var valid = TypeUtils.areTypesAssignable(rightType, new Type(leftType.getName(),false), table);
            if (!valid) {
                var message = "Invalid assignment: " + rightType.getName() + " to " + leftType.getName();
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        message,
                        null)
                );
            }
            return null;
        }catch (Exception e){
            return null;
        }
    }

    private Void visitNewIntExpr(JmmNode node, SymbolTable table){
        try {
            var e = node.getChild(0);
            var eType = TypeUtils.getExprType(e, table);
            if (eType.isArray() || (!eType.getName().equals("int") && !eType.getName().equals("imported"))) {
                var message = "Invalid new int expression. Expected int got " + eType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        message,
                        null)
                );
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }

    private Void visitArrayAccessExpr(JmmNode node, SymbolTable table){
        try {
            var lhs = node.getChildren().get(0);
            var rhs = node.getChildren().get(1);
            var lhsType = TypeUtils.getExprType(lhs, table);
            var rhsType = TypeUtils.getExprType(rhs, table);
            if(!lhsType.isArray()){
                var message = "Invalid array access expression. Expected array got " + lhsType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        message,
                        null)
                );
            }
            if(!TypeUtils.areTypesAssignable(rhsType,TypeUtils.getIntType(),table)){
                var message = "Invalid array access expression. Expected int got " + rhsType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
                        message,
                        null)
                );
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }

    private Void visitArrayValuesExpr(JmmNode node, SymbolTable table){
        try{
        var es = node.getChildren();
        if(es.isEmpty())return null;
        var t = TypeUtils.getExprType(es.get(0),table);
        for(var e:es){
            var eType = TypeUtils.getExprType(e,table);
            if(!TypeUtils.areTypesAssignable(eType,t,table)){
                var message = "Invalid array values expression. Expected " + t.getName() + " got " + eType.getName() + ".";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(node),
                        NodeUtils.getColumn(node),
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

    private Void visitExprStmt(JmmNode exprStmt, SymbolTable table){
        try {
            TypeUtils.getExprType(exprStmt.getChildren().get(0), table);
            return null;
        }catch(Exception e){
            return null;
        }
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        try {
            var leftExpr = assignStmt.get("name");
            var rightExpr = assignStmt.getChildren().get(0);
            //find left type
            Type leftType = null;
            if (table.getLocalVariables(currentMethod) != null) {
                for (var var : table.getLocalVariables(currentMethod)) {
                    if (var.getName().equals(leftExpr)) {
                        leftType = var.getType();
                        break;
                    }
                }
            }
            if (leftType == null) {
                if(table.getParameters(currentMethod) != null){
                    for (var var : table.getParameters(currentMethod)) {
                        if (var.getName().equals(leftExpr)) {
                            leftType = var.getType();
                            break;
                        }
                    }
                }
            }
            if (leftType == null) {
                for (var var : table.getFields()) {
                    if (var.getName().equals(leftExpr)) {
                        leftType = var.getType();
                        break;
                    }
                }
            }
            if (leftType == null) {
                var message = "Variable " + leftExpr + " not declared";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        message,
                        null)
                );
                return null;
            }

            var rightType = TypeUtils.getExprType(rightExpr, table);
            var valid = TypeUtils.areTypesAssignable(rightType, leftType, table);
            if (!valid) {
                var message = "Invalid assignment: " + rightType.getName() + " to " + leftType.getName();
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assignStmt),
                        NodeUtils.getColumn(assignStmt),
                        message,
                        null)
                );
            }
            return null;
        }catch (Exception e){
            return null;
        }
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table){
            try{
            var returnType = TypeUtils.getExprType(returnStmt.getChildren().get(0).getChildren().get(0), table);

            if (currentMethodType.getName().equals("void")) {
                var message = "Invalid return type: " + returnType.getName() + " expected: " + currentMethodType;
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null)
                );
                return null;
            }
            if (!returnType.getName().equals(currentMethodType.getName()) && !TypeUtils.areTypesAssignable(returnType, currentMethodType, table)) {
                var message = "Invalid return type: " + returnType.getName() + " expected: " + currentMethodType + " but got " + returnType;
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null)
                );
                return null;
            }

            if (currentMethodType.isArray() != returnType.isArray()) {
                var message = "Invalid return type: " + returnType.getName() + " expected: " + currentMethodType + " but got " + returnType;
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null)
                );
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }

    private Void visitConditional(JmmNode conditional, SymbolTable table) {
        try {
            var condition = conditional.getChildren().get(0);
            var conditionType = TypeUtils.getExprType(condition, table);
            if ((!conditionType.getName().equals("boolean") && !conditionType.getName().equals("imported") ) || conditionType.isArray() ) {
                var message = "Invalid type for condition: " + conditionType.getName();
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(conditional),
                        NodeUtils.getColumn(conditional),
                        message,
                        null)
                );
            }
            return null;
        }catch(Exception e){
            return null;
        }
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        if(method.getKind().equals(Kind.MAIN_METHOD_DECL.toString()))
            currentMethodType = new Type("void", false);
        else{
            var methods = table.getMethods();
            for (String method1 : methods) {
                if (method1.equals(currentMethod))
                    currentMethodType = table.getReturnType(method1);
            }
        }

        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        try {
            var op = binaryExpr.get("op");
            var leftExpr = binaryExpr.getChildren().get(0);
            var leftType = TypeUtils.getExprType(leftExpr, table);
            JmmNode rightExpr;
            Type rightType;

            if (!op.equals("!")) {
                rightExpr = binaryExpr.getChildren().get(1);
                rightType = TypeUtils.getExprType(rightExpr, table);
            } else {
                if (!TypeUtils.areTypesAssignable(leftType, new Type("boolean", false), table)) {
                    var message = "Invalid type for operation " + op + ": " + leftType.getName();
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            message,
                            null)
                    );
                }
                return null;
            }


            switch (op) {
                case "+", "-", "*", "/", "<" -> {
                    if (!TypeUtils.areTypesAssignable(leftType, new Type("int",false), table) || !TypeUtils.areTypesAssignable(rightType, new Type("int",false), table)) {
                        var message = "Invalid types for operation " + op + ": " + leftType.getName() + " and " + rightType.getName();
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(binaryExpr),
                                NodeUtils.getColumn(binaryExpr),
                                message,
                                null)
                        );
                        return null;
                    }
                    return null;
                }
                case "&&" -> {
                    if (!TypeUtils.areTypesAssignable(leftType,TypeUtils.getBoolType(), table) || !TypeUtils.areTypesAssignable(rightType,TypeUtils.getBoolType(), table)) {
                        var message = "Invalid types for operation " + op + ": " + leftType.getName() + " and " + rightType.getName();
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(binaryExpr),
                                NodeUtils.getColumn(binaryExpr),
                                message,
                                null)
                        );
                    }
                    return null;
                }

                default -> {
                    var message = "Invalid operator: " + op;
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(binaryExpr),
                            NodeUtils.getColumn(binaryExpr),
                            message,
                            null)
                    );
                    return null;
                }
            }
        }catch (Exception e){
            return null;
        }
    }




}
