# Compilador — Linguagem Gabi (Avaliação A3)

**Disciplina:** Teoria da Computação e Compiladores — Prof. Marcelo Di Paoli  
**Integrantes do Grupo:**
- **Gabriel Gomide Noga**
- **Rayan Serhan Trindade**

---

## 1. Apresentação da Linguagem e Arquitetura

A **Linguagem Gabi** é uma linguagem imperativa estaticamente tipada voltada para cálculos algébricos, manipulação de strings (`texto`) e controle de fluxo estruturado (`if/else`, `while`, `do-while`) com escopos aninhados em blocos `{ ... }`.

O projeto implementa um pipeline completo de compilação (Front-End e Back-End) em **Java 21 LTS** com **ANTLR 4.13.2**, estruturado nas seguintes fases:
1. **Análise Léxica e Sintática (`Gabi.g4`):** Reconhecimento de tokens e geração da Árvore Sintática (AST) sem ambiguidade via algoritmo **ALL(*)**.
2. **Análise Semântica (`GabiCompilerVisitor.java`):** Tabela de Símbolos com pilha de escopos aninhados (`Deque<Escopo>`), checagem obrigatória de declaração prévia, bloqueio de redeclaração no mesmo escopo e verificação estrita de tipos (`int` vs `texto`).
3. **Otimização em Tempo de Compilação:** *Constant Folding* (avaliação e redução de expressões constantes em tempo de compilação, como `3 + 4 * 2 -> 11`) e *Dead Code Warning* (detecção de variáveis declaradas e nunca utilizadas).
4. **Geração de Código Intermediário (TAC) e Código C Nativo:** Emissão de código de três endereços (`saida.tac` com temporários `t1..tn` e rótulos `L1..Ln`), tradução para C padrão (`saida.c`) e invocação automática do `gcc` para gerar o binário executável.
5. **Tratamento e Diagnóstico Visual de Erros (`GabiErrorListener`):** Relatório detalhado de erros léxicos, sintáticos e semânticos informando Linha, Coluna e apontador visual (`^~~~`).

---

## 2. Especificação Formal da Gramática (BNF / EBNF)

A linguagem **Gabi** é formalmente definida pela Gramática Livre de Contexto $G = (V, T, P, S)$, onde:
- **$V$ (Não-Terminais):** `{ Programa, ListaInstr, Instrucao, Tipo, Expr, Rel, Adit, Termo, Unario, Fator }`
- **$T$ (Terminais):** `{ int, texto, print, read, if, else, while, do, id, num, str, =, ;, (, ), {, }, +, -, *, /, <, >, <=, >=, ==, !=, &&, ||, !, EOF }`
- **$S$ (Símbolo Inicial):** `Programa`

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
```

### 2.2. Hierarquia de Expressões (Precedência e Associatividade à Esquerda)
```text
Expr   → Expr && Rel | Expr || Rel | Rel
Rel    → Rel < Adit | Rel > Adit | Rel <= Adit | Rel >= Adit | Rel == Adit | Rel != Adit | Adit
Adit   → Adit + Termo | Adit - Termo | Termo
Termo  → Termo * Unario | Termo / Unario | Unario
Unario → - Unario | ! Unario | Fator
Fator  → ( Expr ) | num | str | id
```

### 2.3. Especificação Léxica (Expressões Regulares dos Tokens)
```text
id      → [a-zA-Z_][a-zA-Z0-9_]*
num     → [0-9]+
str     → '"' (~["\r\n])* '"'
COMMENT → '//' ~[\r\n]*          (ignorado/skip)
WS      → [ \t\r\n]+             (ignorado/skip)
```

---

## 3. Testes Automatizados e Validação

O projeto possui uma bateria de nove exemplos para validar o comportamento do compilador Gabi, incluindo programas válidos e programas com erros intencionais.

A execução automatizada é realizada pelo arquivo `testes.ps1`, desenvolvido em PowerShell. O script executa cada exemplo, compara a saída do compilador com os resultados esperados e apresenta um resumo de aprovação e reprovação.

### Casos de Teste

| Arquivo | Objetivo | Resultado esperado |
|---|---|---|
| `teste1.txt` | Aritmética, precedência, `if/else`, `while` | Aceitação e `x = 11` no TAC |
| `teste2.txt` | Parênteses, `texto` e escopos | Aceitação e `base = 14` no TAC |
| `teste3.txt` | Erro léxico (`@`) | Diagnóstico léxico e aborto |
| `teste4.txt` | Ausência de `;` | Diagnóstico sintático e aborto |
| `teste5.txt` | Tipos, escopo e não declarada | Três erros semânticos e aborto |
| `teste6.txt` | Comandos `read` e `print` | Aceitação do programa |
| `teste7.txt` | Comentários e linhas em branco | Aceitação do programa |
| `teste8.txt` | Aritmética e comparações | Aceitação do programa |
| `teste9.txt` | Espaços e indentação | Aceitação do programa |

### Requisitos para Execução

- Java 21
- Maven
- Windows com PowerShell para executar `testes.ps1`

### Como Executar

Na pasta raiz do projeto, compile o código com:

```powershell
mvn clean package
```

Para executar um exemplo individualmente:

```powershell
java -jar target/gabi-compiler.jar exemplos/teste1.txt
```

Substitua `teste1.txt` pelo nome do exemplo desejado.

Para executar a bateria automatizada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\testes.ps1
```

O script exibe o resultado de cada caso e um resumo final com a quantidade de testes aprovados e reprovados.

### Critérios de Aprovação

Para os programas válidos, o script verifica a conclusão da compilação e a ausência de diagnósticos de erro nas seções analisadas. Os testes 1 e 2 também conferem atribuições específicas no código intermediário TAC.

Para os programas inválidos, o script procura os diagnósticos esperados:

- Teste 3: erro léxico relacionado ao caractere `@`.
- Teste 4: erro sintático pela ausência de `;`.
- Teste 5: incompatibilidade de tipos, variável não declarada e variável fora do escopo.

Um teste inválido é considerado aprovado quando o compilador identifica corretamente o erro esperado.

O script retorna código de saída 1 caso algum teste seja reprovado, permitindo identificar falhas na validação automatizada.

### Verificação do Próprio Script

Foi realizado um teste de falha controlada, alterando temporariamente o resultado esperado de um programa válido para erro léxico.

O script identificou a divergência e reprovou o caso. Após restaurar a configuração original, a bateria voltou a apresentar nove testes aprovados.

### Limitações

Os testes validam o reconhecimento dos programas, diagnósticos e alguns resultados do TAC. Eles não representam cobertura exaustiva de todas as produções da gramática nem validam a execução completa dos programas C gerados.

---

## 4. Matriz de Responsabilidade e Contribuições Individuais

### Contribuição — Gabriel Gomide Noga

- Modelagem da Gramática Livre de Contexto (BNF/EBNF) e implementação da gramática unificada `Gabi.g4` no ANTLR 4.
- Implementação da Tabela de Símbolos com controle de escopos aninhados em pilha (`Deque<Escopo>`) em `GabiCompilerVisitor.java`.
- Desenvolvimento do analisador semântico (verificação estrita de tipos `int` e `texto`, declaração prévia obrigatória e bloqueio de redeclaração).
- Implementação do otimizador em tempo de compilação (*Constant Folding* para redução algébrica de constantes e avisos de *Dead Code*).
- Construção do gerador de Código Intermediário de Três Endereços (`saida.tac`) com alocação de temporários (`t1..tn`) e rótulos de desvio (`L1..Ln`).
- Implementação do transpilador/gerador de código-alvo em C (`saida.c`) e integração automática com o compilador nativo `gcc` no `Main.java`.
- Implementação do `GabiErrorListener` com diagnóstico visual (`^~~~`) de linha e coluna e criação dos casos de teste base (`teste1.txt` a `teste5.txt`).
- Configuração da automação de build via Maven (`pom.xml` com `antlr4-maven-plugin` e `maven-shade-plugin`) e estruturação do repositório Git.

### Contribuição — Rayan Serhan Trindade

- Criação dos exemplos `teste6.txt` a `teste9.txt`.
- Execução e validação dos nove exemplos do compilador.
- Desenvolvimento do script `testes.ps1`.
- Implementação das verificações automáticas de aceitação e rejeição.
- Validação de precedência de operadores e uso de parênteses.
- Verificação dos diagnósticos léxicos, sintáticos e semânticos.
- Teste de falha controlada do verificador automatizado.
- Documentação dos procedimentos de teste.