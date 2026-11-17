# Gera o executavel do interface-grafica com o jpackage (vem no JDK 14+).
# Resultado, na raiz CLAUDE/:
#   Interface Grafica\Interface Grafica.exe   (com um Java minimo dentro; nao precisa de Java instalado)
#   Interface Grafica.lnk                     (atalho para abrir com duplo-clique)
#
# Uso (na pasta app/interface-grafica):   .\empacotar.ps1            (roda os testes antes)
#                                         .\empacotar.ps1 -SemTestes
# Se o PowerShell bloquear scripts:       powershell -ExecutionPolicy Bypass -File .\empacotar.ps1
param([switch]$SemTestes)

$ErrorActionPreference = 'Stop'
$projeto = $PSScriptRoot
$raiz = (Resolve-Path (Join-Path $projeto '..\..')).Path
$nome = 'Interface Grafica'
$destino = Join-Path $raiz $nome
$atalho = Join-Path $raiz "$nome.lnk"

# --- JDK com jpackage --------------------------------------------------------------------------------
function Achar-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\jpackage.exe'))) { return $env:JAVA_HOME }
    $candidatos = Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium' -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\jpackage.exe') } |
        Sort-Object Name -Descending
    if ($candidatos) { return $candidatos[0].FullName }
    throw 'Nenhum JDK 17+ com jpackage encontrado. Instale um JDK ou defina JAVA_HOME.'
}
$env:JAVA_HOME = Achar-Jdk
Write-Host "JDK: $env:JAVA_HOME"

# --- Se o app estiver aberto, a pasta antiga nao pode ser apagada --------------------------------------
$exe = Join-Path $destino "$nome.exe"
if (Test-Path $exe) {
    $aberto = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $exe }
    if ($aberto) { throw "Feche o '$nome' antes de empacotar de novo." }
}

# --- Build: jar do app + dependencias -----------------------------------------------------------------
Push-Location $projeto
try {
    $metas = @('-B', '-q', 'clean', 'package')
    if ($SemTestes) { $metas += '-DskipTests' }
    & .\mvnw.cmd @metas
    if ($LASTEXITCODE -ne 0) { throw 'Falha no build (mvnw package).' }
    & .\mvnw.cmd -B -q dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=target/libs'
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar as dependencias.' }
} finally {
    Pop-Location
}
$jar = Get-ChildItem (Join-Path $projeto 'target') -Filter 'interface-grafica-*.jar' | Select-Object -First 1
Copy-Item $jar.FullName (Join-Path $projeto 'target\libs')
$versao = ($jar.BaseName -replace '^interface-grafica-', '') -replace '[^0-9.]', ''

# --- Icone .ico a partir da logo (PNG dentro do ICO, aceito pelo Windows Vista+) ------------------------
$png = [IO.File]::ReadAllBytes((Join-Path $projeto 'src\main\resources\dev\interfacegrafica\favicon-dark.png'))
$ico = Join-Path $projeto 'target\icone.ico'
$ms = New-Object IO.MemoryStream
$bw = New-Object IO.BinaryWriter($ms)
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]1)          # ICONDIR: reservado, tipo=icone, 1 imagem
$bw.Write([Byte]64); $bw.Write([Byte]64); $bw.Write([Byte]0); $bw.Write([Byte]0)  # 64x64, sem paleta
$bw.Write([UInt16]1); $bw.Write([UInt16]32)                                # planos, bits por pixel
$bw.Write([UInt32]$png.Length); $bw.Write([UInt32]22)                      # tamanho e posicao do PNG
$bw.Write($png); $bw.Flush()
[IO.File]::WriteAllBytes($ico, $ms.ToArray())
$bw.Dispose()

# --- jpackage ---------------------------------------------------------------------------------------
if (Test-Path $destino) { Remove-Item $destino -Recurse -Force }
# modulos do Java: saida do jdeps (+ localedata para datas em pt-BR, charsets, logging)
$modulos = 'java.base,java.desktop,java.logging,java.net.http,java.prefs,java.sql,jdk.jfr,jdk.unsupported,jdk.localedata,jdk.charsets'
& (Join-Path $env:JAVA_HOME 'bin\jpackage.exe') `
    --type app-image `
    --name $nome `
    --app-version $versao `
    --vendor 'Geomis Technologies' `
    --description 'Interface grafica da base CLAUDE (lembretes e tarefas)' `
    --input (Join-Path $projeto 'target\libs') `
    --main-jar $jar.Name `
    --main-class dev.interfacegrafica.Launcher `
    --icon $ico `
    --add-modules $modulos `
    --jlink-options '--strip-debug --no-header-files --no-man-pages --compress=2' `
    --java-options '-Dfile.encoding=UTF-8' `
    --dest $raiz
if ($LASTEXITCODE -ne 0) { throw 'Falha no jpackage.' }

# --- Atalho na raiz ---------------------------------------------------------------------------------
$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut($atalho)
$lnk.TargetPath = $exe
$lnk.WorkingDirectory = $destino
$lnk.IconLocation = "$exe,0"
$lnk.Description = 'Interface Grafica (lembretes e tarefas)'
$lnk.Save()

$tamanho = [Math]::Round(((Get-ChildItem $destino -Recurse -File | Measure-Object Length -Sum).Sum / 1MB), 0)
Write-Host ""
Write-Host "Pronto ($tamanho MB):"
Write-Host "  $exe"
Write-Host "  $atalho  (duplo-clique para abrir; pode fixar na barra de tarefas)"
