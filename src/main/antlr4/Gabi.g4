grammar Gabi;

programa : instrucao+ EOF ;

instrucao
    : tipo ID ('=' expr)? ';'                           # DeclInstr
    | ID '=' expr ';'                                   # AtribInstr
    | 'print' expr ';'                                  # PrintInstr
    | 'read' ID ';'                                     # ReadInstr
    | 'if' '(' expr ')' instrucao ('else' instrucao)?   # IfInstr
    | 'while' '(' expr ')' instrucao                    # WhileInstr
    | 'do' instrucao 'while' '(' expr ')' ';'           # DoWhileInstr
    | '{' instrucao* '}'                                # BlocoInstr
    ;

tipo
    : 'int'                                             # TipoInt
    | 'texto'                                           # TipoTexto
    ;

expr
    : '(' expr ')'                                      # ParenExpr
    | op=('-'|'!') expr                                 # UnaryExpr
    | expr op=('*'|'/') expr                            # MulDivExpr
    | expr op=('+'|'-') expr                            # AddSubExpr
    | expr op=('<'|'>'|'<='|'>='|'=='|'!=') expr        # RelExpr
    | expr op=('&&'|'||') expr                          # LogicExpr
    | INT                                               # IntExpr
    | STRING                                            # StrExpr
    | ID                                                # VarExpr
    ;

ID      : [a-zA-Z_][a-zA-Z0-9_]* ;
INT     : [0-9]+ ;
STRING  : '"' (~["\r\n])* '"' ;
COMMENT : '//' ~[\r\n]* -> skip ;
WS      : [ \t\r\n]+ -> skip ;
