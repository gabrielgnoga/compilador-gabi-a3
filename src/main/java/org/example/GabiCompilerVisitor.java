package org.example;

import org.antlr.v4.runtime.Token;
import java.util.*;

public class GabiCompilerVisitor extends GabiBaseVisitor<GabiCompilerVisitor.Valor> {

    // Carrega o tipo, valor de execução, operando TAC/C e se é constante (Constant Folding)
    public static class Valor {
        public String tipo;       // "int", "texto" ou "erro"
        public Object val;        // Valor em tempo de interpretação/compilação
        public String repr;       // Representação no TAC / C (ex: "t1", "11", "x")
        public boolean isConst;   // true se puder ser otimizado por Constant Folding (Aula 25)

        public Valor(String tipo, Object val, String repr, boolean isConst) {
            this.tipo = tipo;
            this.val = val;
            this.repr = repr;
            this.isConst = isConst;
        }
    }

    public static class Simbolo {
        public String nome;
        public String tipo;
        public Object valor;
        public int linhaDecl;
        public int escopoNivel;

        public Simbolo(String nome, String tipo, Object valor, int linhaDecl, int escopoNivel) {
            this.nome = nome;
            this.tipo = tipo;
            this.valor = valor;
            this.linhaDecl = linhaDecl;
            this.escopoNivel = escopoNivel;
        }
    }

    // Classe de Escopo para evitar aninhamento de '< >' e não cortar ao copiar
    public static class Escopo extends LinkedHashMap<String, Simbolo> {}

    private final Deque<Escopo> pilhaEscopos = new ArrayDeque<>();
    private final List<Simbolo> historicoSimbolos = new ArrayList<>();
    private final List<String> errosSemanticos = new ArrayList<>();
    private final List<String> saidasPrint = new ArrayList<>();
    private final List<String> codigoTAC = new ArrayList<>();
    private final List<String> logOtimizacoes = new ArrayList<>();
    private final StringBuilder codigoC = new StringBuilder();
    private final String[] linhasFonte;

    private int contTemp = 0;
    private int contLabel = 0;
    private int nivelIndentacao = 1;
    private boolean emitindoCodigo = true;
    private boolean interpretando = true;

    public GabiCompilerVisitor(String codigoFonte) {
        this.linhasFonte = codigoFonte.split("\r?\n");
        pilhaEscopos.push(new Escopo());
        codigoC.append("#include <stdio.h>\n\nint main() {\n");
    }

    private String novoTemp() {
        return "t" + (++contTemp);
    }

    private String novoLabel() {
        return "L" + (++contLabel);
    }

    private void emitirTAC(String instrucao) {
        if (emitindoCodigo) {
            codigoTAC.add(instrucao);
        }
    }

    private void indentarC(String linha) {
        if (emitindoCodigo) {
            codigoC.append("    ".repeat(Math.max(0, nivelIndentacao))).append(linha).append("\n");
        }
    }

    // Formata erro com trecho do código e setinha ^ apontando a coluna exata (Aula 24 - Slide C7)
    private void registrarErro(Token token, String msg) {
        int linha = token.getLine();
        int col = token.getCharPositionInLine();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("❌ Erro Semântico [Linha %d, Coluna %d]: %s", linha, col, msg));
        if (linha >= 1 && linha <= linhasFonte.length) {
            String textoLinha = linhasFonte[linha - 1];
            sb.append("\n     | ").append(textoLinha);
            sb.append("\n     | ").append(" ".repeat(Math.max(0, col))).append("^~~~");
        }
        String erroFormatado = sb.toString();
        if (!errosSemanticos.contains(erroFormatado)) {
            errosSemanticos.add(erroFormatado);
        }
    }

    private Simbolo buscarSimbolo(String nome) {
        for (Escopo escopo : pilhaEscopos) {
            if (escopo.containsKey(nome)) {
                return escopo.get(nome);
            }
        }
        return null;
    }

    public List<String> getErrosSemanticos() { return errosSemanticos; }
    public List<String> getSaidasPrint() { return saidasPrint; }
    public List<String> getCodigoTAC() { return codigoTAC; }
    public List<String> getLogOtimizacoes() { return logOtimizacoes; }
    public List<Simbolo> getHistoricoSimbolos() { return historicoSimbolos; }
    public String getCodigoC() { return codigoC.toString() + "    return 0;\n}\n"; }

    // ==================== INSTRUÇÕES ====================

    @Override
    public Valor visitPrograma(GabiParser.ProgramaContext ctx) {
        for (GabiParser.InstrucaoContext instr : ctx.instrucao()) {
            visit(instr);
        }
        return null;
    }

    @Override
    public Valor visitDeclInstr(GabiParser.DeclInstrContext ctx) {
        String tipoDecl = ctx.tipo().getText();
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();

        Escopo escopoAtual = pilhaEscopos.peek();
        int nivelEscopo = pilhaEscopos.size() - 1; // 0 = Global, 1+ = Bloco Local

        if (escopoAtual.containsKey(nomeVar)) {
            Simbolo anterior = escopoAtual.get(nomeVar);
            registrarErro(idToken, "Variável '" + nomeVar + "' já foi declarada neste escopo (linha " + anterior.linhaDecl + ").");
            return new Valor("erro", null, "erro", false);
        }

        Object valorInicial = tipoDecl.equals("int") ? 0 : "";
        String reprFinal = tipoDecl.equals("int") ? "0" : "\"\"";

        if (ctx.expr() != null) {
            Valor valExpr = visit(ctx.expr());
            reprFinal = valExpr.repr;

            if (!valExpr.tipo.equals("erro") && !valExpr.tipo.equals(tipoDecl)) {
                registrarErro(idToken, "Tipos incompatíveis na declaração de '" + nomeVar + "': esperado '" + tipoDecl + "', mas recebeu '" + valExpr.tipo + "'.");
            } else if (interpretando) {
                valorInicial = valExpr.val;
            }
        }

        Simbolo novo = new Simbolo(nomeVar, tipoDecl, valorInicial, idToken.getLine(), nivelEscopo);
        escopoAtual.put(nomeVar, novo);
        if (emitindoCodigo) {
            historicoSimbolos.add(novo);
        }

        emitirTAC("  " + nomeVar + " = " + reprFinal);
        String tipoC = tipoDecl.equals("int") ? "int" : "const char*";
        indentarC(tipoC + " " + nomeVar + " = " + reprFinal + ";");

        return new Valor(tipoDecl, valorInicial, nomeVar, false);
    }

    @Override
    public Valor visitAtribInstr(GabiParser.AtribInstrContext ctx) {
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();

        Simbolo simbolo = buscarSimbolo(nomeVar);
        Valor valExpr = visit(ctx.expr());

        if (simbolo == null) {
            registrarErro(idToken, "Variável '" + nomeVar + "' não foi declarada antes do uso.");
            return new Valor("erro", null, "erro", false);
        }

        if (!valExpr.tipo.equals("erro") && !simbolo.tipo.equals(valExpr.tipo)) {
            registrarErro(idToken, "Tipos incompatíveis na atribuição para '" + nomeVar + "': variável é '" + simbolo.tipo + "', mas expressão é '" + valExpr.tipo + "'.");
            return new Valor("erro", null, "erro", false);
        }

        if (interpretando) {
            simbolo.valor = valExpr.val;
        }
        emitirTAC("  " + nomeVar + " = " + valExpr.repr);
        indentarC(nomeVar + " = " + valExpr.repr + ";");
        return valExpr;
    }

    @Override
    public Valor visitPrintInstr(GabiParser.PrintInstrContext ctx) {
        Valor val = visit(ctx.expr());
        if (interpretando && errosSemanticos.isEmpty() && !val.tipo.equals("erro")) {
            saidasPrint.add(String.valueOf(val.val));
        }
        emitirTAC("  print " + val.repr);
        String fmt = "texto".equals(val.tipo) ? "\"%s\\n\"" : "\"%d\\n\"";
        indentarC("printf(" + fmt + ", " + val.repr + ");");
        return val;
    }

    @Override
    public Valor visitReadInstr(GabiParser.ReadInstrContext ctx) {
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();
        Simbolo simbolo = buscarSimbolo(nomeVar);

        if (simbolo == null) {
            registrarErro(idToken, "Variável '" + nomeVar + "' não foi declarada antes do comando 'read'.");
            return new Valor("erro", null, "erro", false);
        }

        emitirTAC("  read " + nomeVar);
        if (simbolo.tipo.equals("int")) {
            indentarC("scanf(\"%d\", &" + nomeVar + ");");
        }
        return new Valor(simbolo.tipo, simbolo.valor, nomeVar, false);
    }

    @Override
    public Valor visitBlocoInstr(GabiParser.BlocoInstrContext ctx) {
        pilhaEscopos.push(new Escopo());
        for (GabiParser.InstrucaoContext instr : ctx.instrucao()) {
            visit(instr);
        }
        pilhaEscopos.pop();
        return null;
    }

    @Override
    public Valor visitIfInstr(GabiParser.IfInstrContext ctx) {
        Valor cond = visit(ctx.expr());
        if (!cond.tipo.equals("erro") && !cond.tipo.equals("int")) {
            registrarErro(ctx.getStart(), "A condição do 'if' deve ser inteira/lógica, mas recebeu '" + cond.tipo + "'.");
        }

        String lblElse = novoLabel();
        String lblFim = (ctx.instrucao(1) != null) ? novoLabel() : lblElse;

        emitirTAC("  ifFalse " + cond.repr + " goto " + lblElse);

        boolean condVerdadeira = cond.val instanceof Integer && ((Integer) cond.val) != 0;
        boolean interpAnterior = interpretando;

        indentarC("if (" + cond.repr + ") {");
        nivelIndentacao++;
        interpretando = interpAnterior && condVerdadeira && errosSemanticos.isEmpty();
        visit(ctx.instrucao(0));
        nivelIndentacao--;

        if (ctx.instrucao(1) != null) {
            emitirTAC("  goto " + lblFim);
            emitirTAC(lblElse + ":");
            indentarC("} else {");
            nivelIndentacao++;
            interpretando = interpAnterior && !condVerdadeira && errosSemanticos.isEmpty();
            visit(ctx.instrucao(1));
            nivelIndentacao--;
            emitirTAC(lblFim + ":");
        } else {
            emitirTAC(lblElse + ":");
        }

        interpretando = interpAnterior;
        indentarC("}");
        return null;
    }

    @Override
    public Valor visitWhileInstr(GabiParser.WhileInstrContext ctx) {
        String lblInicio = novoLabel();
        String lblFim = novoLabel();

        emitirTAC(lblInicio + ":");
        Valor cond = visit(ctx.expr());
        if (!cond.tipo.equals("erro") && !cond.tipo.equals("int")) {
            registrarErro(ctx.getStart(), "A condição do 'while' deve ser inteira/lógica, mas recebeu '" + cond.tipo + "'.");
            return null;
        }
        emitirTAC("  ifFalse " + cond.repr + " goto " + lblFim);

        indentarC("while (" + ctx.expr().getText() + ") {");
        nivelIndentacao++;

        boolean emitAnterior = emitindoCodigo;
        boolean interpAnterior = interpretando;

        if (interpretando && errosSemanticos.isEmpty()) {
            int maxIter = 1000;
            boolean primeiraIteracao = true;

            while (cond.val instanceof Integer && ((Integer) cond.val) != 0 && maxIter-- > 0) {
                emitindoCodigo = emitAnterior && primeiraIteracao;
                visit(ctx.instrucao());
                primeiraIteracao = false;
                emitindoCodigo = false;
                cond = visit(ctx.expr());
            }

            if (primeiraIteracao) {
                emitindoCodigo = emitAnterior;
                interpretando = false;
                visit(ctx.instrucao());
                interpretando = interpAnterior;
            }
            emitindoCodigo = emitAnterior;
        } else {
            interpretando = false;
            visit(ctx.instrucao());
            interpretando = interpAnterior;
        }

        emitirTAC("  goto " + lblInicio);
        emitirTAC(lblFim + ":");

        nivelIndentacao--;
        indentarC("}");
        return null;
    }

    @Override
    public Valor visitDoWhileInstr(GabiParser.DoWhileInstrContext ctx) {
        String lblInicio = novoLabel();
        emitirTAC(lblInicio + ":");

        indentarC("do {");
        nivelIndentacao++;

        boolean emitAnterior = emitindoCodigo;
        boolean interpAnterior = interpretando;
        Valor cond = new Valor("int", 0, "0", false);

        if (interpretando && errosSemanticos.isEmpty()) {
            int maxIter = 1000;
            boolean primeiraIteracao = true;
            do {
                emitindoCodigo = emitAnterior && primeiraIteracao;
                visit(ctx.instrucao());
                cond = visit(ctx.expr());
                if (!cond.tipo.equals("erro") && !cond.tipo.equals("int")) {
                    registrarErro(ctx.getStart(), "A condição do 'do-while' deve ser inteira/lógica.");
                    break;
                }
                primeiraIteracao = false;
            } while (cond.val instanceof Integer && ((Integer) cond.val) != 0 && maxIter-- > 0);
            emitindoCodigo = emitAnterior;
        } else {
            interpretando = false;
            visit(ctx.instrucao());
            cond = visit(ctx.expr());
            interpretando = interpAnterior;
        }

        nivelIndentacao--;
        indentarC("} while (" + ctx.expr().getText() + ");");
        emitirTAC("  ifTrue " + cond.repr + " goto " + lblInicio);
        return null;
    }

    // ==================== EXPRESSÕES + CONSTANT FOLDING + TAC ====================

    @Override
    public Valor visitParenExpr(GabiParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public Valor visitUnaryExpr(GabiParser.UnaryExprContext ctx) {
        Valor v = visit(ctx.expr());
        String op = ctx.op.getText();
        if (v.tipo.equals("erro")) return v;

        if (!v.tipo.equals("int")) {
            registrarErro(ctx.op, "Operador unário '" + op + "' exige tipo 'int'.");
            return new Valor("erro", null, "erro", false);
        }

        int valInt = (Integer) v.val;
        int res = op.equals("-") ? -valInt : (valInt == 0 ? 1 : 0);

        if (v.isConst) {
            return new Valor("int", res, String.valueOf(res), true);
        }
        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + op + v.repr);
        return new Valor("int", res, temp, false);
    }

    @Override
    public Valor visitMulDivExpr(GabiParser.MulDivExprContext ctx) {
        Valor esq = visit(ctx.expr(0));
        Valor dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Valor("erro", null, "erro", false);
        }

        if (!esq.tipo.equals("int") || !dir.tipo.equals("int")) {
            registrarErro(ctx.op, "Operador '" + op + "' exige operandos 'int', mas recebeu '" + esq.tipo + "' e '" + dir.tipo + "'.");
            return new Valor("erro", null, "erro", false);
        }

        int v1 = (Integer) esq.val;
        int v2 = (Integer) dir.val;

        if (op.equals("/") && v2 == 0) {
            if (interpretando) {
                registrarErro(ctx.op, "Divisão por zero detectada.");
                return new Valor("erro", null, "erro", false);
            }
            return new Valor("int", 0, "0", false);
        }

        int res = op.equals("*") ? (v1 * v2) : (v1 / v2);

        // Otimização de Constant Folding (Aula 25 - Slide 4)
        if (esq.isConst && dir.isConst) {
            if (emitindoCodigo) {
                logOtimizacoes.add(String.format("⚡ Constant Folding [Linha %d]: '%s %s %s' reduzido em tempo de compilação para '%d'",
                        ctx.op.getLine(), esq.repr, op, dir.repr, res));
            }
            return new Valor("int", res, String.valueOf(res), true);
        }

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.repr + " " + op + " " + dir.repr);
        return new Valor("int", res, "(" + esq.repr + " " + op + " " + dir.repr + ")", false);
    }

    @Override
    public Valor visitAddSubExpr(GabiParser.AddSubExprContext ctx) {
        Valor esq = visit(ctx.expr(0));
        Valor dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Valor("erro", null, "erro", false);
        }

        if (!esq.tipo.equals(dir.tipo)) {
            registrarErro(ctx.op, "Tipos incompatíveis na operação '" + op + "': não é permitido operar '" + esq.tipo + "' com '" + dir.tipo + "'.");
            return new Valor("erro", null, "erro", false);
        }

        if (esq.tipo.equals("texto")) {
            if (op.equals("+")) {
                String resTxt = String.valueOf(esq.val) + String.valueOf(dir.val);
                String temp = novoTemp();
                emitirTAC("  " + temp + " = " + esq.repr + " + " + dir.repr);
                return new Valor("texto", resTxt, "\"" + resTxt + "\"", esq.isConst && dir.isConst);
            } else {
                registrarErro(ctx.op, "Operador '-' não é suportado para o tipo 'texto'.");
                return new Valor("erro", null, "erro", false);
            }
        }

        int v1 = (Integer) esq.val;
        int v2 = (Integer) dir.val;
        int res = op.equals("+") ? (v1 + v2) : (v1 - v2);

        // Otimização de Constant Folding (Aula 25 - Slide 4)
        if (esq.isConst && dir.isConst) {
            if (emitindoCodigo) {
                logOtimizacoes.add(String.format("⚡ Constant Folding [Linha %d]: '%s %s %s' reduzido em tempo de compilação para '%d'",
                        ctx.op.getLine(), esq.repr, op, dir.repr, res));
            }
            return new Valor("int", res, String.valueOf(res), true);
        }

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.repr + " " + op + " " + dir.repr);
        return new Valor("int", res, esq.repr + " " + op + " " + dir.repr, false);
    }

    @Override
    public Valor visitRelExpr(GabiParser.RelExprContext ctx) {
        Valor esq = visit(ctx.expr(0));
        Valor dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Valor("erro", null, "erro", false);
        }

        if (!esq.tipo.equals("int") || !dir.tipo.equals("int")) {
            registrarErro(ctx.op, "Comparação '" + op + "' exige tipos 'int', mas recebeu '" + esq.tipo + "' e '" + dir.tipo + "'.");
            return new Valor("erro", null, "erro", false);
        }

        int v1 = (Integer) esq.val;
        int v2 = (Integer) dir.val;
        boolean res = switch (op) {
            case "<"  -> v1 < v2;
            case ">"  -> v1 > v2;
            case "<=" -> v1 <= v2;
            case ">=" -> v1 >= v2;
            case "==" -> v1 == v2;
            case "!=" -> v1 != v2;
            default   -> false;
        };

        int resInt = res ? 1 : 0;
        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.repr + " " + op + " " + dir.repr);
        return new Valor("int", resInt, esq.repr + " " + op + " " + dir.repr, false);
    }

    @Override
    public Valor visitLogicExpr(GabiParser.LogicExprContext ctx) {
        Valor esq = visit(ctx.expr(0));
        Valor dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Valor("erro", null, "erro", false);
        }

        if (!esq.tipo.equals("int") || !dir.tipo.equals("int")) {
            registrarErro(ctx.op, "Operador lógico '" + op + "' exige operandos inteiros/lógicos.");
            return new Valor("erro", null, "erro", false);
        }

        boolean b1 = ((Integer) esq.val) != 0;
        boolean b2 = ((Integer) dir.val) != 0;
        boolean res = op.equals("&&") ? (b1 && b2) : (b1 || b2);

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.repr + " " + op + " " + dir.repr);
        return new Valor("int", res ? 1 : 0, esq.repr + " " + op + " " + dir.repr, false);
    }

    @Override
    public Valor visitIntExpr(GabiParser.IntExprContext ctx) {
        String txt = ctx.INT().getText();
        return new Valor("int", Integer.parseInt(txt), txt, true);
    }

    @Override
    public Valor visitStrExpr(GabiParser.StrExprContext ctx) {
        String txt = ctx.STRING().getText();
        return new Valor("texto", txt.substring(1, txt.length() - 1), txt, true);
    }

    @Override
    public Valor visitVarExpr(GabiParser.VarExprContext ctx) {
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();

        Simbolo simbolo = buscarSimbolo(nomeVar);
        if (simbolo == null) {
            registrarErro(idToken, "Variável '" + nomeVar + "' não foi declarada antes do uso.");
            return new Valor("erro", null, "erro", false);
        }
        return new Valor(simbolo.tipo, simbolo.valor, nomeVar, false);
    }
}