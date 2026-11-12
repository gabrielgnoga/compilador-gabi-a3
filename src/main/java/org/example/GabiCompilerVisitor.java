package org.example;

import org.antlr.v4.runtime.Token;
import java.util.*;

public class GabiCompilerVisitor extends GabiBaseVisitor<GabiCompilerVisitor.Attr> {

    // Atributos sintetizados na AST durante a compilação (Tipo, Operando TAC, Expressão C e Constante)
    public static class Attr {
        public String tipo;       // "int", "texto" ou "erro"
        public String reprTAC;    // Operando para TAC (ex: "t1", "11", "x")
        public String reprC;      // Expressão equivalente em C (ex: "x + 1", "11")
        public boolean isConst;   // true se for constante conhecida em tempo de compilação (Constant Folding)
        public Integer valConst;  // Valor inteiro constante para Constant Folding

        public Attr(String tipo, String reprTAC, String reprC, boolean isConst, Integer valConst) {
            this.tipo = tipo;
            this.reprTAC = reprTAC;
            this.reprC = reprC;
            this.isConst = isConst;
            this.valConst = valConst;
        }
    }

    public static class Simbolo {
        public String nome;
        public String tipo;
        public String exprInicial;
        public int linhaDecl;
        public int escopoNivel;
        public int leituras;

        public Simbolo(String nome, String tipo, String exprInicial, int linhaDecl, int escopoNivel) {
            this.nome = nome;
            this.tipo = tipo;
            this.exprInicial = exprInicial;
            this.linhaDecl = linhaDecl;
            this.escopoNivel = escopoNivel;
            this.leituras = 0;
        }
    }

    public static class Escopo extends LinkedHashMap<String, Simbolo> {}

    private final Deque<Escopo> pilhaEscopos = new ArrayDeque<>();
    private final List<Simbolo> historicoSimbolos = new ArrayList<>();
    private final List<String> errosSemanticos = new ArrayList<>();
    private final List<String> codigoTAC = new ArrayList<>();
    private final List<String> logOtimizacoes = new ArrayList<>();
    private final StringBuilder codigoC = new StringBuilder();
    private final String[] linhasFonte;

    private int contTemp = 0;
    private int contLabel = 0;
    private int nivelIndentacao = 1;

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
        codigoTAC.add(instrucao);
    }

    private void indentarC(String linha) {
        codigoC.append("    ".repeat(Math.max(0, nivelIndentacao))).append(linha).append("\n");
    }

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
    public List<String> getCodigoTAC() { return codigoTAC; }
    public List<String> getLogOtimizacoes() { return logOtimizacoes; }
    public List<Simbolo> getHistoricoSimbolos() { return historicoSimbolos; }
    public String getCodigoC() { return codigoC.toString() + "    return 0;\n}\n"; }

    // ==================== INSTRUÇÕES (TRADUÇÃO EM TEMPO DE COMPILAÇÃO) ====================

    @Override
    public Attr visitPrograma(GabiParser.ProgramaContext ctx) {
        for (GabiParser.InstrucaoContext instr : ctx.instrucao()) {
            visit(instr);
        }
        for (Simbolo s : historicoSimbolos) {
            if (s.leituras == 0) {
                logOtimizacoes.add(String.format("⚠️ Dead Code Warning [Linha %d]: Variável '%s' (%s) declarada, mas nunca utilizada.",
                        s.linhaDecl, s.nome, s.tipo));
            }
        }
        return null;
    }

    @Override
    public Attr visitDeclInstr(GabiParser.DeclInstrContext ctx) {
        String tipoDecl = ctx.tipo().getText();
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();

        Escopo escopoAtual = pilhaEscopos.peek();
        int nivelEscopo = pilhaEscopos.size() - 1;

        if (escopoAtual.containsKey(nomeVar)) {
            Simbolo anterior = escopoAtual.get(nomeVar);
            registrarErro(idToken, "Variável '" + nomeVar + "' já foi declarada neste escopo (linha " + anterior.linhaDecl + ").");
            return new Attr("erro", "erro", "erro", false, null);
        }

        String reprTAC = tipoDecl.equals("int") ? "0" : "\"\"";
        String reprC = reprTAC;

        if (ctx.expr() != null) {
            Attr valExpr = visit(ctx.expr());
            reprTAC = valExpr.reprTAC;
            reprC = valExpr.reprC;

            if (!valExpr.tipo.equals("erro") && !valExpr.tipo.equals(tipoDecl)) {
                registrarErro(idToken, "Tipos incompatíveis na declaração de '" + nomeVar + "': esperado '" + tipoDecl + "', mas recebeu '" + valExpr.tipo + "'.");
            }
        }

        Simbolo novo = new Simbolo(nomeVar, tipoDecl, reprC, idToken.getLine(), nivelEscopo);
        escopoAtual.put(nomeVar, novo);
        historicoSimbolos.add(novo);

        emitirTAC("  " + nomeVar + " = " + reprTAC);
        String tipoC = tipoDecl.equals("int") ? "int" : "const char*";
        indentarC(tipoC + " " + nomeVar + " = " + reprC + ";");

        return new Attr(tipoDecl, nomeVar, nomeVar, false, null);
    }

    @Override
    public Attr visitAtribInstr(GabiParser.AtribInstrContext ctx) {
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();

        Simbolo simbolo = buscarSimbolo(nomeVar);
        Attr valExpr = visit(ctx.expr());

        if (simbolo == null) {
            registrarErro(idToken, "Variável '" + nomeVar + "' não foi declarada antes do uso.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (!valExpr.tipo.equals("erro") && !simbolo.tipo.equals(valExpr.tipo)) {
            registrarErro(idToken, "Tipos incompatíveis na atribuição para '" + nomeVar + "': variável é '" + simbolo.tipo + "', mas expressão é '" + valExpr.tipo + "'.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        emitirTAC("  " + nomeVar + " = " + valExpr.reprTAC);
        indentarC(nomeVar + " = " + valExpr.reprC + ";");
        return valExpr;
    }

    @Override
    public Attr visitPrintInstr(GabiParser.PrintInstrContext ctx) {
        Attr val = visit(ctx.expr());
        emitirTAC("  print " + val.reprTAC);
        String fmt = "texto".equals(val.tipo) ? "\"%s\\n\"" : "\"%d\\n\"";
        indentarC("printf(" + fmt + ", " + val.reprC + ");");
        return val;
    }

    @Override
    public Attr visitReadInstr(GabiParser.ReadInstrContext ctx) {
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();
        Simbolo simbolo = buscarSimbolo(nomeVar);

        if (simbolo == null) {
            registrarErro(idToken, "Variável '" + nomeVar + "' não foi declarada antes do comando 'read'.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        emitirTAC("  read " + nomeVar);
        if (simbolo.tipo.equals("int")) {
            indentarC("scanf(\"%d\", &" + nomeVar + ");");
        }
        return new Attr(simbolo.tipo, nomeVar, nomeVar, false, null);
    }

    @Override
    public Attr visitBlocoInstr(GabiParser.BlocoInstrContext ctx) {
        pilhaEscopos.push(new Escopo());
        for (GabiParser.InstrucaoContext instr : ctx.instrucao()) {
            visit(instr);
        }
        pilhaEscopos.pop();
        return null;
    }

    @Override
    public Attr visitIfInstr(GabiParser.IfInstrContext ctx) {
        Attr cond = visit(ctx.expr());
        if (!cond.tipo.equals("erro") && !cond.tipo.equals("int")) {
            registrarErro(ctx.getStart(), "A condição do 'if' deve ser inteira/lógica, mas recebeu '" + cond.tipo + "'.");
        }

        String lblElse = novoLabel();
        String lblFim = (ctx.instrucao(1) != null) ? novoLabel() : lblElse;

        emitirTAC("  ifFalse " + cond.reprTAC + " goto " + lblElse);

        indentarC("if (" + cond.reprC + ") {");
        nivelIndentacao++;
        visit(ctx.instrucao(0));
        nivelIndentacao--;

        if (ctx.instrucao(1) != null) {
            emitirTAC("  goto " + lblFim);
            emitirTAC(lblElse + ":");
            indentarC("} else {");
            nivelIndentacao++;
            visit(ctx.instrucao(1));
            nivelIndentacao--;
            emitirTAC(lblFim + ":");
        } else {
            emitirTAC(lblElse + ":");
        }

        indentarC("}");
        return null;
    }

    @Override
    public Attr visitWhileInstr(GabiParser.WhileInstrContext ctx) {
        String lblInicio = novoLabel();
        String lblFim = novoLabel();

        emitirTAC(lblInicio + ":");
        Attr cond = visit(ctx.expr());
        if (!cond.tipo.equals("erro") && !cond.tipo.equals("int")) {
            registrarErro(ctx.getStart(), "A condição do 'while' deve ser inteira/lógica, mas recebeu '" + cond.tipo + "'.");
        }
        emitirTAC("  ifFalse " + cond.reprTAC + " goto " + lblFim);

        indentarC("while (" + cond.reprC + ") {");
        nivelIndentacao++;
        visit(ctx.instrucao());
        nivelIndentacao--;
        indentarC("}");

        emitirTAC("  goto " + lblInicio);
        emitirTAC(lblFim + ":");
        return null;
    }

    @Override
    public Attr visitDoWhileInstr(GabiParser.DoWhileInstrContext ctx) {
        String lblInicio = novoLabel();
        emitirTAC(lblInicio + ":");

        indentarC("do {");
        nivelIndentacao++;
        visit(ctx.instrucao());
        nivelIndentacao--;

        Attr cond = visit(ctx.expr());
        if (!cond.tipo.equals("erro") && !cond.tipo.equals("int")) {
            registrarErro(ctx.getStart(), "A condição do 'do-while' deve ser inteira/lógica.");
        }

        indentarC("} while (" + cond.reprC + ");");
        emitirTAC("  ifTrue " + cond.reprTAC + " goto " + lblInicio);
        return null;
    }

    // ==================== EXPRESSÕES + CONSTANT FOLDING + TAC ====================

    @Override
    public Attr visitParenExpr(GabiParser.ParenExprContext ctx) {
        Attr v = visit(ctx.expr());
        if (v.isConst) return v;
        return new Attr(v.tipo, v.reprTAC, "(" + v.reprC + ")", false, null);
    }

    @Override
    public Attr visitUnaryExpr(GabiParser.UnaryExprContext ctx) {
        Attr v = visit(ctx.expr());
        String op = ctx.op.getText();
        if (v.tipo.equals("erro")) return v;

        if (!v.tipo.equals("int")) {
            registrarErro(ctx.op, "Operador unário '" + op + "' exige tipo 'int'.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (v.isConst && v.valConst != null) {
            int res = op.equals("-") ? -v.valConst : (v.valConst == 0 ? 1 : 0);
            return new Attr("int", String.valueOf(res), String.valueOf(res), true, res);
        }
        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + op + v.reprTAC);
        return new Attr("int", temp, op + v.reprC, false, null);
    }

    @Override
    public Attr visitMulDivExpr(GabiParser.MulDivExprContext ctx) {
        Attr esq = visit(ctx.expr(0));
        Attr dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (!esq.tipo.equals("int") || !dir.tipo.equals("int")) {
            registrarErro(ctx.op, "Operador '" + op + "' exige operandos 'int', mas recebeu '" + esq.tipo + "' e '" + dir.tipo + "'.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (op.equals("/") && dir.isConst && dir.valConst != null && dir.valConst == 0) {
            registrarErro(ctx.op, "Divisão por constante zero detectada em tempo de compilação.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (esq.isConst && dir.isConst && esq.valConst != null && dir.valConst != null) {
            int res = op.equals("*") ? (esq.valConst * dir.valConst) : (esq.valConst / dir.valConst);
            logOtimizacoes.add(String.format("⚡ Constant Folding [Linha %d]: '%s %s %s' reduzido em tempo de compilação para '%d'",
                    ctx.op.getLine(), esq.reprC, op, dir.reprC, res));
            return new Attr("int", String.valueOf(res), String.valueOf(res), true, res);
        }

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.reprTAC + " " + op + " " + dir.reprTAC);
        return new Attr("int", temp, esq.reprC + " " + op + " " + dir.reprC, false, null);
    }

    @Override
    public Attr visitAddSubExpr(GabiParser.AddSubExprContext ctx) {
        Attr esq = visit(ctx.expr(0));
        Attr dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (!esq.tipo.equals(dir.tipo)) {
            registrarErro(ctx.op, "Tipos incompatíveis na operação '" + op + "': não é permitido operar '" + esq.tipo + "' com '" + dir.tipo + "'.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (esq.tipo.equals("texto")) {
            if (op.equals("+")) {
                if (esq.isConst && dir.isConst) {
                    String s1 = esq.reprC.substring(1, esq.reprC.length() - 1);
                    String s2 = dir.reprC.substring(1, dir.reprC.length() - 1);
                    String unido = "\"" + s1 + s2 + "\"";
                    return new Attr("texto", unido, unido, true, null);
                }
                String temp = novoTemp();
                emitirTAC("  " + temp + " = " + esq.reprTAC + " + " + dir.reprTAC);
                return new Attr("texto", temp, esq.reprC, false, null);
            } else {
                registrarErro(ctx.op, "Operador '-' não é suportado para o tipo 'texto'.");
                return new Attr("erro", "erro", "erro", false, null);
            }
        }

        if (esq.isConst && dir.isConst && esq.valConst != null && dir.valConst != null) {
            int res = op.equals("+") ? (esq.valConst + dir.valConst) : (esq.valConst - dir.valConst);
            logOtimizacoes.add(String.format("⚡ Constant Folding [Linha %d]: '%s %s %s' reduzido em tempo de compilação para '%d'",
                    ctx.op.getLine(), esq.reprC, op, dir.reprC, res));
            return new Attr("int", String.valueOf(res), String.valueOf(res), true, res);
        }

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.reprTAC + " " + op + " " + dir.reprTAC);
        return new Attr("int", temp, esq.reprC + " " + op + " " + dir.reprC, false, null);
    }

    @Override
    public Attr visitRelExpr(GabiParser.RelExprContext ctx) {
        Attr esq = visit(ctx.expr(0));
        Attr dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (!esq.tipo.equals("int") || !dir.tipo.equals("int")) {
            registrarErro(ctx.op, "Comparação '" + op + "' exige tipos 'int', mas recebeu '" + esq.tipo + "' e '" + dir.tipo + "'.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.reprTAC + " " + op + " " + dir.reprTAC);
        return new Attr("int", temp, esq.reprC + " " + op + " " + dir.reprC, false, null);
    }

    @Override
    public Attr visitLogicExpr(GabiParser.LogicExprContext ctx) {
        Attr esq = visit(ctx.expr(0));
        Attr dir = visit(ctx.expr(1));
        String op = ctx.op.getText();

        if (esq.tipo.equals("erro") || dir.tipo.equals("erro")) {
            return new Attr("erro", "erro", "erro", false, null);
        }

        if (!esq.tipo.equals("int") || !dir.tipo.equals("int")) {
            registrarErro(ctx.op, "Operador lógico '" + op + "' exige operandos inteiros/lógicos.");
            return new Attr("erro", "erro", "erro", false, null);
        }

        String temp = novoTemp();
        emitirTAC("  " + temp + " = " + esq.reprTAC + " " + op + " " + dir.reprTAC);
        return new Attr("int", temp, esq.reprC + " " + op + " " + dir.reprC, false, null);
    }

    @Override
    public Attr visitIntExpr(GabiParser.IntExprContext ctx) {
        String txt = ctx.INT().getText();
        return new Attr("int", txt, txt, true, Integer.parseInt(txt));
    }

    @Override
    public Attr visitStrExpr(GabiParser.StrExprContext ctx) {
        String txt = ctx.STRING().getText();
        return new Attr("texto", txt, txt, true, null);
    }

    @Override
    public Attr visitVarExpr(GabiParser.VarExprContext ctx) {
        String nomeVar = ctx.ID().getText();
        Token idToken = ctx.ID().getSymbol();

        Simbolo simbolo = buscarSimbolo(nomeVar);
        if (simbolo == null) {
            registrarErro(idToken, "Variável '" + nomeVar + "' não foi declarada antes do uso.");
            return new Attr("erro", "erro", "erro", false, null);
        }
        simbolo.leituras++;
        return new Attr(simbolo.tipo, nomeVar, nomeVar, false, null);
    }
}