grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSPAREN : '[';
RSPAREN : ']';
MUL : '*' ;
DIV : '/' ;
ADD : '+' ;
SUB : '-' ;
COMMA : ',';
DOT : '.' ;
NOT : '!' ;
COMMENT : (('//' .*?[\r\n] )| ('/*' .*? '*/')) -> skip ;
VOID : 'void';
IMPORT: 'import';
EXTENDS: 'extends';
STATIC: 'static';
CLASS : 'class' ;
INT : 'int' ;
BOOLEAN: 'boolean';
PUBLIC : 'public' ;
RETURN : 'return' ;
NEW : 'new' ;
THIS : 'this' ;
VARARGS: '...';

IF : 'if';
ELSE : 'else';
WHILE : 'while';

INTEGER : '0' | [1-9][0-9]* ;
BOOL : ('true' | 'false') ;
ID: [a-zA-Z_$] [a-zA-Z_$0-9]* ;


WS : [ \t\n\r\f]+ -> skip ;

program
    : imp* classDecl EOF
    ;


classDecl
    : CLASS name=ID (EXTENDS superName=ID)?
        LCURLY
        varDecl*
        methodDecl*
        mainMethodDecl?
        methodDecl*
        RCURLY
    ;

varDecl
    : type name=ID SEMI
    ;

type locals[boolean isArray=false]
    : name=INT varArg=VARARGS
    | name=INT (arr=LSPAREN RSPAREN {$isArray=true;})?
    | name=BOOLEAN
    | name=ID
    ;

ret: RETURN expr SEMI;

mainMethodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})? (isStatic=STATIC) VOID name=ID LPAREN 'String' LSPAREN RSPAREN args=ID RPAREN LCURLY varDecl* stmt* RCURLY
    ;


methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})? type name=ID LPAREN (param (COMMA param)*)? RPAREN LCURLY varDecl* stmt* RCURLY
    ;

param: type name=ID;

stmt
    : name=ID LSPAREN expr RSPAREN EQUALS expr SEMI #AssignArrayStmt
    | name=ID EQUALS expr SEMI #AssignStmt //
    | LCURLY stmt* RCURLY #ScopeStmt
    | expr SEMI # ExprStmt
    | IF LPAREN expr RPAREN (stmt | LCURLY stmt+ RCURLY) ELSE (stmt | LCURLY stmt+ RCURLY) #IfStmt
    | WHILE LPAREN expr RPAREN (stmt | LCURLY stmt+ RCURLY) #WhileStmt
    | ret #ReturnStmt
    ;

expr: LPAREN expr RPAREN #ParenExpr
    | expr DOT method=ID LPAREN (expr (COMMA expr)*)? RPAREN #MethodCallOnObjectExpr
    | expr DOT length=ID #LengthCallExpr
    | expr LSPAREN expr RSPAREN #ArrayAccessExpr
    | LSPAREN (expr (COMMA expr)*)? RSPAREN #ArrayValuesExpr
    | NEW INT LSPAREN expr RSPAREN #NewIntExpr
    | NEW name=ID LPAREN RPAREN #NewObjectExpr
    | op=NOT expr #BinaryExpr
    | expr op=(MUL | DIV) expr #BinaryExpr
    | expr op=(ADD | SUB) expr #BinaryExpr
    | expr op='<' expr #BinaryExpr
    | expr op='&&' expr #BinaryExpr
    | THIS #ThisExpr
    | name=ID #VarRefExpr
    | value=BOOL #BooleanLiteral
    | value=INTEGER #IntegerLiteral
    ;


imp
    : IMPORT name+=ID ('.' name+=ID)* SEMI
    ;