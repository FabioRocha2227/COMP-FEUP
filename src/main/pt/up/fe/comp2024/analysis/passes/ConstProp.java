package pt.up.fe.comp2024.analysis.passes;

import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.misc.Triple;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.HashMap;
import java.util.List;

public class ConstProp extends AnalysisVisitor {
    boolean changed =false;
    HashMap<String, Pair<String, String>> fields =new HashMap<>();
    HashMap<String, Pair<String, String>> locals=new HashMap<>();
    HashMap<String, Triple<JmmNode, JmmNode, Integer>> removed=new HashMap<>();

    @Override
    protected void buildVisitor() {
        addVisit(Kind.MAIN_METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);

        setDefaultVisit(this::defaultVisit);
    }

    private Void defaultVisit(JmmNode node, SymbolTable table){
        return null;
    }

    private Void visitAssignStmt(JmmNode node, SymbolTable table) {
        JmmNode right = node.getChild(0);
        String leftName = node.get("name");

        boolean isLocal = TypeUtils.isLocal(leftName, node, table);
        if (inWhileOrIf(node)) {
            var t = removed.get(leftName);
            if (t != null)
                t.a.add(t.b, t.c);

            removeLocalOrField(table, right, leftName, isLocal);

            return null;
        }

        var rKind = right.getKind();

        if (rKind.equals(Kind.INTEGER_LITERAL.toString()) || rKind.equals(Kind.BOOLEAN_LITERAL.toString())) {
            if (isLocal) {
                locals.put(leftName, new Pair<>(rKind, right.get("value")));
            } else {
                fields.put(leftName, new Pair<>(rKind, right.get("value")));
            }

            JmmNode p = node.getParent();
            int index = p.removeChild(node);
            removed.put(leftName, new Triple<>(p, node, index));
        } else {
            removeLocalOrField(table, right, leftName, isLocal);
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode node, SymbolTable table) {
        if (inWhile(node)) return null;
        String varName = node.get("name");
        Pair<String, String> value = locals.get(varName);
        if(value == null) {
            value = fields.get(varName);
            if(value == null) return null;
        }
        JmmNodeImpl newNode = new JmmNodeImpl(value.a);
        newNode.put("value", value.b);
        node.replace(newNode);
        changed = true;
        return null;
    }

    private Void visitMethodDecl(JmmNode node, SymbolTable table) {
        locals.clear();
        return null;
    }

    private void removeLocalOrField(SymbolTable table, JmmNode right, String leftName, boolean isLocal) {
        if (isLocal) {
            if (locals.containsKey(leftName)) {
                findRef(right, leftName, table);
                locals.remove(leftName);
            }
        } else {
            if (fields.containsKey(leftName)) {
                findRef(right, leftName, table);
                fields.remove(leftName);
            }
        }
    }

    private boolean inWhileOrIf(JmmNode node) {
        JmmNode p = node.getParent();
        var isMethod = p.getKind().equals(Kind.METHOD_DECL.toString()) || p.getKind().equals(Kind.MAIN_METHOD_DECL.toString());
        while (!isMethod){
            if (p.getKind().equals(Kind.WHILE_STMT.toString()) || p.getKind().equals(Kind.IF_STMT.toString()))
                return true;
            p = p.getParent();
            isMethod = p.getKind().equals(Kind.METHOD_DECL.toString()) || p.getKind().equals(Kind.MAIN_METHOD_DECL.toString());
        }
        return false;
    }

    private boolean inWhile(JmmNode node) {
        JmmNode parent = node.getParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            if (parent.getKind().equals(Kind.WHILE_STMT.toString()))
                return true;
            parent = parent.getParent();
        }
        return false;
    }

    private void findRef(JmmNode node, String varName, SymbolTable table) {
        if (node.getKind().equals(Kind.VAR_REF_EXPR.toString()) && node.get("name").equals(varName)) {
            visitVarRefExpr(node, table);
        }
        var children = node.getChildren();
        for (var c : children) {
            findRef(c, varName, table);
        }
    }

    public boolean hasChanged() {
        return changed;
    }
}
