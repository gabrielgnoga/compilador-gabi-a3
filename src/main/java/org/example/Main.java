package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    // Listener com indicador visual ^~~~ da linha e coluna exata do erro (Aula 24)
    public static class GabiErrorListener extends BaseErrorListener {
        private final String fase;
        private final List<String> listaErros;
        private final String[] linhasFonte;

        public GabiErrorListener(String fase, List<String> listaErros, String codigoFonte) {
            this.fase = fase;
            this.listaErros = listaErros;
            this.linhasFonte = codigoFonte.split("\r?\n");
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine, String msg, RecognitionException e) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("❌ Erro %s [Linha %d, Coluna %d]: %s", fase, line, charPositionInLine, msg));
            if (line >= 1 && line <= linhasFonte.length) {
                String textoLinha = linhasFonte[line - 1];
                sb.append("\n     | ").append(textoLinha);
                sb.append("\n     | ").append(" ".repeat(Math.max(0, charPositionInLine))).append("^~~~");
            }
            listaErros.add(sb.toString());
        }
    }

    public static void compilarEExecutar(String titulo, String codigoFonte, boolean salvarArquivos) {
        System.out.println("======================================================================");
        System.out.println("▶ " + titulo);
        System.out.println("======================================================================");
        System.out.println("--- [Código-Fonte] ---");
        System.out.println(codigoFonte.trim());

        List<String> errosLexicosSintaticos = new ArrayList<>();

        // 1. Fase Léxica (Scanner)
        CharStream input = CharStreams.fromString(codigoFonte);
        GabiLexer lexer = new GabiLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new GabiErrorListener("Léxico", errosLexicosSintaticos, codigoFonte));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        System.out.println("\n--- [1. Fase Léxica: Tokens Reconhecidos] ---");
        for (Token t : tokens.getTokens()) {
            if (t.getType() != Token.EOF) {
                String nomeToken = GabiLexer.VOCABULARY.getSymbolicName(t.getType());
                if (nomeToken == null) nomeToken = GabiLexer.VOCABULARY.getLiteralName(t.getType());
                System.out.printf("  Token(%-10s, %-10s, Linha %d:%d)%n",
                        nomeToken, "'" + t.getText() + "'", t.getLine(), t.getCharPositionInLine());
            }
        }

        // 2. Fase Sintática (Parser)
        GabiParser parser = new GabiParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new GabiErrorListener("Sintático", errosLexicosSintaticos, codigoFonte));

        ParseTree tree = parser.programa();

        System.out.println("\n--- [2. Fase Sintática: Árvore Sintática (AST)] ---");
        System.out.println("  " + tree.toStringTree(parser));

        if (!errosLexicosSintaticos.isEmpty()) {
            System.out.println("\n--- [5. Diagnóstico de Erros Léxicos/Sintáticos] ---");
            errosLexicosSintaticos.forEach(System.out::println);
            System.out.println();
            return;
        }

        // 3, 4 e 5. Semântica + Otimização (Constant Folding) + TAC + Geração C + Interpretação
        GabiCompilerVisitor visitor = new GabiCompilerVisitor(codigoFonte);
        visitor.visit(tree);

        if (!visitor.getErrosSemanticos().isEmpty()) {
            System.out.println("\n--- [3 & 5. Diagnóstico de Erros Semânticos] ---");
            visitor.getErrosSemanticos().forEach(System.out::println);
            System.out.println();
            return;
        }

        System.out.println("\n--- [3. Fase Semântica: Tabela de Símbolos Completa (Global + Locais)] ---");
        for (GabiCompilerVisitor.Simbolo s : visitor.getHistoricoSimbolos()) {
            String escopoStr = (s.escopoNivel == 0) ? "Global (0)" : "Bloco Local (" + s.escopoNivel + ")";
            System.out.printf("  Nome: %-10s | Tipo: %-6s | Escopo: %-15s | Valor Final: %-18s | Linha: %d%n",
                    s.nome, s.tipo, escopoStr, s.valor, s.linhaDecl);
        }

        if (!visitor.getLogOtimizacoes().isEmpty()) {
            System.out.println("\n--- [4A. Otimizador de Código (Constant Folding — Aula 25)] ---");
            visitor.getLogOtimizacoes().forEach(opt -> System.out.println("  " + opt));
        }

        System.out.println("\n--- [4B. Código Intermediário de Três Endereços (TAC — Aula 25)] ---");
        visitor.getCodigoTAC().forEach(System.out::println);

        System.out.println("\n--- [4C. Resultado da Interpretação Direta] ---");
        for (String out : visitor.getSaidasPrint()) {
            System.out.println("  > " + out);
        }

        System.out.println("\n--- [4D. Código-Alvo Otimizado em C] ---");
        System.out.println(visitor.getCodigoC());

        if (salvarArquivos) {
            try {
                Files.writeString(Path.of("saida.tac"), String.join("\n", visitor.getCodigoTAC()) + "\n");
                Files.writeString(Path.of("saida.c"), visitor.getCodigoC());
                System.out.println("💾 Artefatos gerados com sucesso na raiz: 'saida.tac' e 'saida.c'!");
            } catch (Exception ignored) {}
        }
        System.out.println();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String conteudo = Files.readString(Path.of(args[0]));
            compilarEExecutar("Arquivo: " + args[0], conteudo, true);
            return;
        }

        compilarEExecutar("TESTE 1: Pipeline Completo (Constant Folding 3+4*2=11, TAC, Escopo, If/Else, While e Do-While)", """
                // Constant Folding reduz 3 + 4 * 2 para 11 em tempo de compilação!
                int x = 3 + 4 * 2;
                print x;
                
                texto msg = "Resultado aprovado";
                if (x > 10 && x == 11) {
                    int interna = 100;
                    x = x + interna;
                    print msg;
                } else {
                    x = 0;
                }
                
                // Laço while tradicional
                int cont = 3;
                while (cont > 0) {
                    print cont;
                    cont = cont - 1;
                }
                
                // Laço do-while (Aula 24)
                do {
                    cont = cont + 1;
                } while (cont < 2);
                
                print x;
                """, true);

        compilarEExecutar("TESTE 2: Erro Léxico (Caractere inválido '@' com apontador visual ^~~~)", """
                int a = 10 @ 5;
                print a;
                """, false);

        compilarEExecutar("TESTE 3: Erro Sintático (Falta de ponto e vírgula com apontador visual ^~~~)", """
                int a = 10
                print a;
                """, false);

        compilarEExecutar("TESTE 4: Erro Semântico (Variável não declarada e vazamento de escopo)", """
                int a = 5;
                if (a > 0) {
                    int localBloco = 20;
                }
                a = y + 1;
                print localBloco;
                """, false);

        compilarEExecutar("TESTE 5: Erro Semântico (Incompatibilidade de tipos int + texto)", """
                int numero = 10;
                texto nome = "Gabi";
                int somaInvalida = numero + nome;
                """, false);
    }
}