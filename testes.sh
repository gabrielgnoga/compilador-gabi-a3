#!/usr/bin/env bash
echo "===================================================="
echo "   1. COMPILANDO O PROJETO (ANTLR4 + JAVAC + JAR)   "
echo "===================================================="
mvn clean package -DskipTests

echo ""
echo "===================================================="
echo "   2. EXECUTANDO BATERIA DE TESTES (PASTA EXEMPLOS) "
echo "===================================================="
for t in exemplos/*.txt; do
    echo ""
    echo ">>> Testando arquivo: $t <<<"
    java -jar target/gabi-compiler.jar "$t"
done