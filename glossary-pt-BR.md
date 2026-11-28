# Brazilian Portuguese wiki glossary (AndroidAPSdocs, language pt-BR)

Style: simple, clear Brazilian Portuguese, short sentences, common words. Never European forms
(tela, not ecrã; celular or telefone, not telemóvel; arquivo, not ficheiro; configurações; "está
carregando"). Address the reader with "você" ("Selecione", "seu telefone"), as Brazilian software does.
Text quoted from the app keeps the app's own wording.
Numbers: decimal comma (5,5 mmol/L is written 5,5 mmol/l), 2%, en dash in ranges (1–4 horas), quotes "…".
Units: mg/dl, mmol/l, U for insulin units (as in the app), h / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Brazilian Portuguese string in bold, exactly as on screen. Current app strings
from Crowdin are in <WORK>/app_all.json ({key,en,tr}); they are newer than the repo; search there first.
If a label looks clearly wrong (European form, wrong meaning), use correct Portuguese and report it as
APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, HbA1c, CGM, fork, workflow names, labels of
English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays): English in bold, a
short Portuguese explanation in brackets is fine.

## Terms (app words win)
loop = loop (app: Loop); closed loop = loop fechado; open loop = loop aberto
Suspend loop = Suspender loop; Resume loop = Retomar loop
Objectives = Objetivos (app); Objective N = Objetivo N
BG = glicose (BG); BG Source = Fonte de BG (app); target = alvo; temp target = alvo temporário
carbs = carboidratos (app); basal = basal; temp basal = basal temporária (TBR)
Extended bolus = Bólus estendido (app); bolus = bólus; insulin = insulina; pump = bomba
profile = perfil; Configuration = Configuração (app); Settings = Configurações; running mode = Modo de operação (app)
Manage = Gerenciar; Treatments = Tratamentos; Open plugin = Abrir plugin; Communication = Comunicação
Automation = Automação; Scenes = Cenários; Maintenance = Manutenção; History browser = Navegador do histórico
Export settings = Exportar configurações; simple mode = Modo simples; master password = Senha mestra
Pair with master = Parear com o dispositivo principal; master = dispositivo principal; client = cliente
Authorized clients = Clientes autorizados; calibration = calibração; reservoir = reservatório; cannula = cânula
hypo = hipoglicemia; clinician = médico; caregiver = cuidador / pais
build (verb) = compilar; keystore = keystore; plugin = plugin
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Portuguese title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-pt-rBR files are older; these win)
(see app_all.json)

## Decisions from the app review (2026-10-31)
bolus = Bólus everywhere; IOB / COB / ISF / TBR / TDD stay English (Insulina ativa (IOB), Carboidratos ativos (COB)); pair = parear / pareamento
master password = Senha mestra; Prime/Fill = Purgar/Preencher; target = Alvo (in range = Na faixa-alvo); bolus advisor = Orientador de bólus
follow-up scene = Cenário seguinte; Retry button = Tentar novamente; extended bolus = Bólus estendido

## User decisions
- Wiki: upload and approve. App: translate missing strings + fix clear errors.

## Round 2 (2026-11-21): app completion + wiki
Configuration (menu, was Config Builder) = Configuração; Manage sheet = painel Gerenciar; Insulin settings = Configurações de insulina
Load peak from = Carregar pico de; preset = predefinição; Pregnant = Grávida; Switch plugin? = Trocar plugin?
External Companion Apps = Apps companheiros externos; mobile icon = ícone de celular
synced from the master = sincronizado a partir do dispositivo principal; fingerstick = glicose capilar; slope / offset = inclinação / deslocamento
pump screen (AAPS pump tab) = tela da bomba; insulin delivery = administração (not "entrega"); Suspend Delivery = Suspender administração
Insulin to go = Insulina restante; Authenticator = Autenticador; pump log = histórico da bomba; Recent searches = Pesquisas recentes
insulin change (concentration) = troca de insulina; write the profile to the pump = gravar o perfil na bomba; Insulin shortage = Falta de insulina
Notification Reader = Leitor de notificações; trusted BG data source = fonte de dados de BG confiável
Bolus protection = Proteção do bólus; Settings protection = Proteção das configurações; proofreading = revisão
Resume Delivery = Retomar administração (app fixed 2026-11-21); Failed to resume delivery = Falha ao retomar a administração
Average smoothing = Suavização por média (app fixed 2026-11-21); RileyLink Status = Status do RileyLink; RileyLink Stats = Estatísticas do RileyLink
Next (wizard button) = Seguinte
