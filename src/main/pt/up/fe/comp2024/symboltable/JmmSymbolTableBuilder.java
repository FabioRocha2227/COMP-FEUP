package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var classDecl = root.getChildren(CLASS_DECL).get(0);
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String className = classDecl.get("name");

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var imports = buildImports(root);
        String superName;
        try{
            superName = classDecl.get("superName");
        } catch (Exception e){
            superName = "";
        }

        var fields = buildFields(classDecl);

        return new JmmSymbolTable(className, methods, returnTypes, params, locals, imports, superName, fields);
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        return classDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(new Type(varDecl.getJmmChild(0).get("name"), (varDecl.getDescendants().get(0).get("isArray").equals("true") || varDecl.getChild(0).hasAttribute("varArg")) ), varDecl.get("name")))
                .toList();
    }

    private static List<List<String>> buildImports(JmmNode root) {
        return root.getChildren(IMP).stream()
                .map(importNode -> importNode.getObjectAsList("name", String.class)).toList();
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method ->{

                            if (method.getChild(0).hasAttribute("varArg")) {
                                Type type = new Type(method.getChild(0).get("name"), true);
                                type.putObject("varArg", true);
                                map.put(method.get("name"), type);
                            } else {
                                map.put(method.get("name"),
                                        new Type(method.getChild(0).get("name"), method.getDescendants().get(0).get("isArray").equals("true")));
                            }
                        });
        classDecl.getChildren(MAIN_METHOD_DECL).stream()
                .forEach(mainMethod -> map.put("main", new Type("void", false)));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"),
                        method.getChildren(PARAM).stream().map(param -> {
                            if (param.getChild(0).hasAttribute("varArg")) {
                                Type type = new Type(param.getChild(0).get("name"), true);
                                type.putObject("varArg", true);
                                return new Symbol(type, param.get("name"));
                            } else {
                                return new Symbol(
                                        new Type(param.getChild(0).get("name"), param.getDescendants().get(0).get("isArray").equals("true")), param.get("name"));
                            }
                        }).toList()));


        classDecl.getChildren(MAIN_METHOD_DECL).stream()
                .forEach(mainMethod -> map.put("main", Arrays.asList(new Symbol(new Type("String", true), mainMethod.get("args")))));

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        for (JmmNode method : classDecl.getChildren(METHOD_DECL)) {
            List<Symbol> localsList = getLocalsList(method);
            map.put(method.get("name"), localsList);
        }
        //get locals of method main
        if (!classDecl.getChildren(MAIN_METHOD_DECL).isEmpty()) {
            List<Symbol> localsList = getLocalsList(classDecl.getChildren(MAIN_METHOD_DECL).get(0));
            map.put("main", localsList);
        }

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {
        List<String> methods = classDecl.getChildren(METHOD_DECL).stream()
                    .map(method -> method.get("name"))
                    .collect(Collectors.toList());
        if (!classDecl.getChildren(MAIN_METHOD_DECL).isEmpty()) {
            methods.add("main");
        }

        return methods;
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(new Type(varDecl.getJmmChild(0).get("name"), (varDecl.getDescendants().get(0).get("isArray").equals("true") || varDecl.getChild(0).hasAttribute("varArg"))), varDecl.get("name")))
                .toList();
    }

}
