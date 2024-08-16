package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.symboltable.JmmSymbolTable;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOLEAN_TYPE_NAME = "boolean";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }
    public static String getBooleanTypeName() {return BOOLEAN_TYPE_NAME;}
    public static Type getIntType() {
        return new Type(INT_TYPE_NAME, false);
    }

    public static Type getBoolType() {
        return new Type(BOOLEAN_TYPE_NAME, false);
    }

    public static Type getIntArrayType() {
        return new Type(INT_TYPE_NAME, true);
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {

        var kind = Kind.fromString(expr.getKind());

        return switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr, table);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL, LENGTH_CALL_EXPR -> getIntType();
            case BOOLEAN_LITERAL -> getBoolType();
            case METHOD_CALL_ON_OBJECT_EXPR -> getMethodCallOnObjectExprType(expr, table);
            case NEW_INT_EXPR -> getIntArrayType();
            case ARRAY_VALUES_EXPR -> getArrayValuesExprType(expr, table);
            case NEW_OBJECT_EXPR -> new Type(expr.get("name"), false);
            case THIS_EXPR -> new Type(table.getClassName(), false);
            case PAREN_EXPR -> getExprType(expr.getChildren().get(0), table);
            case ARRAY_ACCESS_EXPR -> getArrayAcccessExprType(expr, table);

            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };
    }

    private static Type getArrayAcccessExprType(JmmNode arrayAccessExpr, SymbolTable table) {
        var array = arrayAccessExpr.getChildren().get(0);
        var index = arrayAccessExpr.getChildren().get(1);

        Type arrayType = getExprType(array, table);
        Type indexType = getExprType(index, table);

        if (!arrayType.isArray()) {
            throw new RuntimeException("Array access on non-array type");
        }

        if (!indexType.getName().equals(INT_TYPE_NAME)) {
            throw new RuntimeException("Array access with non-integer index");
        }

        return new Type(arrayType.getName(), false);
    }

    private static Type getArrayValuesExprType(JmmNode arrayValuesExpr, SymbolTable table) {
        var values = arrayValuesExpr.getChildren();
        if (values.isEmpty()) {
            return getIntArrayType();
        }

        Type type = getExprType(values.get(0), table);
        for (int i = 1; i < values.size(); i++) {
            Type currentType = getExprType(values.get(i), table);
            if (!areTypesAssignable(currentType, type, table)) {
                throw new RuntimeException("Array values have different types");
            }
        }

        return new Type(type.getName(), true);
    }

    private static Type getMethodCallOnObjectExprType(JmmNode methodCallOnObjectExpr, SymbolTable table) {
        String methodName = methodCallOnObjectExpr.get("method");
        var obj = methodCallOnObjectExpr.getChildren().get(0);

        Type objType = getExprType(obj, table);

        if(objType.getName().equals(table.getClassName())){
            for (String method : table.getMethods()) {
                if (method.equals(methodName))
                    return table.getReturnType(method);
            }
            if(!table.getSuper().isEmpty()){
                return new Type("imported", false);
            }
        } else if(objType.getName().equals("imported")){
            return new Type("imported", false);
        } else{
            var imports = ((JmmSymbolTable)table).getImportsList();
            for (var imps : imports) {
                var imp = imps.get(imps.size() - 1);
                if (imp.equals(objType.getName())) {
                    return new Type("imported", false);
                }
                if(obj.getChild(0).getKind().equals(Kind.VAR_REF_EXPR.toString())){
                    var varName = obj.getChild(0).get("name");
                    if(varName.equals(imp)){
                        return new Type("imported", false);
                    }
                }
            }
        }

        throw new RuntimeException("Method '" + methodName + "' not found in symbol table");
    }

    private static Type getBinExprType(JmmNode binaryExpr, SymbolTable table) {

        var operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> getIntType();
            case "!", "&&", "<" -> getBoolType();
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        String varName = varRefExpr.get("name");

        Type t = null;
        for (Symbol field : table.getFields()) {
            if (field.getName().equals(varName))
                t = field.getType();
        }

        JmmNode parent = varRefExpr.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }

        for (Symbol param : table.getParameters(parent.get("name"))) {
            if (param.getName().equals(varName)){
                t = param.getType();
                break;}
        }

        for (Symbol local : table.getLocalVariables(parent.get("name"))) {
            if (local.getName().equals(varName)){
                t = local.getType();
                break;}
        }

        if(t != null){
            return t;
        }
        var imps=table.getImports();
        for (String imp : imps) {
            var parts = imp.split("\\.");
            var impName = parts[parts.length-1];
            if (impName.equals(varName)) {
                return new Type("imported", false);
            }
        }

        throw new RuntimeException("Variable '" + varName + "' not found in symbol table.");
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType, SymbolTable table) {
        if(sourceType.getName().equals(destinationType.getName())){
            return (sourceType.isArray() == destinationType.isArray()) || (!sourceType.isArray() && destinationType.hasAttribute("varArg"));
        }

        if(table.getClassName().equals(sourceType.getName())){
            return destinationType.getName().equals(table.getSuper());
        }
        var imports = ((JmmSymbolTable)table).getImportsList();
        var sImp=false;
        var dImp=false;
        for (var imps : imports) {
            var imp=imps.get(imps.size()-1);
            if (imp.equals(sourceType.getName())) {
                sImp=true;
            }
            if (imp.equals(destinationType.getName())) {
                dImp=true;
            }
        }

        return (sourceType.getName().equals("imported")) || (sImp && dImp) || (sImp && destinationType.getName().equals("imported")) ;

    }

    public static Type getVarType(JmmNode varRefExpr, SymbolTable table){
        //get method name
        JmmNode parent = varRefExpr.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }
        String methodName = parent.get("name");
        for(var local:table.getLocalVariables(methodName)){
            if(local.getName().equals(varRefExpr.get("name"))){
                return local.getType();
            }
        }
        for(var param:table.getParameters(methodName)){
            if(param.getName().equals(varRefExpr.get("name"))){
                return param.getType();
            }
        }
        for(var field:table.getFields()){
            if(field.getName().equals(varRefExpr.get("name"))){
                return field.getType();
            }
        }
        for(var imp:table.getImports()){
            if(imp.equals(varRefExpr.get("name"))){
                return new Type("imported", false);
            }
        }
        return null;


    }

    public static Type getVarType(String varName, JmmNode node, SymbolTable table){
        //get method name
        JmmNode parent = node.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }
        String methodName = parent.get("name");
        for(var local:table.getLocalVariables(methodName)){
            if(local.getName().equals(varName)){
                return local.getType();
            }
        }
        for(var param:table.getParameters(methodName)){
            if(param.getName().equals(varName)){
                return param.getType();
            }
        }
        for(var field:table.getFields()){
            if(field.getName().equals(varName)){
                return field.getType();
            }
        }
        for(var imp:table.getImports()){
            if(imp.equals(varName)){
                return new Type("imported", false);
            }
        }
        return null;
    }

    public static boolean isField(String varName, JmmNode node, SymbolTable table){
        for(var field:table.getFields()){
            if(field.getName().equals(varName)){
                return !isLocal(varName, node, table) && !isParameter(varName, node, table);
            }
        }
        return false;
    }

    public static boolean isLocal(String varName, JmmNode node, SymbolTable table){
        //get method name
        JmmNode parent = node.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }
        String methodName = parent.get("name");
        for(var local:table.getLocalVariables(methodName)){
            if(local.getName().equals(varName)){
                return true;
            }
        }
        return false;
    }

    public static boolean isParameter(String varName, JmmNode node, SymbolTable table){
        //get method name
        JmmNode parent = node.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }
        String methodName = parent.get("name");
        for(var param:table.getParameters(methodName)){
            if(param.getName().equals(varName)){
                return !isLocal(varName, node, table);
            }
        }
        return false;
    }

    public static boolean inMain(JmmNode node, SymbolTable table){
        JmmNode parent = node.getJmmParent();
        while (!parent.getKind().equals(Kind.METHOD_DECL.toString()) && !parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString())) {
            parent = parent.getJmmParent();
        }
        return parent.getKind().equals(Kind.MAIN_METHOD_DECL.toString());
    }

    public static boolean isClass(JmmNode node, SymbolTable table){
        if(isLocal(node.get("name"), node,table) || isParameter(node.get("name"), node,table) || (isField(node.get("name"), node,table) && !inMain(node,table))){
            return false;
        }
        return true;

    }

    public static Type getMethodReturnType(String methodName, SymbolTable table){
        for (String method : table.getMethods()) {
            if (method.equals(methodName))
                return table.getReturnType(method);
        }
        return null;
    }
}
