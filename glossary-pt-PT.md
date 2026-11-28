# European Portuguese wiki glossary (AndroidAPSdocs, language pt-PT)

Style: simple, clear European Portuguese (Portugal, current spelling of the 1990 agreement:
objetivo, ação, ativo), short sentences, common words. Never Brazilian forms (no "você" pronoun in
running text, no gerund for ongoing actions: "está a carregar", not "está carregando"; ecrã, not tela;
telemóvel or telefone, not celular; ficheiro, not arquivo; definições, not configurações).
Address the reader with the polite 3rd person without pronoun ("Selecione", "o seu telefone"), as
Portuguese software and manuals do. Text quoted from the app keeps the app's own wording.
Numbers: decimal comma (5,5 mmol/l), 2 %, en dash in ranges (1–4 horas), quotes «…».
Units: mg/dl, mmol/l, U for insulin units (as in the app), h / min in text.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's European Portuguese string in bold, exactly as on screen. Current app strings
from Crowdin are in <WORK>/app_all.json ({key,en,tr}); they are newer than the repo; search there first.
If a label looks clearly wrong (Brazilian form, old spelling, wrong meaning), use correct Portuguese
and report it as APP bug.
Names kept in English: Nightscout, Tidepool, xDrip, BYODA, GitHub, GitHub Actions, Android Studio,
AAPS Builder, Autosens, SMB, HbA1c, CGM, fork (IOB, COB, ISF, IC: use the app abbreviations below), workflow names, labels of
English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays): English in bold, a
short Portuguese explanation in brackets is fine.

## Terms (app words win)
loop = loop (app: Loop); closed loop = loop fechado; open loop = loop aberto
Suspend loop = Suspender loop; Resume loop = Retomar loop
Objectives = Objetivos (app has old spelling «Objectivos» - report); Objective N = Objetivo N
BG = glicose (BG); BG Source = Fonte da Glicose (app); target = alvo; temp target = alvo temporário
carbs = hidratos de carbono (app: Hidratos / HC); basal = basal; temp basal = basal temporária (TBR)
Extended bolus = Bólus prolongado (app); bolus = bólus; insulin = insulina; pump = bomba
profile = perfil; Settings = Definições (app); calibration = calibração; reservoir = reservatório
cannula = cânula; Automation = Automatização (app); Maintenance = Manutenção
History browser = Navegador do histórico; Export settings = Exportar definições; master password = Senha Mestre
hypo = hipoglicemia; clinician = médico; caregiver = cuidador / pais
App abbreviations (use them, the app shows them): IOB = IA, COB = HCA, ISF = FSI, IC = IHC, DIA = DAI, TDD = TID, UAM = RNA, LGS = SGB, BGI = IG; Autotune = Autoajuste (app plugin name); hardened mode = modo reforçado
build (verb) = compilar; keystore = keystore; plugin = plugin
SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Portuguese title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## App labels fixed on Crowdin (local values-pt-rPT files are older; these win)
(see app_all.json)

## Decisions from the app review (2026-10-31)
AAPS is masculine in the wiki («o AAPS»); the app still has about 75 «a AAPS» strings (open)
TBR = basal temporária; Status = Estado; watchface (f.): personalizada / predefinida; disconnect = desligar, reconnect = religar
master password = senha mestre; Prime = purgar / purga, Fill (menu, button) = Preencher, physically fill = encher; cannula = cânula
site change = Mudança de local da bomba; Reset (state) = Repor, device reset = Reinício; QuickWizard = Assistente Rápido; master phone = telefone principal

## User decisions
- Wiki: upload and approve. App: translate missing strings + fix clear errors.

## Round 2 (2026-11-21): app completion + wiki
Configuration (menu, was Config Builder) = Configuração; Open plugin = Abrir plugin; Manage = Gerir
Insulin settings = Definições de insulina; Load peak from = Carregar pico de; Patient age = Idade do paciente
fingerstick = glicose capilar; synced from the master = sincronizado a partir do principal
DIA = DAI (Duração da ação da insulina); broadcast = difundir / difusão; Recent searches = Pesquisas recentes
pump screen (AAPS pump tab) = ecrã da bomba; pump log = registo da bomba; cloud sync = sincronização com a nuvem
Insulin change (concentration) = mudança de insulina; write the profile to the pump = escrever o perfil na bomba
Engineering Mode = modo engenheiro (app engineering_mode_enabled «Modo engenheiro»; bold label «Modo engenheiro»), not «modo de engenharia»
Setup (Open Humans button) = Configurar (app setup, fixed on Crowdin 2026-11-21); the page heading «Setup» stays «Instalação» (not the button)
SettingUpAaps/ChangeAapsConfiguration.md = Alterar a configuração do AAPS (H1 and toctree; replaces «Fazer alterações à configuração do AAPS» from round 1)
Release notes (page title, link text to the page) = Notas das Versões; release notes of one version (GitHub link) = Notas da versão
secret (GitHub Actions secret) = secret (m.); pairing secret (Master/Client control) = segredo (de emparelhamento)
download = descarregar in new text; the wiki also uses «transferir» (also normal pt-PT, Android pt-PT says «Transferir»); not unified, see open items
password = senha (app); «palavra-passe» no longer used
backup = cópia de segurança; Notification Reader = Leitor de notificações (app)
fingerstick = glicose capilar (Libre2 «picada no dedo» left: «verifique com uma picada no dedo» is a plain action)
Low Glucose Suspend (running mode) = Suspensão por glicose baixa (wiki, app loop_status_lgs); app key lowglucosesuspend «Suspensão Glicose Baixa» is the odd one (APP bug)
Number of units (Omnipod alert setting) = N.º de unidades (app)
Show battery level reported by OrangeLink/EmaLink/DiaLink = Mostrar o nível de bateria reportado pelo OrangeLink/EmaLink/DiaLink (app)
Combo v2 labels: Successfully paired with Combo = Emparelhamento com a Combo concluído com sucesso; Updating pump status = A atualizar o estado da bomba; Enable verbose Combo logging = Ativar registo detalhado da Combo; Autodetect and automatically enter insulin reservoir change = Detetar automaticamente e registar a troca do reservatório de insulina (app)
Record pump site change = Registar mudança de local da bomba; Record insulin cartridge change = Registar mudança de cartucho de insulina (app)
