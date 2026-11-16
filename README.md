# Compilador — Linguagem Gabi (Avaliação A3)

**Disciplina:** Teoria da Computação e Compiladores — Prof. Marcelo Di Paoli 
**Integrantes do Grupo:**
* **Gabriel Gomide Noga**
* **Rayan Serhan Trindade**

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
```

---

## Testes Automatizados e Validação

O projeto possui uma bateria de nove exemplos para validar o comportamento do compilador Gabi, incluindo programas válidos e programas com erros intencionais.

A execução automatizada é realizada pelo arquivo `testes.ps1`, desenvolvido em PowerShell. O script executa cada exemplo, compara a saída do compilador com os resultados esperados e apresenta um resumo de aprovação e reprovação.

### Casos de Teste

| Arquivo | Objetivo | Resultado esperado |
|---|---|---|
| teste1.txt | Validar operações aritméticas, precedência, if/else e while | Aceitação e geração de `x = 11` no TAC |
| teste2.txt | Validar parênteses, tipos texto e escopos | Aceitação e geração de `base = 14` no TAC |
| teste3.txt | Validar erro léxico causado pelo caractere `@` | Diagnóstico léxico e compilação abortada |
| teste4.txt | Validar ausência de ponto e vírgula | Diagnóstico sintático e compilação abortada |
| teste5.txt | Validar incompatibilidade de tipos, variável não declarada e escopo | Três diagnósticos semânticos e compilação abortada |
| teste6.txt | Validar reconhecimento de read e print | Aceitação do programa |
| teste7.txt | Validar comentários e linhas em branco | Aceitação do programa |
| teste8.txt | Validar operações aritméticas e comparações | Aceitação do programa |
| teste9.txt | Validar espaços e indentação | Aceitação do programa |

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

### Contribuição — Rayan Serhan Trindade

- Criação dos exemplos `teste6.txt` a `teste9.txt`.
- Execução e validação dos nove exemplos do compilador.
- Desenvolvimento do script `testes.ps1`.
- Implementação das verificações automáticas de aceitação e rejeição.
- Validação de precedência de operadores e uso de parênteses.
- Verificação dos diagnósticos léxicos, sintáticos e semânticos.
- Teste de falha controlada do verificador automatizado.
- Documentação dos procedimentos de teste.
