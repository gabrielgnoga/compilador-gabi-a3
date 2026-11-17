
# Testes automatizados do compilador Gabi
# Verifica programas validos, erros esperados e resultados especificos.

$ErrorActionPreference = "Stop"

$jar = Join-Path $PSScriptRoot "target\gabi-compiler.jar"
$pastaExemplos = Join-Path $PSScriptRoot "exemplos"

# Compila o projeto caso o JAR nao exista
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

# Casos de teste e seus resultados esperados
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

    # Executa o compilador e captura sua saida
    $saida = (& java -jar $jar $arquivo 2>&1) | Out-String
    $codigoSaida = $LASTEXITCODE

    # Localiza a secao de diagnosticos.
    # Evita confundir palavras no codigo-fonte com erros encontrados.
    $inicioDiagnosticos = [regex]::Match(
        $saida,
        '(?m)^--- \[(?:5\.|3 & 5\.)'
    )

    $diagnosticos = ""

    if ($inicioDiagnosticos.Success) {
        $diagnosticos = $saida.Substring($inicioDiagnosticos.Index)
    }

    # Verifica se houve algum diagnostico ou compilacao abortada
    $possuiDiagnosticos = $diagnosticos -match "Erro\s+"
    $compilacaoAbortada = $diagnosticos -match "Abortada"

    # Confirma a geracao dos artefatos nos programas validos
    $compilacaoConcluida = $saida -match "saida\.tac.*saida\.c"

    $passou = $false
    $motivo = ""

    switch ($caso.Tipo) {

        "Valido" {

            $passou = (
                $codigoSaida -eq 0 -and
                $compilacaoConcluida -and
                -not $possuiDiagnosticos -and
                -not $compilacaoAbortada
            )

            if (-not $passou) {
                $motivo = "Programa valido apresentou erros ou nao concluiu a compilacao."
            }

            # Teste 1: multiplicacao antes da soma
            if ($caso.Nome -eq "teste1.txt") {

                $resultadoCorreto = $saida -match "(?m)^\s*x = 11\s*$"

                if (-not $resultadoCorreto) {
                    $passou = $false
                    $motivo = "Esperado no TAC: x = 11"
                }
            }

            # Teste 2: prioridade dos parenteses
            if ($caso.Nome -eq "teste2.txt") {

                $resultadoCorreto = $saida -match "(?m)^\s*base = 14\s*$"

                if (-not $resultadoCorreto) {
                    $passou = $false
                    $motivo = "Esperado no TAC: base = 14"
                }
            }
        }

        "Lexico" {

            # Teste 3: caractere @ invalido.
            # O erro sintatico seguinte e uma consequencia esperada.
            $erroEsperado = $diagnosticos.Contains(
                "token recognition error at: '@'"
            )

            $passou = (
                $erroEsperado -and
                $compilacaoAbortada
            )

            if (-not $passou) {
                $motivo = "Erro lexico do caractere @ nao foi diagnosticado corretamente."
            }
        }

        "Sintatico" {

            # Teste 4: falta de ponto e virgula antes de int
            $erroEsperado = $diagnosticos.Contains(
                "missing ';' at 'int'"
            )

            $semErroLexico = -not (
                $diagnosticos -match "token recognition error"
            )

            $passou = (
                $erroEsperado -and
                $semErroLexico -and
                $compilacaoAbortada
            )

            if (-not $passou) {
                $motivo = "Erro sintatico de ponto e virgula nao foi diagnosticado corretamente."
            }
        }

        "Semantico" {

            # Teste 5: diagnosticos especificos, nao apenas nomes de variaveis
            $tipoIncompativel = $diagnosticos -match "Tipos incompat"

            $variavelNaoDeclarada = $diagnosticos -match "Vari.vel 'naoExiste' n.o foi declarada"

            $variavelForaEscopo = $diagnosticos -match "Vari.vel 'secretaDoBloco' n.o foi declarada"

            $semErroLexico = -not (
                $diagnosticos -match "token recognition error"
            )

            $semErroSintatico = -not (
                $diagnosticos -match "missing ';'|extraneous input|mismatched input|no viable alternative"
            )

            $passou = (
                $tipoIncompativel -and
                $variavelNaoDeclarada -and
                $variavelForaEscopo -and
                $compilacaoAbortada -and
                $semErroLexico -and
                $semErroSintatico
            )

            if (-not $passou) {
                $motivo = "Os tres erros semanticos esperados nao foram diagnosticados corretamente."
            }
        }
    }

    if ($passou) {
        Write-Host "PASSOU - $($caso.Nome) [$($caso.Tipo)]" -ForegroundColor Green
        $aprovados++
    }
    else {
        Write-Host "FALHOU - $($caso.Nome) [$($caso.Tipo)]" -ForegroundColor Red
        Write-Host "Motivo: $motivo" -ForegroundColor Yellow
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
