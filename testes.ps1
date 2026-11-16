
# Testes automatizados do compilador Gabi
# Executa os exemplos e verifica os resultados esperados.

$ErrorActionPreference = "Stop"

# Caminhos do projeto
$jar = Join-Path $PSScriptRoot "target\gabi-compiler.jar"
$pastaExemplos = Join-Path $PSScriptRoot "exemplos"

# Compila o projeto se o JAR ainda nao existir
if (-not (Test-Path $jar)) {
    Write-Host "Compilando o projeto..." -ForegroundColor Yellow

    Push-Location $PSScriptRoot
    try {
        & mvn clean package

        if ($LASTEXITCODE -ne 0) {
            throw "Falha na compilacao com Maven."
        }
    }
    finally {
        Pop-Location
    }
}

# Resultados esperados para cada arquivo
# Strings sem acentos evitam problemas de codificacao.
$casos = @(
    @{ Nome = "teste1.txt"; Tipo = "Valido" },
    @{ Nome = "teste2.txt"; Tipo = "Valido" },
    @{ Nome = "teste3.txt"; Tipo = "Lexico" },
    @{ Nome = "teste4.txt"; Tipo = "Sintatico" },
    @{ Nome = "teste5.txt"; Tipo = "Semantico" },
    @{ Nome = "teste6.txt"; Tipo = "Valido" },
    @{ Nome = "teste7.txt"; Tipo = "Valido" },
    @{ Nome = "teste8.txt"; Tipo = "Valido" },
    @{ Nome = "teste9.txt"; Tipo = "Valido" }
)

$aprovados = 0
$reprovados = 0

Write-Host ""
Write-Host "========== TESTES DO COMPILADOR GABI =========="

foreach ($caso in $casos) {

    $arquivo = Join-Path $pastaExemplos $caso.Nome

    if (-not (Test-Path $arquivo)) {
        Write-Host "FALHOU - $($caso.Nome): arquivo nao encontrado" -ForegroundColor Red
        $reprovados++
        continue
    }

    # Executa o compilador e captura a saida
    $saida = (& java -jar $jar $arquivo 2>&1) | Out-String
    $codigoSaida = $LASTEXITCODE

    # Marcadores das mensagens geradas pelo compilador
    $erroLexico = $saida -match "token recognition error"
    $erroSintatico = $saida -match "missing ';'|extraneous input|mismatched input|no viable alternative"
    $erroSemantico = $saida -match "Tipos incompat|naoExiste.*declarada|secretaDoBloco.*declarada|Erro Sem"

    # Programas validos geram esta secao ao finalizar
    $compilacaoConcluida = $saida -match "saida\.tac.*saida\.c"

    $passou = $false

    switch ($caso.Tipo) {

        "Valido" {
            $passou = (
                $codigoSaida -eq 0 -and
                $compilacaoConcluida -and
                -not $erroLexico -and
                -not $erroSintatico -and
                -not $erroSemantico
            )
        }

        "Lexico" {
            $passou = $erroLexico
        }

        "Sintatico" {
            $passou = $erroSintatico -and -not $erroLexico
        }

        "Semantico" {
            $passou = (
                ($saida -match "Tipos incompat") -and
                ($saida -match "naoExiste") -and
                ($saida -match "secretaDoBloco") -and
                -not $erroLexico -and
                -not $erroSintatico
            )
        }
    }

    if ($passou) {
        Write-Host "PASSOU - $($caso.Nome) [$($caso.Tipo)]" -ForegroundColor Green
        $aprovados++
    }
    else {
        Write-Host "FALHOU - $($caso.Nome) [$($caso.Tipo)]" -ForegroundColor Red
        Write-Host "Saida do compilador:"
        Write-Host $saida
        $reprovados++
    }
}

Write-Host ""
Write-Host "========== RESULTADO FINAL =========="
Write-Host "Aprovados: $aprovados"
Write-Host "Reprovados: $reprovados"
Write-Host "Total: $($aprovados + $reprovados)"

if ($reprovados -gt 0) {
    exit 1
}

exit 0
