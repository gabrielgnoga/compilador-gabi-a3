#!/usr/bin/env bash
echo "===================================================="
echo "   BATERIA AUTOMATIZADA DE TESTES — LINGUAGEM GABI  "
echo "===================================================="

for t in exemplos/*.txt; do
    echo ""
    echo ">>> Executando caso de teste: $t <<<"
    java -cp "target/classes;C:/Users/Gabriel/.m2/repository/org/antlr/antlr4-runtime/4.13.2/antlr4-runtime-4.13.2.jar" org.example.Main "$t"
done