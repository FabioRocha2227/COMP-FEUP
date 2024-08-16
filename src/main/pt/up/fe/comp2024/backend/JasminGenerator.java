package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private final FunctionClassMap<TreeNode, String> generators;
    private ClassUnit classUnit;
    private int stack_counter;
    private int max_counter;
    private int limit_locals;
    private int jump;

    private int num_parameters;


    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

     


        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(GotoInstruction.class, this::generateGoTo);
        generators.put(CondBranchInstruction.class, this::generateBranch);
        generators.put(BinaryOpInstruction.class, this::generateBinary);
        generators.put(UnaryOpInstruction.class, this::generateUnary);
        generators.put(SingleOpInstruction.class, this::generateNoper);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(ArrayOperand.class, this::generateArray);



    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL);

        boolean hasDefaultConstructor = false;
        for(var method : classUnit.getMethods()){
            if(method.isConstructMethod() && method.getParams().isEmpty()){
                hasDefaultConstructor = true;
                System.out.println("Default constructor found in the class methods.");
                break;
            }
        }

        if(ollirResult.getOllirClass().getSuperClass() != null)
            code.append(".super ").append(ollirResult.getOllirClass().getSuperClass()).append(NL);
        else
            // default superclass (Object
            code.append(".super java/lang/Object").append(NL);

        // generate fields
        for (var field : ollirResult.getOllirClass().getFields()) {
            code.append(".field ");
            if (field.getFieldAccessModifier() != AccessModifier.DEFAULT) {
                code.append(field.getFieldAccessModifier().name().toLowerCase()).append(" ");
            }
            code.append(field.getFieldName()).append(" ");
            code.append(this.convertType(field.getFieldType())).append(NL);
        }

        // generate a single constructor method
        if(hasDefaultConstructor) {

            String classSuper = "java/lang/Object";
            if (classUnit.getSuperClass() != null) {
                classSuper = classUnit.getSuperClass();
            }

            var defaultConstructor = String.format("""
            ;default constructor
            .method public <init>()V
                aload_0
                invokespecial %s/<init>()V
                return
            .end method
            """, classSuper);
            code.append(defaultConstructor);
            System.out.println("Default constructor added.");

        }
        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        if(methodName.equals("main")) {
            modifier = "public static ";
        }

        //Generate method descriptor dynamically
        code.append("\n.method ").append(modifier).append(methodName).append("(");
        //params
        for(Element element : method.getParams()) {
            if (methodName.equals("main") && element.getType().getTypeOfElement() == ElementType.ARRAYREF) {
                code.append("[Ljava/lang/String;");
            } else {
                code.append(convertType(element.getType()));
            }
        }

        //return type
        code.append(")").append(this.convertType(method.getReturnType())).append(NL);
        
        
        
        // Add limits
        this.stack_counter = 0;
        this.max_counter = 0;
        this.limit_locals = getLimitLocals(method);

        jump = 0;
        String instructions = generateInstructions(method);
//        stack_counter=99;
//        limit_locals=99;
        if (this.stack_counter < 0) {
            this.stack_counter = 99;
        }
        code.append("\t.limit stack " + this.stack_counter + "\n");
        code.append("\t.limit locals " + this.limit_locals + "\n");

        code.append(instructions);

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }
    private String generateInstructions(Method method) {
        StringBuilder instructions = new StringBuilder();

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            for (Map.Entry<String, Instruction> entry : method.getLabels().entrySet()) {
                if (entry.getValue().equals(inst)) {
                    instructions.append(TAB).append(entry.getKey()).append(":").append(NL);
                }
            }

            instructions.append(instCode);
            if (inst instanceof CallInstruction && ((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID) {
                instructions.append("pop").append(NL);
                this.decrementStackCounter(1);
            }
        }

        return instructions.toString();
    }
    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // store value in the stack in destination
        var lhs = assign.getDest();
        //System.out.println("generateAssign " + lhs.getClass());

//        if (!(lhs instanceof Operand)) {
//            throw new NotImplementedException(lhs.getClass());
//        }

        var operand = (Operand) lhs;

        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        if(operand instanceof ArrayOperand){
            ArrayOperand aoperand = (ArrayOperand) operand;
            //Load aray
            String index = aoperand.getName().equals("this") ? "_0" : (reg < 4 ? "_" : " ") + reg;
            code.append("aload").append(index).append(NL);
            this.updateStackCounter(1);
            // load array index
            code.append(generators.apply(aoperand.getIndexOperands().get(0)));
            code.append(generators.apply(assign.getRhs()));
            code.append(storeVar(operand)).append(NL);
            return code.toString();
        }
        if(assign.getRhs() instanceof BinaryOpInstruction){
            String statement  = generateInc(assign);
            if(statement != null){
                code.append(statement);
                return code.toString();
            }
        }
        code.append(generators.apply(assign.getRhs()));
        //code.append(storeVar(operand)).append(NL);
        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN:
                if (operand.getType().getTypeOfElement() == ElementType.ARRAYREF) {
                    this.decrementStackCounter(3);
                    if (reg >= 0 && reg <= 3) {
                        code.append("astore_").append(reg).append(NL);
                    } else {
                        code.append("astore ").append(reg).append(NL);
                    }
                } else {
                    this.decrementStackCounter(1);
                    if (reg >= 0 && reg <= 3) {
                        code.append("istore_").append(reg).append(NL);
                    } else {
                        code.append("istore ").append(reg).append(NL);
                    }
                }
                break;
            case ARRAYREF, OBJECTREF, STRING, THIS:
                this.decrementStackCounter(1);
                if (reg >= 0 && reg <= 3) {
                    code.append("astore_").append(reg).append(NL);
                } else {
                    code.append("astore ").append(reg).append(NL);
                }
                break;
            default:
                throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }
    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }
    private String generateLiteral(LiteralElement element) {
            String literal = element.getLiteral();
            ElementType elementType = element.getType().getTypeOfElement();

            if (elementType != ElementType.INT32 && elementType != ElementType.BOOLEAN) {
                return "";
            }else {
                int value = Integer.parseInt(literal);
                if (value >= -1 && value <= 5) {
                    this.updateStackCounter(1);
                    return "iconst_" + value + NL;
                } else if (value >= -128 && value <= 127) {
                    this.updateStackCounter(1);
                    return "bipush " + value + NL;
                } else if (value >= -32768 && value <= 32767) {
                    this.updateStackCounter(1);
                    return "sipush " + value + NL;
                } else {
                    this.updateStackCounter(1);
                    return "ldc " + value + NL;
                }
            }
    }

    private String generateOperand(Operand operand) {
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN:
                if (reg >= 0 && reg <= 3) {
                    code.append("iload_").append(reg).append(NL);
                } else {
                    code.append("iload ").append(reg).append(NL);
                }
                break;
            case ARRAYREF, OBJECTREF, STRING, THIS:
                if (reg >= 0 && reg <= 3) {
                    code.append("aload_").append(reg).append(NL);
                } else {
                    code.append("aload ").append(reg).append(NL);
                }
                break;
            default:
                throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
            case DIV -> "idiv";
            case ANDB -> "iand";
            case NOTB -> "ifeq";
            case LTH -> "if_icmplt";
            case GTE -> "if_icmpte";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op);
        this.decrementStackCounter(1);
        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if(!returnInst.hasReturnValue()) return "return";

        code.append(generators.apply(returnInst.getOperand()));
        switch (returnInst.getOperand().getType().getTypeOfElement()) {
            case VOID:
                code.append("return");
                break;
            case INT32,BOOLEAN:
                code.append("ireturn");
                break;
            case ARRAYREF,OBJECTREF:
                code.append("areturn");
                break;
            default:
                throw new NotImplementedException(returnInst.getOperand().getType().getTypeOfElement());
        }
        return code.toString();
    }

    private StringBuilder convertType(Type type) {
        ElementType elementType = type.getTypeOfElement();
        var code = new StringBuilder();

        if(elementType == ElementType.ARRAYREF) return getArrayTypes(type);

        switch (elementType) {
            case INT32:
                return code.append("I");
            case BOOLEAN:
                return code.append("Z");
            case STRING:
                return code.append("Ljava/lang/String;");
            case CLASS:
                return code.append("CLASS");
            case VOID:
                return code.append("V");
            case OBJECTREF:
                String className = ((ClassType) type).getName();
                return code.append("L").append(className).append(";");
            default:
                return code.append("UNKNOWN");
        }
    }
    private StringBuilder getArrayTypes(Type type) {
        var code = new StringBuilder();
        return code.append("[").append(convertType(((ArrayType) type).getElementType()));
    }


    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();
        this.decrementStackCounter(2);
        Element first = putField.getOperands().get(0);
        Element second = putField.getOperands().get(1);
        Element third = putField.getOperands().get(2);

        code.append(generators.apply(first));
        code.append(generators.apply(third));

        // generate putfield instruction
        String className = null;
        if (first.getType().getTypeOfElement().equals(ElementType.THIS)) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (first.getType().getTypeOfElement().equals(ElementType.OBJECTREF)) {
            className = ((ClassType) first.getType()).getName();
        } else {
            className = this.getImportedClassName(first.getType().toString());
        }
        code.append("putfield ")
                .append(className)
                .append("/")
                .append(putField.getField().getName())
                .append(" ")
                .append(convertType(second.getType()))
                .append(NL);

        return code.toString();
    }
    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();

        Element first = getField.getOperands().get(0);

        code.append(generators.apply(first));
        String className = null;

        if (first.getType().getTypeOfElement().equals(ElementType.THIS)) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (first.getType().getTypeOfElement().equals(ElementType.OBJECTREF)) {
            className = ((ClassType) first.getType()).getName();
        } else {
            className = this.getImportedClassName(first.getType().toString());
        }

        code.append("getfield ")
                .append(className)
                .append("/")
                .append(getField.getField().getName())
                .append(" ")
                .append(convertType(getField.getField().getType()))
                .append(NL);

        return code.toString();
    }
    private String generateCall(CallInstruction call) {
        var code = new StringBuilder();
        switch (call.getInvocationType()) {
            case invokespecial -> {
                //this.decrementStackCounter(num_parameters);
                code.append(generateSpecialCall(call));
            }
            case invokestatic -> {
                //this.decrementStackCounter(num_parameters);
                code.append(generateStaticCall(call));
            }
            case invokevirtual -> {
                //this.decrementStackCounter(num_parameters);
                code.append(generateVirtualCall(call));
            }
            case NEW -> {
                //this.decrementStackCounter(num_parameters);
                code.append(generateObjectCall(call));
            }
            case arraylength -> {
                //this.decrementStackCounter(num_parameters);
                System.out.println("generateCall");
                code.append(generators.apply(call.getOperands().get(0)));
                code.append("arraylength").append(NL);
            }
            default -> {
                return "UNKNOWN";
            }
        }
        this.decrementStackCounter(num_parameters);
        return code.toString();
    }

    private String generateSpecialCall(CallInstruction call) {
        var code = new StringBuilder();
        num_parameters = 0;
        String className = null;
        Operand first = (Operand) call.getOperands().get(0);

        if (first.getType().getTypeOfElement().equals(ElementType.THIS)) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (first.getType().getTypeOfElement().equals(ElementType.OBJECTREF)) {
            className = ((ClassType) first.getType()).getName();
        } else {
            className = this.getImportedClassName(first.getType().toString());
        }

        // Get the method descriptor
        String methodDescriptor = "(";
        for (Element arg : call.getArguments()) {
            methodDescriptor += convertType(arg.getType());
        }
        methodDescriptor += ")" + convertType(call.getReturnType());

        // Append the invokespecial instruction to the code
        for (Element operand : call.getArguments()) {
            code.append(generators.apply(operand));
        }

        if(call.getReturnType().getTypeOfElement() != ElementType.VOID) this.updateStackCounter(1);

        String special = generators.apply(call.getOperands().get(0));
        code.append(special).append("invokespecial ").append(className).append("/").append("<init>").append(methodDescriptor).append(NL);
        num_parameters = call.getReturnType().getTypeOfElement() == ElementType.VOID ? num_parameters : num_parameters -1;
        this.decrementStackCounter(num_parameters);
        return code.toString();
    }

    private String generateStaticCall(CallInstruction call) {
        var code = new StringBuilder();
        num_parameters= 0;
        Operand first = (Operand) call.getOperands().get(0);
        String fullName = first.getName();
        String[] importedClassName = fullName.split("\\.");
        String lastPart = importedClassName[importedClassName.length - 1];

        LiteralElement second = (LiteralElement) call.getOperands().get(1);

        String string = "";
        for(int i = 1; i < call.getOperands().size(); i++){
            Element staticElement = call.getOperands().get(i);
            num_parameters+=1;
            string += this.generators.apply(staticElement);
        }

        String className = null;
        if (first.getType().getTypeOfElement().equals(ElementType.THIS)) {
            className = ollirResult.getOllirClass().getClassName();
        }
        else {
            List<String> imports = ollirResult.getOllirClass().getImports();
            for (String importClass : imports) {
                if (importClass.endsWith(lastPart)) {
                    className = importClass;
                    break;
                }
            }
        }
        if (className == null) {
            className = ollirResult.getOllirClass().getClassName();
        }
        className = className.replace('.', '/');

        // Get the method descriptor
        String methodDescriptor = "(";
        for (Element arg : call.getArguments()) {
            methodDescriptor += convertType(arg.getType());
        }

        if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
            this.updateStackCounter(1);
        }

        methodDescriptor += ")" + convertType(call.getReturnType());
        code.append(string).append("invokestatic ").append(className).append("/").append(second.getLiteral().replace("\"", "")).append(methodDescriptor).append(NL);

        num_parameters = call.getReturnType().getTypeOfElement() == ElementType.VOID ? num_parameters : num_parameters -1;
        this.decrementStackCounter(num_parameters);

        return code.toString();
    }
    private String generateVirtualCall(CallInstruction call) {
        var code = new StringBuilder();
        num_parameters= 1;
        Operand first = (Operand) call.getOperands().get(0);
        String fullName = first.getName();

        LiteralElement second = (LiteralElement) call.getOperands().get(1);
        String virtual = this.generators.apply(first);

        for(int i = 2; i < call.getOperands().size(); i++){
            Element staticElement = call.getOperands().get(i);
            num_parameters+=1;
            virtual += this.generators.apply(staticElement);
        }

        String className = null;

        if (first.getType().getTypeOfElement().equals(ElementType.THIS)) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (first.getType().getTypeOfElement().equals(ElementType.OBJECTREF)) {
            className = ((ClassType) first.getType()).getName();
        } else {
            className = this.getImportedClassName(first.getType().toString());
        }

        className = className.replace('.', '/');
        // Get the method descriptor
        String methodDescriptor = "(";
        for (Element arg : call.getArguments()) {
            methodDescriptor += convertType(arg.getType());
        }

        if (call.getReturnType().getTypeOfElement() != ElementType.VOID) {
            this.updateStackCounter(1);
        }
        methodDescriptor += ")" + convertType(call.getReturnType());

        code.append(virtual).append("invokevirtual ").append(className).append("/").append(second.getLiteral().replace("\"", "")).append(methodDescriptor).append(NL);

        num_parameters = call.getReturnType().getTypeOfElement() == ElementType.VOID ? num_parameters : num_parameters -1;
        this.decrementStackCounter(num_parameters);
        return code.toString();
    }
    private String generateObjectCall(CallInstruction call) {
        var code = new StringBuilder();
        num_parameters = -1;
        String className = null;
        Operand first = (Operand) call.getOperands().get(0);


        if (first.getType().getTypeOfElement().equals(ElementType.THIS)) {
            className = ollirResult.getOllirClass().getClassName();
        } else if (first.getType().getTypeOfElement().equals(ElementType.OBJECTREF)) {
            className = ((ClassType) first.getType()).getName();
        } else {
            className = this.getImportedClassName(first.getType().toString());
        }

        Element e = call.getOperands().get(0);

        for (Element arg : call.getOperands()) {
            num_parameters += 1;
        }


        if(e.getType().getTypeOfElement().equals(ElementType.ARRAYREF)) {
            code.append(generators.apply(call.getOperands().get(1)));
            code.append("newarray int").append(NL);
        } else {
            code.append("new ").append(className).append(NL).append("dup").append(NL);
        }
        decrementStackCounter(num_parameters);

        return code.toString();
    }
    private String getImportedClassName(String classname){
        String ImportedClassName = null;
        if (classname.equals("this")) {
            ImportedClassName = ollirResult.getOllirClass().getClassName();
            return ImportedClassName;
        }
        else {
            List<String> imports = ollirResult.getOllirClass().getImports();
            for (String importClass : imports) {
                if (importClass.endsWith(classname)) {
                    ImportedClassName = importClass;
                    return ImportedClassName;
                }
            }
        }
        return classname;
    }
    private int getLimitLocals(Method method) {
        Set<Integer> virtualRegs = new TreeSet<>();
        virtualRegs.add(0); // init, base case

        for (Descriptor variable : method.getVarTable().values()) {
            virtualRegs.add(variable.getVirtualReg());
        }
        return virtualRegs.size();
    }
    private void updateStackCounter(int value) {
        stack_counter += value;
        if (stack_counter > max_counter) max_counter = stack_counter;
//        System.out.println("num_parameters"+num_parameters);
//        System.out.println("updateStackCounter " + value);
//        System.out.println("Stack " + stack_counter );
    }

    private void decrementStackCounter(int value) {
        stack_counter -= value;
//        System.out.println("num_parameters"+num_parameters);
//        System.out.println("decrementStackCounter " + value);
//        System.out.println("stack_counter " + stack_counter);
    }
    //NOTA -> Problema no Limits stack simple muito provavelmente no invokevirtual

    private String generateArray(ArrayOperand arrayOperand) {
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(arrayOperand.getName()).getVirtualReg();
        this.updateStackCounter(1);
        //get register
        //System.out.println("generateArray " + arrayOperand.getName());
        code.append(TAB).append("aload").append(reg).append(NL);

        code.append(generators.apply(arrayOperand.getIndexOperands().get(0))).append(NL);
        code.append(TAB).append("iaload").append(NL);//load array[index]
        this.decrementStackCounter(1);
        return code.toString();
    }

    private String storeVar(Operand operand) {
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        switch (operand.getType().getTypeOfElement()) {
            case INT32, BOOLEAN:
                if (reg >= 0 && reg <= 3) {
                    code.append("istore_").append(reg).append(NL);
                } else {
                    code.append("istore ").append(reg).append(NL);
                }
                break;
            case ARRAYREF, OBJECTREF, STRING, THIS:
                if (reg >= 0 && reg <= 3) {
                    code.append("astore_").append(reg).append(NL);
                } else {
                    code.append("astore ").append(reg).append(NL);
                }
                break;
            default:
                throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
        return code.toString();
    }

    private String generateGoTo(GotoInstruction goTo) {
        var code = new StringBuilder();
        code.append(TAB).append("goto ").append(goTo.getLabel()).append(NL);
        return code.toString();
    }

    private String generateBranch(CondBranchInstruction branch) {
        var code = new StringBuilder();
        Instruction condition = branch.getCondition();
        String operation = null;
        System.out.println("generateBranch " + branch.getLabel());
        switch (branch.getInstType()){
            case UNARYOPER -> {
                UnaryOpInstruction unaryInstruction = (UnaryOpInstruction) condition;
                if (unaryInstruction.getOperation().getOpType() == OperationType.NOTB) { // Not Binary
                    operation = "ifeq";
                    code.append(generators.apply(unaryInstruction.getOperand()));

                }
            }
            case BINARYOPER -> {
                //String[] result = generateBinaryBranch((BinaryOpInstruction) condition);
                //operation = result[0];
                //code.append(result[1]);
                BinaryOpInstruction binaryOp = (BinaryOpInstruction) condition;
                switch (binaryOp.getOperation().getOpType()) {
                    case LTH -> {
                        Element left = binaryOp.getLeftOperand();
                        Element right = binaryOp.getRightOperand();
                        Integer literal = null;
                        operation = "if_icmplt";
                        Element temp = null;

                        if(left instanceof LiteralElement && ((LiteralElement) left).getLiteral().equals("0")){
                            literal = Integer.parseInt(((LiteralElement) left).getLiteral());
                            temp = right;
                            operation = "ifgt";
                        }
                        else if (right instanceof LiteralElement&& ((LiteralElement) right).getLiteral().equals("0") ) {
                            literal = Integer.parseInt(((LiteralElement) right).getLiteral());
                            temp = left;
                            operation = "iflt";
                        }
                        if (literal != null && literal == 0) {
                            code.append(generators.apply(temp));
                        } else {
                            code.append(generators.apply(left));
                            code.append(generators.apply(right));
                            operation = "if_icmplt";
                        }


                    }
                    case GTE -> {
                        Element left = binaryOp.getLeftOperand();
                        Element right = binaryOp.getRightOperand();
                        Integer literal = null;
                        operation = "if_icmpge";
                        Element temp = null;

                        if(left instanceof LiteralElement && ((LiteralElement) left).getLiteral().equals("0")){
                            literal = Integer.parseInt(((LiteralElement) left).getLiteral());
                            temp = right;
                            operation = "ifle";
                        }
                        else if (right instanceof LiteralElement && ((LiteralElement) right).getLiteral().equals("0")) {
                            literal = Integer.parseInt(((LiteralElement) right).getLiteral());
                            temp = left;
                            operation = "ifge";
                        }
                        if (literal != null && literal == 0) {
                            code.append(generators.apply(temp));
                        } else {
                            code.append(generators.apply(left));
                            code.append(generators.apply(right));
                            operation = "if_icmpge";
                        }
                    }
                    case ANDB -> {
                        System.out.println("ANDB");
                        operation = "ifne";
                        code.append(generators.apply(binaryOp));
                        break;
                    }
                    default -> {
                        code.append("BROKEN");
                    }
                }

            }
            default -> {
                operation = "ifne";
                code.append(generators.apply(branch.getCondition()));
            }
        }

        System.out.println("generateBranch " + branch.getLabel());
        code.append(TAB).append(operation).append(" ").append(branch.getLabel()).append(NL);
        return code.toString();
    }


    private String generateBinary(BinaryOpInstruction binInstruction) {
        var code = new StringBuilder();
        Element left = binInstruction.getLeftOperand();
        Element right = binInstruction.getRightOperand();

        code.append(generators.apply(left)).append(generators.apply(right)).append(TAB).append(generateBinaryOp(binInstruction));
        //code.append(generators.apply(left)).append(generators.apply(right)).append(TAB).append(getOp(binInstruction.getOperation()));

        if (binInstruction.getOperation().getOpType() == OperationType.EQ ||
                binInstruction.getOperation().getOpType() == OperationType.NEQ ||
                binInstruction.getOperation().getOpType() == OperationType.GTH ||
                binInstruction.getOperation().getOpType() == OperationType.GTE ||
                binInstruction.getOperation().getOpType() == OperationType.LTE ||
                binInstruction.getOperation().getOpType() == OperationType.LTH)
        {
            code.append(getBoolJumps());
        } else {
            code.append(NL);
        }
        this.decrementStackCounter(1);
        return code.toString();
    }

    private String generateUnary(UnaryOpInstruction unaryInstruction) {
        var code = new StringBuilder();
        code.append(generators.apply(unaryInstruction.getOperand())).append(TAB).append("ifeq");
        //code.append(generators.apply(unaryInstruction.getOperand())).append(TAB).append(getOp(unaryInstruction.getOperation()));

        if (unaryInstruction.getOperation().getOpType() == OperationType.NOTB) {
            code.append(getBoolJumps()).append(NL);
        }
        return code.toString();
    }

    private String getBoolJumps() {
        StringBuilder code = new StringBuilder();
        code.append(" true").append(this.jump).append(NL)
                .append(TAB).append("iconst_0").append(NL)
                .append(TAB).append("goto jump").append(this.jump).append(NL)
                .append("true").append(this.jump).append(":").append(NL)
                .append(TAB).append("iconst_1").append(NL)
                .append("jump").append(this.jump).append(":").append(NL);
        jump++;
        return code.toString();
    }
    private String generateNoper(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateInc(AssignInstruction assign){
        StringBuilder code = new StringBuilder();
        BinaryOpInstruction binaryOp = (BinaryOpInstruction) assign.getRhs();
        OperationType operation = binaryOp.getOperation().getOpType();

        code.append(getOp(binaryOp.getOperation())).append(NL);

        if (operation == OperationType.SUB || operation == OperationType.ADD) {

            Element leftOperand = binaryOp.getLeftOperand();
            Element rightOperand = binaryOp.getRightOperand();

            // verify if one of the operands is a literal
            if ((leftOperand.isLiteral() && !rightOperand.isLiteral()) || (!leftOperand.isLiteral() && rightOperand.isLiteral())) {

                Operand operand;
                LiteralElement literal;

                if (leftOperand.isLiteral()) {
                    literal = (LiteralElement) leftOperand;
                    operand = (Operand) binaryOp.getRightOperand();
                } else {
                    literal = (LiteralElement) binaryOp.getRightOperand();
                    operand = (Operand) binaryOp.getLeftOperand();
                }

                // verify if the variable is the same as the destination
                int value = Integer.parseInt(literal.getLiteral());
                if (operation == OperationType.SUB) value = -value;

                if (operand.getName().equals(((Operand) assign.getDest()).getName()) && value >= -128 && value <= 127) {
                    var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                    code.append("iinc ").append(reg).append(" ").append(value).append(NL);
                    return code.toString();
                }

            }
        }
        return null;
    }

    private String getOp(Operation op){
        switch(op.getOpType()){
            case ADD -> {
                return "iadd";
            }
            case MUL -> {
                return "imul";
            }
            case SUB -> {
                return "isub";
            }
            case DIV -> {
                return "idiv";
            }
            case ANDB -> {
                return "iand";
            }
            case NOTB -> {
                return "ifeq";
            }
            case LTH -> {
                return "if_icmplt";
            }
            case GTE -> {
                return "if_icmpte";
            }
            default -> {
                return "UNKNOWN";
            }
        }
    }

}





