package org.example;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

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

    public static void compilarPrograma(String titulo, String codigoFonte, boolean gerarArquivosFisicos) {
        System.out.println("======================================================================");
        System.out.println("▶ COMPILANDO: " + titulo);
        System.out.println("======================================================================");
        System.out.println("--- [Código-Fonte (.gabi)] ---");
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
            System.out.println("\n--- [5. Diagnóstico de Erros Léxicos/Sintáticos (Compilação Abortada)] ---");
            errosLexicosSintaticos.forEach(System.out::println);
            System.out.println();
            return;
        }

        // 3 e 4. Fase Semântica + Otimização (Constant Folding) + Geração de TAC e Código C
        GabiCompilerVisitor compiler = new GabiCompilerVisitor(codigoFonte);
        compiler.visit(tree);

        if (!compiler.getErrosSemanticos().isEmpty()) {
            System.out.println("\n--- [3 & 5. Diagnóstico de Erros Semânticos (Compilação Abortada)] ---");
            compiler.getErrosSemanticos().forEach(System.out::println);
            System.out.println();
            return;
        }

        System.out.println("\n--- [3. Fase Semântica: Tabela de Símbolos (Global + Escopos Locais)] ---");
        for (GabiCompilerVisitor.Simbolo s : compiler.getHistoricoSimbolos()) {
            String escopoStr = (s.escopoNivel == 0) ? "Global (0)" : "Bloco Local (" + s.escopoNivel + ")";
            System.out.printf("  Nome: %-10s | Tipo: %-6s | Escopo: %-15s | Expr Compilada: %-18s | Leituras: %d | Linha: %d%n",
                    s.nome, s.tipo, escopoStr, s.exprInicial, s.leituras, s.linhaDecl);
        }

        if (!compiler.getLogOtimizacoes().isEmpty()) {
            System.out.println("\n--- [4A. Otimizador em Tempo de Compilação (Constant Folding & Dead Code)] ---");
            compiler.getLogOtimizacoes().forEach(opt -> System.out.println("  " + opt));
        }

        System.out.println("\n--- [4B. Código Intermediário de Três Endereços (TAC)] ---");
        compiler.getCodigoTAC().forEach(System.out::println);

        System.out.println("\n--- [4C. Código-Objeto Alvo Gerado em C (saida.c)] ---");
        System.out.println(compiler.getCodigoC());

        if (gerarArquivosFisicos) {
            try {
                Files.writeString(Path.of("saida.tac"), String.join("\n", compiler.getCodigoTAC()) + "\n");
                Files.writeString(Path.of("saida.c"), compiler.getCodigoC());
                System.out.println("💾 Artefatos de compilação gerados: 'saida.tac' e 'saida.c'");

                compilarComGccSeDisponivel();
            } catch (Exception e) {
                System.out.println("Erro ao gravar artefatos: " + e.getMessage());
            }
        }
        System.out.println();
    }

    private static void compilarComGccSeDisponivel() {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String binName = isWindows ? "programa.exe" : "./programa";

        try {
            ProcessBuilder pbGcc = new ProcessBuilder("gcc", "saida.c", "-o", isWindows ? "programa.exe" : "programa");
            pbGcc.redirectErrorStream(true);
            Process procGcc = pbGcc.start();
            int exitGcc = procGcc.waitFor();

            if (exitGcc == 0) {
                System.out.println("⚙️ [Back-End Nativo] 'gcc saida.c -o " + binName + "' compilado com sucesso!");
                System.out.println("--- [Execução do Binário Nativo Compilado (" + binName + ")] ---");
                ProcessBuilder pbRun = new ProcessBuilder(binName);
                pbRun.redirectErrorStream(true);
                Process procRun = pbRun.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(procRun.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("  > " + line);
                    }
                }
                procRun.waitFor();
            }
        } catch (Exception ignored) {
            // Se o PC não tiver gcc no PATH, apenas mantém o saida.c pronto para compilar manualmente
            System.out.println("ℹ️ Para gerar e rodar o binário nativo manualmente com GCC: gcc saida.c -o programa && ./programa");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            String conteudo = Files.readString(Path.of(args[0]));
            compilarPrograma("Arquivo: " + args[0], conteudo, true);
            return;
        }

        compilarPrograma("TESTE 1: Programa Válido (Constant Folding 3+4*2=11, TAC, Escopo, If/Else, While e Do-While)", """
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

        compilarPrograma("TESTE 2: Erro Léxico (Caractere inválido '@' com apontador visual ^~~~)", """
                int a = 10 @ 5;
                print a;
                """, false);

        compilarPrograma("TESTE 3: Erro Sintático (Falta de ponto e vírgula com apontador visual ^~~~)", """
                int a = 10
                print a;
                """, false);

        compilarPrograma("TESTE 4: Erro Semântico (Variável não declarada e vazamento de escopo)", """
                int a = 5;
                if (a > 0) {
                    int localBloco = 20;
                }
                a = y + 1;
                print localBloco;
                """, false);

        compilarPrograma("TESTE 5: Erro Semântico (Incompatibilidade de tipos int + texto)", """
                int numero = 10;
                texto nome = "Gabi";
                int somaInvalida = numero + nome;
                """, false);
    }
}