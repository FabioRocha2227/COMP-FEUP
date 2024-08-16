package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.sql.Array;

import static pt.up.fe.comp2024.ast.Kind.*;
import static pt.up.fe.comp2024.ast.TypeUtils.isClass;
import static pt.up.fe.comp2024.ast.TypeUtils.isField;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult>{

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private int ifCounter = -1;

    protected int ifnumber (){
        ifCounter++;
        return ifCounter;
    }


    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;

        buildVisitor();
    }

    @Override
    protected void buildVisitor() {

        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(THIS_EXPR, this::visitThis);
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObjectExpr);
        addVisit(METHOD_CALL_ON_OBJECT_EXPR, this::visitMethodCall);
        addVisit(NEW_INT_EXPR, this::visitNewIntExpr);
        addVisit(ARRAY_VALUES_EXPR, this::visitArrayValuesExpr);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(LENGTH_CALL_EXPR, this::visitLengthCallExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitLengthCallExpr(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var object = visit(jmmNode.getJmmChild(0));

        computation.append(object.getComputation());

        var temp = OptUtils.getTemp() + ".i32";
        computation.append(temp)
                .append(SPACE)
                .append(ASSIGN)
                .append(".i32")
                .append(SPACE)
                .append("arraylength(")
                .append(object.getCode())
                .append(").i32")
                .append(END_STMT);

        code.append(temp);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var lhs = visit(jmmNode.getJmmChild(0));
        var rhs = visit(jmmNode.getJmmChild(1));

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        String[] lhsCode = lhs.getCode().split("\\.");

        var temp = OptUtils.getTemp() + ".i32";
        computation.append(temp)
                .append(SPACE)
                .append(ASSIGN)
                .append(".i32")
                .append(SPACE)
                .append(lhsCode[0])
                .append("[")
                .append(rhs.getCode())
                .append("].i32")
                .append(END_STMT);

        code.append(temp);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitArrayValuesExpr(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var values = jmmNode.getChildren();
        var size = values.size();

        var temp = OptUtils.getTemp();
        Type type = TypeUtils.getExprType(jmmNode, table);
        var typeStr = OptUtils.toOllirType(type);
        computation.append(temp)
                .append(typeStr)
                .append(SPACE)
                .append(ASSIGN)
                .append(typeStr)
                .append(SPACE)
                .append("new(array, ")
                .append(size)
                .append(".i32)")
                .append(typeStr)
                .append(END_STMT);

        for(int i = 0; i < size; i++) {
            var value = visit(values.get(i));
            computation.append(value.getComputation());
            computation.append(temp)
                    .append("[")
                    .append(i)
                    .append(".i32]")
                    .append(OptUtils.toOllirType(TypeUtils.getExprType(values.get(i), table)))
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeStr)
                    .append(SPACE)
                    .append(value.getCode())
                    .append(END_STMT);
        }

        code.append(temp)
                .append(typeStr);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        return new OllirExprResult("this");
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        var boolType = new Type(TypeUtils.getBooleanTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(boolType);
        String code = node.get("value").equals("true") ? "1" + ollirBoolType : "0" + ollirBoolType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        String code = "";

        if(node.hasAttribute("op") && node.get("op").equals("!")){

            var lhs = visit(node.getJmmChild(0));

            StringBuilder computation = new StringBuilder();

            computation.append(lhs.getComputation());

            Type resType = TypeUtils.getExprType(node, table);
            String resOllirType = OptUtils.toOllirType(resType);
            String ret = OptUtils.getTemp() + resOllirType;
            code = code + ret;

            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(lhs.getCode()).append(SPACE);



            computation.append(node.get("op"));

            computation.append(OptUtils.toOllirType(resType)).append(SPACE)
                    .append(lhs.getCode()).append(END_STMT);

            code = ret;

            return new OllirExprResult(code, computation);
        }

        else if(node.get("op").equals("<")){

            var lhs = visit(node.getJmmChild(0));
            var rhs = visit(node.getJmmChild(1));

            StringBuilder computation = new StringBuilder();

            computation.append(lhs.getComputation());
            computation.append(rhs.getComputation());

            computation.append("if (").append(lhs.getCode()).append(" ").append(" <").append(".bool ").append(rhs.getCode()).append(") goto ").append("if").append(ifnumber()).append(END_STMT);
            var temp3 = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getExprType(node, table));
            computation.append("\t").append(temp3).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("0").append(".bool").append(END_STMT);
            computation.append("\tgoto endif").append(ifCounter).append(END_STMT);

            computation.append("if").append(ifCounter).append(":").append("\n");
            computation.append("\t").append(temp3).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("1").append(".bool").append(END_STMT);

            computation.append("endif").append(ifCounter).append(":\n");

            code = temp3;

            return new OllirExprResult(code, computation);
        }
        else if(node.get("op").equals("&&")){

            var lhs = visit(node.getJmmChild(0));
            var rhs = visit(node.getJmmChild(1));

            StringBuilder computation = new StringBuilder();
            var ifN = ifnumber();

            var temp = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getExprType(node, table));
            computation.append(lhs.getComputation());
            computation.append("if (").append(lhs.getCode()).append(") goto ").append("if").append(ifN).append(END_STMT);
            computation.append("\t").append(temp).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("0").append(".bool").append(END_STMT);
            computation.append("\tgoto endif").append(ifN).append(END_STMT);

            computation.append("if").append(ifN).append(":").append("\n");
            computation.append(rhs.getComputation());
            computation.append(temp).append(SPACE).append(ASSIGN).append(OptUtils.toOllirType(TypeUtils.getExprType(node, table))).append(SPACE).append(rhs.getCode()).append(END_STMT);

            computation.append("endif").append(ifN).append(":\n");

            code = temp;
            return new OllirExprResult(code, computation);
        }



        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String ret = OptUtils.getTemp() + resOllirType;
        code = code + ret;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);



        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op"));

        computation.append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);


        code = ret;

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitParenExpr(JmmNode node, Void unused) {
        return visit(node.getJmmChild(0));
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);
        if(isField(node.get("name"), node, table)){
            var temp = OptUtils.getTemp() + ollirType;
            computation.append(temp).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE).append("getfield(this, ").append(node.get("name")).append(")").append(ollirType).append(END_STMT);
            code.append(temp);
        }
        else{
            var id = node.get("name");
            code.append(id).append(ollirType);
        }

        return new OllirExprResult(code.toString());
    }

    private boolean isObject(JmmNode node){
        boolean is = false;
        var methodname = node.get("method");
        for (var param : table.getParameters(methodname)){
            if (node.getJmmChild(0).get("name").equals(param.getName())){
                is = true;
            }
        }
        for (var field : table.getFields()){
            if (node.getJmmChild(0).get("name").equals(field.getName())){
                is = true;
            }
        }
        for (var local : table.getLocalVariables(methodname)){
            if (node.getJmmChild(0).get("name").equals(local.getName())){
                is = true;
            }
        }
        return is;
    }



    private OllirExprResult visitMethodCall(JmmNode node, Void unused){

        var methodName = node.get("method");
        var args = node.getChildren();
        args = args.subList(1, args.size());

        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();
        StringBuilder argsString = new StringBuilder();

        var object = node.getJmmChild(0);

        boolean staticmethod = false;



        if(!object.getKind().equals(THIS_EXPR.toString())){
            staticmethod = isStatic(object);
        }

        for(var arg : args){
            var argResult = visit(arg);
            computation.append(argResult.getComputation());
            argsString.append(", ").append(argResult.getCode());
        }

        StringBuilder invoke = new StringBuilder();

        if(staticmethod){
            invoke.append("invokestatic(")
                .append(object.get("name"))
                .append(", \"")
                .append(methodName)
                .append("\"")
                .append(argsString)
                .append(")")
                .append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)));
        }
        else{
            invoke.append("invokevirtual(")
                .append(visit(object).getCode())
                .append(", \"")
                .append(methodName)
                .append("\"")
                .append(argsString)
                .append(")")
                .append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)));
        }

        if(methodName.equals("println")){
            System.out.println("papi " + node.getJmmParent().getKind());
        }

        if(node.getJmmParent().getKind().equals(METHOD_DECL.toString()) || node.getJmmParent().getKind().equals(IF_STMT.toString()) || node.getJmmParent().getKind().equals(WHILE_STMT.toString())
                || node.getJmmParent().getKind().equals(MAIN_METHOD_DECL.toString()) || node.getJmmParent().getKind().equals(EXPR_STMT.toString()) || node.getJmmParent().getKind().equals(SCOPE_STMT.toString())){
            code.append(invoke).append(END_STMT);
        }
        else{
            var temp = OptUtils.getTemp() + OptUtils.toOllirType(TypeUtils.getExprType(node, table));
            computation.append(temp).append(SPACE).append(ASSIGN).append(OptUtils.toOllirType(TypeUtils.getExprType(node, table))).append(SPACE).append(invoke).append(END_STMT);
            code.append(temp);
        }


        return new OllirExprResult(code.toString(), computation);
    }

    public boolean isStatic(JmmNode node){
        var curr = node;
        while(!(curr.getKind().equals(VAR_REF_EXPR.toString())) && !(curr.getKind().equals(THIS_EXPR.toString()))){
            curr = curr.getJmmChild(0);
        }
        return isClass(curr, table);
    }

    private OllirExprResult visitNewObjectExpr(JmmNode node, Void unused) {
        var name = node.get("name");
        var code = new StringBuilder();
        var computation = new StringBuilder();

        var temp = OptUtils.getTemp() + OptUtils.toOllirType(new Type(name, false));
        computation.append(temp).
                append(SPACE).
                append(ASSIGN).
                append(OptUtils.toOllirType(new Type(name, false))).
                append(SPACE).
                append("new").
                append("(").
                append(name).
                append(")").
                append(OptUtils.toOllirType(new Type(name, false))).
                append(END_STMT);

        computation.append("invokespecial(" + temp + ", \"<init>\").V;\n");

        code.append(temp);

        return new OllirExprResult(code.toString(), computation);
    }

    private OllirExprResult visitNewIntExpr(JmmNode node, Void unused) {
        var size = node.getJmmChild(0);
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var sizeResult = visit(size);

        computation.append(sizeResult.getComputation());
        code.append("new(array, ").append(sizeResult.getCode()).append(")").append(OptUtils.toOllirType(TypeUtils.getExprType(node, table)));

        return new OllirExprResult(code.toString(), computation);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
