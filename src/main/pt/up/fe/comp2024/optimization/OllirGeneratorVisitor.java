package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";
    private final String IMPORT = "import ";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    private int whileCounter = -1;

    protected int whileNumber() {
        whileCounter++;
        return whileCounter;
    }

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMP, this::visitImportDecl);
        addVisit(MAIN_METHOD_DECL, this::visitMainDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(ASSIGN_ARRAY_STMT, this::visitAssignArrayStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitAssignArrayStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        var variable = jmmNode.get("name");
        var index = exprVisitor.visit(jmmNode.getJmmChild(0));
        var value = exprVisitor.visit(jmmNode.getJmmChild(1));
        var type = TypeUtils.getVarType(variable, jmmNode, table);
        var typeStr = ".i32";

        code.append(index.getComputation());
        code.append(value.getComputation());
        code.append(variable)
                .append("[")
                .append(index.getCode())
                .append("]")
                .append(typeStr)
                .append(SPACE)
                .append(ASSIGN)
                .append(typeStr)
                .append(SPACE)
                .append(value.getCode())
                .append(END_STMT);

        return code.toString();
    }

    private String visitWhileStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();
        var condition = exprVisitor.visit(jmmNode.getJmmChild(0));
        var stmts = visit(jmmNode.getJmmChild(1));
        var whileN = whileNumber();
        code.append("whileCond").append(whileN).append(":\n");
        code.append(condition.getComputation());
        code.append("if (")
                .append(condition.getCode())
                .append(") goto whileBody")
                .append(whileN).append(END_STMT);
        code.append("goto endWhile").append(whileN).append(END_STMT);

        code.append("whileBody").append(whileN).append(":\n");
        for(var stmt : jmmNode.getJmmChild(1).getChildren()){
            code.append(visit(stmt));
        }
        code.append("goto whileCond").append(whileN).append(END_STMT);

        code.append("endWhile").append(whileN).append(":\n");
        return code.toString();
    }

    private String visitIfStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();

        var condition = exprVisitor.visit(jmmNode.getJmmChild(0));
        var ifN = exprVisitor.ifnumber();
        code.append(condition.getComputation());
        code.append("if (")
                .append(condition.getCode())
                .append(") goto if")
                .append(ifN).append(END_STMT);
        //insert ELSE code
        var elseStmts = jmmNode.getJmmChild(2);
        for (var stmt : elseStmts.getChildren()) {
            code.append(visit(stmt));
        }

        code.append("goto endif").append(ifN).append(END_STMT);
        code.append("if").append(ifN).append(":\n");
        //insert THEN code
        var thenStmts = jmmNode.getJmmChild(1);
        for (var stmt : thenStmts.getChildren()) {
            code.append(visit(stmt));
        }


        code.append("endif").append(ifN).append(":\n");


        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

            var rhs = exprVisitor.visit(node.getJmmChild(0));
            StringBuilder code = new StringBuilder();

            Type type = TypeUtils.getVarType(node.get("name"), node, table);
            String typeStr = OptUtils.toOllirType(type);

            var varName = node.get("name");

            var isField = TypeUtils.isField(varName, node, table);
            if (isField){
                code.append(rhs.getComputation());
                code.append("putfield(this, ")
                        .append(varName)
                        .append(typeStr)
                        .append(", ")
                        .append(rhs.getCode())
                        .append(")")
                        .append(".V")
                        .append(END_STMT);
                return code.toString();
            }

            code.append(rhs.getComputation());
            code.append(varName)
                    .append(typeStr)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeStr)
                    .append(SPACE)
                    .append(rhs.getCode())
                    .append(END_STMT);

            return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var ret = exprVisitor.visit(node.getJmmChild(0).getJmmChild(0));
        System.out.println("node: " + node.getJmmChild(0).getJmmChild(0));
        var parent = node.getParent();
        while (!parent.getKind().equals(METHOD_DECL.toString()) && !parent.getKind().equals(MAIN_METHOD_DECL.toString())){
            parent = parent.getParent();
        }
        String methodName = parent.get("name");
        Type retType = table.getReturnType(methodName);
        String typeStr = OptUtils.toOllirType(retType);
        code.append(ret.getComputation());
        code.append("ret")
                .append(typeStr)
                .append(SPACE)
                .append(ret.getCode())
                .append(END_STMT);

        System.out.println("Return: " + code.toString());
        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {

        var expr = exprVisitor.visit(node.getJmmChild(0));

        StringBuilder code = new StringBuilder();

        code.append(expr.getComputation());
        code.append(expr.getCode());


        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");
        code.append(id).append(typeCode);

        return code.toString();
    }

    private String visitMainDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        code.append("static ");

        // name
        var name = node.get("name");
        code.append(name);
        // args
        var args=node.get("args");
        code.append( "(" + args + ".array.String" + ").V");

        code.append(L_BRACKET);


        // rest of its children stmts
        for (int i = 0; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }

        code.append("ret.V").append(END_STMT);
        code.append(R_BRACKET);
        code.append(NL);


        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var name = node.get("name");
        code.append(name);

        // param
        code.append("(");
        var f=false;
        for(var child : node.getChildren(PARAM)){
            if(f){
                code.append(", ");
            }
            f=true;
            code.append(visit(child));
        }
        code.append(")");
        //var paramCode = visit(node.getJmmChild(1));
        //code.append("(" + paramCode + ")");

        // type
        var retType = TypeUtils.getMethodReturnType(name, table);
        String typeStr = OptUtils.toOllirType(retType);
        code.append(typeStr);
        code.append(L_BRACKET);


        // rest of its children stmts
        var afterParam = node.getChildren(PARAM).size() + 1;
        for (int i = afterParam; i < node.getNumChildren(); i++) {
            var child = node.getJmmChild(i);
            var childCode = visit(child);
            code.append(childCode);
        }


        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        if(node.hasAttribute("superName")){
            code.append(" extends ");
            code.append(node.get("superName"));
        }
        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren(VAR_DECL)) {
            code.append(".field public ");

            var type = child.getJmmChild(0);
            var id = child.get("name");

            code.append(id);
            code.append(OptUtils.toOllirType(type));
            code.append(END_STMT);

        }

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        for (var child : node.getChildren()) {
            code.append(visit(child));
        }

        System.out.println("Code" + code);
        return code.toString();
    }

    private String visitImportDecl(JmmNode node, Void unused) {
        StringBuilder importStmt = new StringBuilder();
        importStmt.append(IMPORT);


        for (var importID : table.getImports()) {
            String a = getLastSegment(importID);
            if (a.equals(node.get("ID"))) {
                importStmt.append(importID);
            }
        }



        importStmt.append(END_STMT);

        return importStmt.toString();
    }

    public String getLastSegment(String importString) {
        // Split the import string by '.'
        String[] segments = importString.split("\\.");

        // Return the last segment
        return segments[segments.length - 1];
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
