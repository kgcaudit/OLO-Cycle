# OLO Cycle — 작업 지침

사용자와의 모든 대화는 **우리말**로 한다.

## 화면 작업 순서(모든 UI 작업에 적용)

**구상안 이미지 → 의사결정·조정 → 확정 → 코딩 → 대조.** 코딩 뒤에 디자인을 바꾸면 손실이 크므로 순서를 지킨다.

1. **구상안은 실제 디자인 시스템 부품(앱의 `OloTheme`·실제 Composable)으로 그린다.** 손그림·HTML 금지 —
   구상안과 완성 화면이 달라지지 않게. 방법은 아래 "스크린샷 하니스".
   - **구상안 테스트 코드는 커밋하지 않는다.** `app/src/test/java/com/kgcaudit/olocycle/proposal/`
     와 `app/build/proposals/`는 `.gitignore` 처리되어 있다(작업공간 보관).
2. 선택지가 있으면 **가안·나안을 나란히 붙인 비교판 이미지**로 보이고 권고안에 "(권고)"를 붙인다.
   정할 것은 ①②③ 번호로 묻고 표로 정리한다.
3. 사용자가 고르거나 고치면 구상안을 다시 그리고, **"권고안대로"·"진행해 줘" 같은 확정** 뒤에만 코딩한다.
4. 완성 후 앱 시험 스크린샷을 **확정 구상안과 나란히 붙인 대조 이미지**로 보여 차이가 없는지 확인한다.
   다르면 고치거나 왜 다른지 말한다.
5. 새 기능에는 **돌연변이 확인**(일부러 망가뜨린 코드를 시험이 잡는지)도 보고한다.

## 스크린샷 하니스(구상안·대조 렌더)

Robolectric NATIVE 로 실제 Compose 를 PNG 로 저장한다. 이 환경에서 동작 검증됨.
- 러너: `@RunWith(RobolectricTestRunner::class)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)`,
  `@Config(qualifiers = "w393dp-h851dp-xhdpi", sdk = [34])`, `createAndroidComposeRule<ComponentActivity>()`.
- `setContent { OloTheme { … } }` → `waitForIdle()` → **`decorView` 를 소프트웨어 `Canvas`에 직접 그려**
  PNG 저장(`captureToImage()`는 Robolectric 에서 forceRedraw 타임아웃이 나므로 쓰지 않는다).
- 실행: `./gradlew --no-daemon testDebugUnitTest --tests "com.kgcaudit.olocycle.proposal.*"`,
  결과 PNG 는 `app/build/proposals/` 에 생성. 테스트 파일과 산출물은 커밋하지 않는다.
- 실제 화면(private Composable)을 그리려면 대상 화면의 부품을 그 작업에서 `internal` 로 열어 테스트에서 호출한다.

## 앱 개요

- 로컬 전용·프라이버시 우선 생리주기 트래커(다중 프로필). **인터넷 권한 없음**(권한은 `USE_BIOMETRIC` 하나).
- Kotlin · Jetpack Compose(Material3) · Room(KSP) · core library desugaring(java.time, minSdk 24).
- 디자인: OLO 디자인 시스템(클레이 #B95B3B + 아이보리 웜톤). 색 토큰은 `ui/theme/Color.kt`(`OloColors`).
  색의 축은 2개 — **활성 구성원색 = 화면 강조**(히어로·탭·FAB·오늘 등), **주기 의미색**(생리 로즈 / 가임·배란 세이지 틸).
- 예측은 참고용 추정치(비의료). 예측 로직 `cycle/CyclePredictor.kt`(단위 테스트 `app/src/test/.../cycle/`).

## 빌드·배포 관습

- 릴리스 절차: 구현 → `./gradlew --no-daemon -q testDebugUnitTest lintDebug assembleDebug`(lint 오류 0) →
  버전 올림(`app/build.gradle.kts`) → 커밋 → `main` 푸시 → APK 를 사용자에게 전달.
- APK 사본은 `olo-cycle-<버전>-debug.apk` 로 저장소 루트에 둔다(디버그 확인용).
- 커밋 메시지·PR·코드 어디에도 모델 식별자를 넣지 않는다.
