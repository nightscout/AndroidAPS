# Korean wiki and app glossary (AndroidAPSdocs + AndroidAPS, language ko)

Style (user decision 2026-10-31): simple, clear standard Korean in the formal polite 합니다체
("선택합니다", "누르십시오", "설정하십시오"), short sentences, common words, as in medical device
manuals. Buttons and short labels: noun forms ("저장", "취소", "활성화"), as Korean Android apps do.
Text quoted from the app keeps the app's own wording.
Product names, plugin names and abbreviations stay in Latin letters (AAPS, Nightscout, xDrip,
Tidepool, GitHub, Android Studio, AAPS Builder, Autosens, SMB, IOB, COB, ISF, IC, CGM, TDD, DIA).
Numbers: decimal point (5.5), units mmol/L and mg/dL as on screen (app format strings win), insulin
units as the app shows them (check `format_insulin_units` in app_all.json). Time: 시간 / 분.
Keep "AAPS" (never "AndroidAPS" unless the source says so).
UI labels: use the app's Korean string in bold, exactly as on screen. Current Korean app strings from
Crowdin are in <WORK>/app_all.json ({key,en,tr}). About half the app was translated before; the rest
is being translated now (<WORK>/app_missing_part*.out.json). If a label looks clearly wrong, use
correct Korean and report it as APP bug.
Labels of English-only tools (GitHub, AAPS Builder page, Android Studio, pump displays) stay English
in bold; a short Korean explanation in brackets is fine.

## Terms (app words win; add new ones here as they are decided)
loop = 루프; closed loop = 폐쇄 루프; open loop = 개방 루프
bolus = 볼러스; basal = 기저; temp basal = 임시 기저; insulin = 인슐린; pump = 펌프
carbs = 탄수화물; profile = 프로파일 (app: 124 approved strings); target = 목표; temp target = 임시 목표
BG / blood glucose = 혈당; sensor = 센서; SMS commands, JSON keys, anchors, code, image names: unchanged.

## Links to other wiki pages
Link text that names another page must use that page's Korean title from <WORK>/titles.txt or
<WORK>/titles_proposed.txt. If the page has no title yet, translate it and add
`Folder/Page.md = title` to your <name>.terms.txt.

## Decisions (2026-10-31)
- User decision: bolus = 볼러스, basal = 기저 everywhere (old app strings with English "Bolus"/"Basal" get rewritten); extended bolus = 확장 볼러스; temp basal = 임시 기저; basal rate = 기저 주입량
- profile = 프로파일 (existing app, 124 strings); profile switch = 프로파일 전환
- pairing = 페어링 (never 동기화 = sync); OK button = 확인
- scene = 장면, follow-up scene = 후속 장면; running mode = 실행 모드; master = 마스터; main phone = 메인 휴대폰; client = 클라이언트
- roles: 읽기 전용 / 식사 / 보호자 / 관리자; hardened mode = 강화 모드; simple mode = 간단 모드
- QuickWizard = 빠른 마법사; bolus wizard = 볼러스 마법사; site rotation = 위치 순환; CarePortal = 케어포털
- export / import = 내보내기 / 가져오기; maintenance = 정비; history = 이력; treatments = 처치
- IOB = 활성 인슐린 (IOB); COB = 활성 탄수화물 (COB); delta = 증분; hypo = 저혈당 (never 저혈압 = low blood pressure)
- time units in Korean (분, 시간, 초, 일); U, g, mg/dL, mmol/L, U/h stay Latin

## Added 2026-11-07 (ConfigBuilder rewrite, AAPSClient iOS)
websockets = 웹소켓 (approved @235007, @233881 still have English "websockets" - fix)
calibration pair = 짝; slope / offset = 기울기 / 오프셋; Config Builder (old name) = 구성 관리자; category = 분류
macOS labels: 완료, 개인정보 보호 및 보안, 그래도 열기 (English in brackets); GitHub labels stay English
Markdown italics: put a space after the closing `_` before a Korean particle (_x_ 값을)
