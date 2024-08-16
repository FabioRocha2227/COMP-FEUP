package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmSymbolTable implements SymbolTable {

    private final String className;
    private final List<String> methods;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;
    private final List<List<String>> imports;
    private final String superName;
    private final List<Symbol> fields;

    public JmmSymbolTable(String className,
                          List<String> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals,
                          List<List<String>> imports,
                          String superName,
                          List<Symbol> fields) {
        this.className = className;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
        this.imports = imports;
        this.superName = superName;
        this.fields = fields;
    }

    @Override
    public List<String> getImports() {

        return imports.stream().map(list -> String.join(".", list)).toList();
    }

    public List<List<String>> getImportsList() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {

        return superName;
    }

    @Override
    public List<Symbol> getFields() {

        return fields;
    }

    @Override
    public List<String> getMethods() {
        return Collections.unmodifiableList(methods);
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return Collections.unmodifiableList(params.getOrDefault(methodSignature, Collections.emptyList()));
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return Collections.unmodifiableList(locals.getOrDefault(methodSignature, Collections.emptyList()));
    }

}
