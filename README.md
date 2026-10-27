# 로드 도슨트 · Road Docent

**목적지를 찍는 순간, 내 차에 그 지역 전문 가이드가 탑승한다.**
자동차의 목적지는 예술작품이 되어 간다.

현대자동차그룹 차량용 앱 개발 공모전 (Code your App into Pleos App Market) 출품작.
Pleos Connect 위에서 동작하는 차량용 오디오 가이드 앱입니다. 운전자는 묻지 않습니다. 차가 먼저 말합니다.

## 한 줄 소개
내비게이션 목적지·경로·남은 시간·시동·좌석 신호를 읽어, 가는 길의 역사와 길목 명소, 도착지에서 할 일, 다시 차에 탔을 때의 다음 행선지를 **타이밍에 맞춰 음성으로** 들려주는 앱.

## 로그인 · 약관 (테스트 계정)
첫 화면은 로그인입니다. 테스트 계정: **tester / pleos2026!** · judge / hyundai2026 · family / family2026 (비밀번호는 앱 안에 SHA-256으로만 저장). 첫 로그인 때 이용약관·개인정보 처리방침(`assets/terms_ko.md`)에 두 항목을 체크해야 넘어가고, 약관 버전이 바뀌면 다시 묻습니다. Google 로그인은 Credential Manager로 구현돼 있으며 `gradle.properties`에 `GOOGLE_WEB_CLIENT_ID=<Google Cloud 웹 클라이언트 ID>`를 넣고 빌드하면 켜집니다(없으면 버튼이 설정 안내를 띄움). 로그아웃은 연결 화면에서.

## 키즈 모드 퀴즈
아이가 탔을 때(뒷좌석 착좌 또는 설정)는 이야기 두 편마다 **창밖 퀴즈**가 끼어듭니다: 질문 + 보기 3개를 읽어주고, 화면의 보기 버튼을 누르거나 "2번"이라고 말하면 채점·칭찬·스티커. 가이드마다 3~4개(`kidsQuizzes`), 동승자 폰 화면에도 같은 카드가 뜹니다. 데모 바 ❓ 퀴즈로 바로 불러낼 수 있습니다.

## 실행 방법

### A. Pleos Connect AVD / 실차 (실제 SDK 연동)
1. Pleos Playground 가이드대로 Android Studio에 **Pleos Connect v2.1.0 시스템 이미지(API 34)** 를 추가하고 AVD를 만듭니다.
2. `apk/RoadDocent_로드도슨트_release.apk` 를 설치하거나, 이 저장소를 열어 `Run` 합니다.
3. 앱 첫 화면에서 **"Pleos 차량 연결"** 을 선택합니다.
4. 차량 내비에서 목적지를 설정하면(강릉 안목해변 / 현대모터스튜디오 고양 / 영월 청령포 / 경복궁) 가이드가 시작됩니다.
   - NaviHelper SDK: 목적지·남은 거리/시간·현재 위치·도착 이벤트·목적지 변경
   - Vehicle SDK: 기어 P/D(재탑승 감지), 2열 좌석 점유(키즈 모드), 트립 거리/시간(여행 일지)

### B. 시뮬레이션 (어떤 Android 기기/에뮬레이터)
1. APK 설치 후 **"시뮬레이션 · 데모"** 선택.
2. 목적지 4개 중 하나를 고르면 서울(또는 인천공항)에서 출발하는 주행이 압축 시간으로 재생됩니다.
3. 하단 DEMO 바: `EN/KO` 언어, `키즈` 모드, `⏩ 빨리감기`, `🚗 정체`, `🏁 도착·P`, `▶ D (재출발)`, `🧒 뒷좌석`, `🗺 일지`.

## 테스트 환경
- Android Studio Ladybug 이상, JDK 17, Gradle 8.11 (wrapper 포함)
- compileSdk 35 / minSdk 33 / targetSdk 35
- Pleos SDK: `ai.pleos.playground:NaviHelper:3.0.5`, `ai.pleos.playground:Vehicle:2.3.2`
  (`https://nexus-playground.pleos.ai/repository/maven-releases/` 에서 자동 다운로드)
- TTS: Android 플랫폼 TTS. 한국어·영어 음성 데이터가 설치된 기기에서 음성이 나옵니다.
  Pleos TextToSpeech SDK가 "Request Required" 상태가 되면 `speech/Speaker.kt` 구현만 교체합니다.
- 음성 명령: Gleo Interface 연동 전까지 화면 버튼(“넘겨”, “더 자세히”, “저장해”, “거기로 가자”)으로 동일 명령을 실행합니다.

## 시연 자료
- 시연 영상: `docs/로드도슨트_시연영상.mp4` (1분 42초, 전체 흐름 + 실제 APK 실행 화면)
- 실제 APK 실행 화면: `docs/실행화면_*.png` (Android 14 x86_64 에뮬레이터, Automotive 1024p)
- 화면 시안: `docs/로드도슨트_화면시안.pdf`

## APK · 릴리즈 배포
- 저장소 안: `apk/RoadDocent_로드도슨트_release.apk` (release 서명, 데모용 키스토어 `keystore/`) · 빌드: `./gradlew :app:assembleRelease`
- **GitHub Releases 자동 배포** (`.github/workflows/release.yml`): `main`에 푸시될 때마다 테스트·빌드 후 Actions 아티팩트로 APK를 올리고, `v*` 태그를 푸시하거나 Actions 탭 → "Build & Release APK" → **Run workflow**에 버전(예: `v0.3.0`)을 넣으면 그 버전의 Release가 만들어지고 APK가 첨부됩니다. 폰에서는 Releases 페이지에서 바로 내려받으면 됩니다.

## 구조
```
app/src/main/java/kr/roaddocent
├─ core/        Models.kt          — Guide / Episode / Trigger / Place(onArrival) / CreatorCourse
├─ vehicle/     Signals.kt         — NavSignals, VehicleSignals 인터페이스
│               PleosNavSignals    — NaviHelper SDK 어댑터
│               PleosVehicleSignals— Vehicle SDK 어댑터 (기어·좌석·트립)
│               SimulatedTrip      — 데모용 주행 시뮬레이터 (같은 인터페이스)
├─ speech/      Speaker.kt         — TTS 추상화 (Android TTS, Pleos TTS 교체 지점)
├─ content/     GuideRepository    — assets/guides/*.json 로드, 목적지명으로 가이드 선택
├─ engine/      DocentEngine       — 언제·무엇을 말할지 결정하는 상태 머신
├─ log/         TripLog, TripHistory — 오늘의 일지(경로·체류·총계) + 기기 내 다일 누적(체류 시간 데이터)
└─ ui/          Compose 화면 5종 + 설정 + Voice Family + 데모 컨트롤
app/src/main/assets/guides/        — 목적지별 가이드 JSON 5종 (한/영/키즈, 퀴즈, 출처, 테마 태그)
app/src/main/assets/overlays/      — 오버레이 가이드: car_onboarding.json (새 차 알아가기)
app/src/test/                      — 콘텐츠 검증 테스트 (JSON 파싱·id 중복·연결 가이드·퀴즈 이중언어) `./gradlew testReleaseUnitTest`
```

## 가이드 콘텐츠: "운전 시간이 배우는 시간이 되도록"
에피소드마다 `kind`가 있습니다 — `story`(이야기) · `fact`(알고 보면) · `quiz`(퀴즈: 질문 → 8초 → 정답, "더 자세히"로 즉시 정답) · `phrase`(한국어 한마디) · `food` · `tip`. 퀴즈는 화면 카드로도 보이고 여행 일지에 집계됩니다.
가이드 5종: 서울→강릉 · 현대모터스튜디오 고양(현대차 역사) · 영월 '왕과 사는 남자' 단종 코스 · 인천공항→경복궁(광화문·남대문·흑백요리사 맛집) · **경복궁→국립중앙박물관(용산)** — 경복궁에서 재탑승하면 첫 추천이 박물관이고, "거기로 가자"에 가이드가 끊기지 않고 이어집니다(`Place.guideId` 연결).
테마 태그(`themes`: history · art · nature · thrill · food · family · car)로 첫 화면에서 골라 보고, "다음은 어디?" 추천도 테마를 우선합니다.

## 도슨트 앱 벤치마크에서 가져온 것
GuideAlong·Shaka Guide·Autio(해외 드라이브 오디오 투어), 한국관광공사 '오디', 국립중앙박물관 전시안내 앱, VoiceMap·Cuseum의 오디오 가이드 작성 원칙을 참고해 다음을 넣었습니다.
- **층위 콘텐츠** `deep`: "더 자세히"는 같은 이야기를 반복하지 않고 한 단계 깊은 해설(연도·수치·출처)을 읽습니다 (스미스소니언식 layered content).
- **예고(cliffhanger)** `teaser`: 에피소드 끝에 다음 길목을 한 문장으로 예고해 정류장 사이를 잇습니다 (VoiceMap).
- **정적 방지** `Trigger.AfterSilence`: 주행 중 5~6분 조용하면 짧은 '알고 보면'을 넣어 긴 침묵을 없앱니다 (VoiceMap "avoid long silences").
- **대화형 해설** `kind: dialogue`: 상궁·내관 두 인물의 대화로 풀어내는 에피소드 (관광공사 '오디' 경복궁 해설 형식).
- **출발 전 알아두기** `knowBeforeYouGo`: 운영시간·요금·주차·추천 출발 시각·챙길 것·예산 (Shaka Guide "Know Before You Go", GuideAlong "Essential Tips").
- **현장 앱 인계** `handoff`: 주차하면 궁궐은 '오디', 박물관은 공식 전시안내 앱(실내 길안내·5개 국어)으로 버튼 한 번에 넘기고, 다시 타면 차 안 가이드가 이어받습니다.
- 에피소드 길이는 35~85초(1~2분 권장, Cuseum), 짧은 문장·구어체, 정체 때만 긴 이야기.

## 2차 보강 (2026-10-23)
- **관광 스토리텔링 DB 파이프라인** `tools/storydb_to_bank.py`: 한국관광공사 공개 대본 1,624건(공공데이터포털 3041768, 이용허락범위 제한 없음) → `assets/storybank/kto_storytelling.json`. 손으로 쓴 `deep`이 없는 에피소드에서 "더 자세히"를 말하면 지명 매칭으로 공사 해설을 읽고 출처를 표시합니다 (`content/StoryBank.kt`).
- **지금 창밖 모드** (데모 바 🪟): 목적지 없이 달릴 때 모든 가이드의 길목 에피소드가 살아 있어 지나는 곳마다 이야기가 나오고, 조용하면 필러가 끼어듭니다 (`DocentEngine.startFreeRide`).
- **동승자 폰 연동** (📱): 차가 작은 HTTP 서버(8765)를 띄우고 QR을 보여줍니다. 뒷좌석 폰이 한 번 찍으면 지금 이야기·퀴즈 카드·스티커·오늘의 한국어가 폰에서 따라옵니다 (`share/CompanionServer.kt`, ZXing QR). 차 화면은 운전자용 최소 표시.
- **오늘의 여행 공유 카드** (일지 화면 📤): 경로·체류 원·총계·한 줄 요약을 1080×1350 이미지로 그려 공유 시트로 보냅니다 (`share/ShareCard.kt`).
- **EV 충전 트리거** (🔌): `VehicleState.charging` → 충전 예절 에피소드 + 남은 이야기 중 가장 긴 것을 몰아줍니다.
- **UX**: 주행 중(기어 D·5km/h 이상)에는 제목·파형·진행 점만 보이고 본문은 P에서 펼침 · 말 시작 시 패널 테두리 글로우 · 상황별 음성 힌트(퀴즈 중 "정답 알려줘", 브리핑 중 "예약해", 재탑승 "첫 번째로 가자") · 첫 실행 3탭 온보딩(언어→탑승자→테마/새 차) · 본문 최소 22sp + 글자 크게 옵션 · 키즈 퀴즈 정답 효과음 + 스티커.

## v0.4.0 디자인 개편 (10/27 오후)
- **밝은 디자인 시스템**: 모래색 배경 + 흰 카드(그림자) + 현대 네이비/스카이, 픽토그램 타일, 히어로 밴드. 옆으로 미는 목록 전부 제거(타일 그리드·줄바꿈 칩), 버튼은 잘리지 않게 줄바꿈 또는 균등 아이콘 바.
- **하단 바 제거**: 상단 ⋮ 메뉴(일지·동승자·키즈·언어·설정·시연 컨트롤·로그아웃)로 옮기고, 시연 버튼은 "🎬 시연 컨트롤" 창 안에.
- **도착**: 할 일을 다 읽지 않고 "하나만 기억하세요" 한 줄만 말하고, 나머지는 아이콘 카드(🅿️ 주차 · 🎫 입장 · 🗺️ 순서 · ⭐ 꼭 볼 것 · 🧒 아이와 · 🙏 예절…) — 누르면 펼치고 읽어 줌.
- **말 끊김 수정(실기기 원인)**: Android TTS가 "말하는 중"을 늦게 알려 같은 순간의 두 트리거가 서로를 자르던 문제 → 말하기 시작 즉시 표시. 도착·브리핑을 포함한 모든 자동 안내가 지금 이야기 뒤에 줄을 섭니다.
- **키즈 모드 존댓말**: 아이용 해설·퀴즈 77곳을 해요체로.
- **화면 렌더링 테스트**: Robolectric + Roborazzi로 12개 화면을 411×891dp로 그려 확인(`./gradlew testDebugUnitTest` → `app/build/shots/`, 사본 `docs/screens/`).

## 10/27 폰 테스트 피드백 반영
- **겹침** 수정: 상태바·시스템 내비바 안쪽으로만 그리고(safeDrawing), 칩·버튼은 글자가 세로로 쪼개지지 않게 한 줄 고정 + 가로 스크롤.
- **흐름**: 이야기가 끊기지 않습니다 — 길목·정체 트리거는 지금 읽는 이야기가 끝난 뒤 1.2초 쉬고 이어집니다(도착 브리핑·재탑승만 끼어듦). 시뮬레이터는 기본이 **시연 모드**: 시간이 빨리 흐르다가 이야기가 시작되면 멈추고, 끝나면 다시 흐릅니다. 목적지를 고르면 3초간 "내비 목적지 설정" 장면(현재 위치 → 목적지, 경로 지도, 이야기 지점)이 먼저 나오고, 상단에 "○○ 부근 → 경복궁 37분"으로 현재 위치가 표시됩니다.
- **한국인 / 외국인 콘텐츠 분리**: 에피소드에 `text`(한국 거주자용)와 `foreignText`(방문객용)가 따로 있고, `forAudiences`로 방문객 전용 편(한국 10분 브리핑, 한국어 한마디, 한글 원리)은 한국인 가족에겐 나오지 않습니다. 경복궁 가이드는 한국인용으로 다시 썼습니다(품계석 24개, 숭례문 세로 현판, 12·23 분수, 2023 현판·월대 복원 등). 키즈 텍스트도 한국 아이 기준.
- **그림**: 장소 카테고리 픽토그램, 이야기 종류 아이콘, 경로 지도(출발·목적지·이야기 점·현재 위치), 목적지 카드. 사진은 `assets/images/`에 넣고 `poiId`에 파일명을 적으면 자동 표시 — 저작권 문제 없는 공공누리 1유형·퍼블릭 도메인 사진만.

## 벤치마크에서 더 가져온 것 (10/26)
- **이어서 듣기** (GuideAlong "resumes automatically"): 앱을 껐다 켜도 첫 화면에 "▶ 이어서 듣기 · 경복궁 (12분 전)"이 뜹니다.
- **지금 주변 이야기 듣기** (Autio "play a story near you"): 목적지 없이도 현재 위치 5km 안에서 가장 가까운 길목 이야기를 바로 재생합니다.
- **👍 👎** (Autio 평가): 에피소드마다 평가를 일지에 남겨 추천 가중치·오류 신고 데이터로 씁니다.

## 새 차 알아가기 (오버레이)
설정 또는 데모 바 **🚗 새 차**를 켜면 어떤 목적지든 센서가 실제로 작동하는 순간에 하나씩 설명합니다 — 첫 출발(센서 지도) · 뒷좌석 착좌(착좌 센서·후석 승객 알림) · 60km/h(전방 카메라·차로 유지) · 100km/h(레이더·스마트 크루즈) · 5km(TPMS·타이어 관리) · 15km(정비 주기: 엔진오일/감속기 오일, 에어컨 필터, 와이퍼, 브레이크 패드, 12V 배터리, 냉각수) · 정체(회생제동) · 주차(초음파 센서·하차 체크). `Trigger.VehicleEvent`, Vehicle SDK의 GearSelection·SeatOccupancy·Odometer로 구동. 실차에서는 누적 1,000km까지 자동.

## 엔진 규칙 (DocentEngine)
| 신호 | 행동 |
| --- | --- |
| 목적지 설정 | 인사 + 도시 분위기 + 출발 에피소드 큐 |
| 특정 지점 반경 진입 | 길목 해설 (평창, 대관령, 남대문, 광화문 …) |
| 속도 20km/h 미만 60초 지속 | 정체: 긴 심층 에피소드 |
| 휴게소 5km 전 | 명물·동선 안내, “들를까요?” |
| 남은 시간 20분 미만 | 도착 전 브리핑 + 예약 전화 제안 |
| 도착 + 기어 P | 주차: “여기서 할 일” 순서대로 음성 + 화면 목록, 체류 타이머 시작 |
| 기어 P → D | “다음은 어디?” 제안 (30분 미만 체류면 “기대와 달랐나요?”) |
| 속도 100km/h 초과 | 문장 경계에서 발화 일시정지, 안전해지면 재개 |
| 2열 좌석 점유 | 키즈 모드 전환 (역사 이야기·창밖 퀴즈·인사 연습) |
| 재탑승 추천에 `guideId`가 있는 곳으로 "거기로 가자" | 다음 가이드로 교체해 즉시 출발 에피소드 시작 (가이드 연속성) |
| 퀴즈 에피소드 | 이야기 + 질문 → 8초 대기(자동 진행 멈춤) → 정답 낭독, 일지에 집계 |
| `VehicleEvent` (새 차 오버레이) | first_drive · rear_seat · speed_60 · speed_100 · trip_5km · trip_15km · park 각 1회 |

## 음성 명령 (Gleo Interface + 기기 음성인식)
- `voice/VoiceCommands.kt`: 명령 8종 (`SKIP_EPISODE`, `TELL_MORE`, `SAVE_PLACE`, `QUIET`, `WHERE_NEXT`, `GO_THERE`, `RESERVE`, `SHOW_TRAVEL_LOG`)을 하나의 `CommandBus`로 모읍니다.
- **Gleo (Pleos Connect)**: `assets/gleo-schema.json`을 Playground에 등록하면 Gleo AI가 발화를 함수로 해석해 브로드캐스트 인텐트로 보냅니다. `GleoReceiver`가 받아 실행하고 `APP_ACTION_RESPONSE`로 응답합니다. (스키마 필드는 제출 전 App Schema Writing Guide로 재확인)
- **일반 기기**: 상단 🎤 버튼 → Android 음성인식 → 키워드 매칭("넘겨", "더 자세히", "거기로 가자", "두 번째로 가자", "다른 데 없어?", "예약해", "일지 보여줘" / 영어 동일).
- 에피소드가 끝나면 2.5초 뒤 다음 이야기가 자동으로 이어집니다(안전 일시정지 중엔 대기).

## 설정 (⚙)
언어 · 탑승자(일반/아이/외국인) · 아이 나이(난이도) · 여행 테마 · 새 차 알아가기 켜기/끄기 · 안전 일시정지 속도(80/100/110) · 여행 일지 저장 여부(다일 누적, 기기에만) · 음성 복제 제공자와 API 키(기기에만 저장) · 음성 명령 소스(Gleo/기기).

## 가족 목소리 가이드 (Voice Family)
엄마·아빠·아이가 앱에서 샘플 문장 15개(약 2분)를 녹음하면, 음성 복제 TTS가 그 목소리로 모든 에피소드를 읽어줍니다.
- 화면: ⚙ 설정 → 가족 목소리 (또는 주차 화면의 **🎙 가족 목소리 녹음**) → 누가 녹음하는지 선택 → 동의 → 15문장 녹음 → 목소리 만들기
- 코드: `speech/VoiceFamily.kt` — `VoiceRecorder`(녹음), `VoiceCloneClient`(복제 API, 제공자 교체 가능), `CloudVoiceSpeaker`(복제 음성 재생, 키가 없으면 기기 TTS로 자동 폴백)
- API 키는 APK에 넣지 않습니다. `DocentViewModel`의 `VoiceCloneClient(apiKey = ...)`에 설정 화면/BuildConfig로 주입하세요. (ElevenLabs 기준 엔드포인트, Typecast·Azure Personal Voice로 교체 가능)
- 안전장치: 첫 문장은 본인 동의 낭독(도용 방지), 14세 미만은 보호자 동의 체크 필수, 모델 생성 후 원본 녹음 삭제, 복제 음성은 가이드 대본에만 사용, 언제든 삭제

## 크리에이터 코스 (실제 영상 기준)
각 가이드 JSON의 `creatorCourses`에는 실제로 공개된 여행 영상의 코스가 들어 있습니다 (2026-10-22 기준 YouTube에서 제목·업로드일·설명의 방문 순서를 확인한 것만 수록).
강릉: 히제이, 유일랜드, 할명수, 한국관광공사TV · 영월: 걸어서토요일, 동네형, 가빈, 강쥐G · 경복궁: GAMJA IN SEOUL, livewithshannon, 너도 가봤으면 해, Wanderlust Wellman · 고양: 칼쳐맨(박준형), 너도 가봤으면 해, 경기관광 · 국립중앙박물관: 공식 전시 안내.
앱은 도착 전 브리핑과 재탑승 화면에서 이 코스를 보여 주고, 각 장소에서 해당 크리에이터의 한마디를 인용합니다. 상용 서비스에서는 크리에이터 등록·제휴로 확장합니다.

## 콘텐츠 출처
가이드 JSON의 각 에피소드에 `source` 가 있고, 가이드 끝에 `dataSources` 목록이 있습니다.
문화체육관광부·국가유산청, 한국관광공사 TourAPI, 지자체 관광 정보, 현대자동차그룹 공식 연혁, 영화·TV 촬영지 공개 자료, 크리에이터 코스.
흑백요리사 셰프 식당 등 상호·주소는 제출 전 최신 정보로 확인합니다.

## 라이선스
공모전 출품용. 소스 코드 MIT. 가이드 텍스트 © Road Docent 팀, 인용 출처는 각 항목에 표기.
