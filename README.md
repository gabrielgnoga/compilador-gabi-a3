# Compilador e Interpretador — Linguagem Gabi (Avaliação A3)

**Integrantes do Grupo:**
* **Gabriel Gomide Noga**
* **Rayan Serhan de Andrade**

---

## 1. Apresentação da Linguagem

A **Linguagem Gabi** é uma linguagem imperativa tipada voltada para cálculos algébricos, manipulação de strings (`texto`) e controle de fluxo estruturado (`if/else`, `while`, `do-while`) com escopos aninhados em blocos `{ ... }`.

O projeto implementa um pipeline completo de compilação (Front-End e Back-End) em **Java 21 LTS** com **ANTLR 4**, incluindo:
1. **Análise Léxica e Sintática (`Gabi.g4`):** Reconhecimento de tokens e geração de AST sem ambiguidade via algoritmo ALL(*).
2. **Análise Semântica (`GabiCompilerVisitor.java`):** Tabela de Símbolos com pilha de escopos (`Deque<Escopo>`), checagem de declaração prévia, bloqueio de redeclaração e verificação estrita de tipos (`int` vs `texto`).
3. **Otimização de Código:** *Constant Folding* (avaliação de expressões constantes em tempo de compilação) e *Dead Code Warning* (detecção de variáveis declaradas e nunca lidas).
4. **Geração de Código Intermediário (TAC) e Código C:** Emissão de código de três endereços (`saida.tac` com temporários `t1..tn` e rótulos `L1..Ln`) e tradução para C padrão (`saida.c`).
5. **Tratamento e Diagnóstico Visual de Erros (`GabiErrorListener`):** Relatório de erros léxicos, sintáticos e semânticos informando Linha, Coluna e apontador visual (`^~~~`).

---

## 2. Especificação Formal da Gramática (BNF / EBNF)

### 2.1. Estrutura do Programa e Instruções
```text
Programa   → ListaInstr EOF
ListaInstr → Instrucao ListaInstr | Instrucao
Instrucao  → Tipo id ;
           | Tipo id = Expr ;
           | id = Expr ;
           | print Expr ;
           | read id ;
           | if ( Expr ) Instrucao
           | if ( Expr ) Instrucao else Instrucao
           | while ( Expr ) Instrucao
           | do Instrucao while ( Expr ) ;
           | { ListaInstr }
           | { }
Tipo       → int | texto

Expr   → Expr && Rel | Expr || Rel | Rel
Rel    → Rel < Adit | Rel > Adit | Rel <= Adit | Rel >= Adit | Rel == Adit | Rel != Adit | Adit
Adit   → Adit + Termo | Adit - Termo | Termo
Termo  → Termo * Unario | Termo / Unario | Unario
Unario → - Unario | ! Unario | Fator
Fator  → ( Expr ) | num | str | id