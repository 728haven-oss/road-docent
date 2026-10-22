# 로드 도슨트 · Road Docent

**목적지를 찍는 순간, 내 차에 그 지역 전문 가이드가 탑승한다.**
자동차의 목적지는 예술작품이 되어 간다.

현대자동차그룹 차량용 앱 개발 공모전 (Code your App into Pleos App Market) 출품작.
Pleos Connect 위에서 동작하는 차량용 오디오 가이드 앱입니다. 운전자는 묻지 않습니다. 차가 먼저 말합니다.

## 한 줄 소개
내비게이션 목적지·경로·남은 시간·시동·좌석 신호를 읽어, 가는 길의 역사와 길목 명소, 도착지에서 할 일, 다시 차에 탔을 때의 다음 행선지를 **타이밍에 맞춰 음성으로** 들려주는 앱.

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

## APK
- 경로: `apk/RoadDocent_로드도슨트_release.apk` (release 서명, 데모용 키스토어 `keystore/`)
- 빌드: `./gradlew :app:assembleRelease`

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
├─ log/         TripLog            — 여행 일지(경로·체류·총계)
└─ ui/          Compose 화면 5종 + 데모 컨트롤
app/src/main/assets/guides/        — 목적지별 가이드 JSON (한/영/키즈, 출처 포함)
```

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

## 콘텐츠 출처
가이드 JSON의 각 에피소드에 `source` 가 있고, 가이드 끝에 `dataSources` 목록이 있습니다.
문화체육관광부·국가유산청, 한국관광공사 TourAPI, 지자체 관광 정보, 현대자동차그룹 공식 연혁, 영화·TV 촬영지 공개 자료, 크리에이터 코스.
흑백요리사 셰프 식당 등 상호·주소는 제출 전 최신 정보로 확인합니다.

## 라이선스
공모전 출품용. 소스 코드 MIT. 가이드 텍스트 © Road Docent 팀, 인용 출처는 각 항목에 표기.
