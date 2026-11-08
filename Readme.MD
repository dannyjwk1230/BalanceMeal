# BalanceMeal 팀 연결 안내

## 출력 파트(내역·상세·분석 화면) 통합 및 병합 (이번 변경)

- **내용**: GitHub PR #1의 출력 파트 화면 구현을 현재 적응형 2-pane 및 기록 삭제 기능과 충돌 없이 로컬 병합 완료.
- **주요 통합 사항**:
  - `HistoryScreen`: 식사 내역 카드 목록, 총 열량 및 끼니 수 요약 카드, 다중 사진 썸네일 지원, 카드 클릭 시 상세 이동 연동
  - `MealDetailScreen`: 날짜/시간/끼니, 다중 사진(단일 사진 및 가로 스크롤 다중 사진), 음식별 섭취량 및 영양소, 식사 전체 총합산 영양소 카드 표시
  - `AnalysisScreen` & `MealAnalysis`: 최근 1개월(30일)간 총 합산 열량 카드 및 끼니별(조식/중식/석식/간식) 섭취량 비율 바 차트 구현
  - `MealViewModel`: `analysisState` StateFlow 추가 (30일 이내 기록의 끼니별 영양소 및 칼로리 실시간 집계)
  - `HistoryScreenTest`: 내역 화면 렌더링, 요약 카드 표시 및 아이템 클릭 계측 테스트 추가
  - `MealViewModelTest`: 최근 30일 제외 필터링 및 끼니별 영양소 집계 단위 테스트 2건 추가
- **검증**:
  - `:app:testDebugUnitTest`: 단위 테스트 **70개 전체 통과**
  - `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`: 빌드 및 컴파일 성공

## 기존 식사 항목 전체 삭제 시 기록 삭제 처리

- **요청**: 저장된 식사 항목이 있다가 모두 삭제하고 변경사항을 저장할 때, 내용이 없어서 저장 불가 오류가 발생하는 대신 해당 식사 기록을 삭제 처리.
- **동작**:
  - 기존에 저장된 기록(`editingRecordId != null`)의 모든 음식 항목을 삭제한 경우(`draft.items.isEmpty()`), 하단 버튼이 오류 색상(error)의 **"기록 삭제"** 버튼으로 전환됩니다.
  - 버튼 클릭 시 `deleteMeal(recordId)`가 호출되어 `LocalMealRepository.deleteMeal`을 통해 파일에서 원자적으로 해당 기록을 삭제합니다.
  - 저장 실패가 아닌 정상 삭제(`OperationState.Success`)로 처리되며, "식사 기록을 삭제했습니다." 안내가 표시되고 해당 끼니 초안이 깨끗한 기본 상태로 초기화됩니다(`editingRecordId = null`).
  - 신규 작성 중(`editingRecordId == null`)에 항목이 없는 경우에는 기존처럼 "음식을 추가해 주세요." 검증 안내를 유지합니다.
  - 삭제 실패 시 재시도(`MealEvent.RetrySave`)를 통해 `pendingDeleteId`를 다시 삭제 시도할 수 있습니다.
- **변경 파일**:
  - `data/MealRepository.kt`: `suspend fun deleteMeal(recordId: String)` 계약 추가
  - `data/LocalMealRepository.kt`: `deleteMeal(recordId)` 원자적 삭제 구현
  - `ui/meal/MealEvent.kt`: `MealEvent.DeleteCurrentMeal` 추가
  - `ui/meal/MealViewModel.kt`: 항목 전체 삭제 + 기존 기록 감지 시 삭제 분기, `deleteMeal()` 구현, 재시도 지원
  - `ui/screens/record/RecordScreen.kt`: 삭제 모드 감지(`isDeletionMode`), "기록 삭제" 버튼 표시 및 스타일 적용, 삭제 성공 메시지
  - `test/java/com/example/balancemeal/ui/meal/MealViewModelTest.kt`: 전체 항목 삭제 시 기록 삭제 및 재시도 단위 테스트 3건 추가
  - `test/java/com/example/balancemeal/data/LocalMealRepositoryTest.kt`: `deleteMeal` 원자적 파일 삭제 및 비존재 ID 무시 단위 테스트 2건 추가
- **검증**:
  - `:app:testDebugUnitTest`: 단위 테스트 **68개 전체 통과** (기존 63개 + 신규 5개).
  - `:app:assembleDebug`, `:app:compileDebugAndroidTestKotlin`: 빌드 성공.

## 음식 추가·변경 화면 적응형 2-pane 구성

### 레이아웃 전환 기준

- 기기 종류·방향이 아닌 실제 창 너비와 두 패널의 최소 사용 너비를 기준으로 판단합니다.
- `MIN_EDIT_PANE_WIDTH = 320dp`, `MIN_FOOD_PANE_WIDTH = 280dp`, `PANE_GAP = 16dp`
- 창 너비 ≥ 616dp(네비게이션 레일 포함 시 ~672dp)이면 2-pane, 미만이면 단일 화면입니다.
- 실제 창 너비는 `BoxWithConstraints.maxWidth`로 측정합니다.

### 좁은 화면

- 기존과 동일하게 기록 화면에서 `FoodSelection` NavKey로 Navigation 이동합니다.
- 복귀 시 편집 내용(날짜·끼니·시간·음식·사진)과 스크롤 위치(`rememberScrollState`)를 유지합니다.

### 넓은 화면

- 왼쪽에 `RecordEditPane`(식사 기록 편집), 오른쪽에 `FoodSelectionPanel`(음식 검색·선택)을 배치합니다.
- 각 패널은 독립적으로 스크롤합니다.
- 현재 오른쪽 패널에서 선택 중인 항목을 왼쪽 `MealItemRow`에서 주 색상 2dp 테두리로 강조합니다.
- 선택 확정(`onSelectFood`) 시 `MealEvent.SelectFood`로 ViewModel 상태를 즉시 갱신하고 패널을 닫습니다. 왼쪽 목록과 영양 합계가 즉시 반영됩니다.
- 취소(`onCancel`) 시 기존 음식 목록을 그대로 유지합니다.
- 변경 대상 항목은 `FoodPickerRequest(itemId, mealType, date)`의 안정적인 UUID `itemId`로 구분합니다.

### 레이아웃 전환 시 상태 보존

- `foodPickerRequest: FoodPickerRequest?`와 `foodQuery: String`을 `rememberSaveable(stateSaver = listSaver(...))`로 보존합니다.
- 좁은 → 넓은 전환: `BalanceMealApp`의 `LaunchedEffect(snapshotFlow)`가 `FoodSelection` NavKey를 백스택에서 자동으로 팝합니다. 중복 추가를 방지합니다.
- 넓은 → 좁은 전환: `foodPickerRequest`가 null로 초기화되고 패널은 닫힙니다. 선택 중이던 음식 목록은 ViewModel의 `draftsByKey`에 그대로 보존됩니다.
- 회전·창 크기 변경·분할 화면 전환에서도 선택 날짜·끼니·시간·사진·음식 목록·검색어·변경 대상이 유지됩니다.

### 시스템 영역·키보드 가림 방지

- 편집 패널에 `Modifier.imePadding()` 적용으로 소프트 키보드 위로 콘텐츠가 올라옵니다.
- 하단에 `Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))`를 추가해 저장 버튼이 내비게이션 바에 가려지지 않습니다.
- 최상위 `safeDrawingPadding()`은 기존 `BalanceMealApp`에서 유지됩니다.

### 변경 파일

- `ui/meal/FoodPickerRequest.kt` (신규): 패널 선택 요청 모델
- `ui/screens/food/FoodSelectionPanel.kt` (신규): 패널 전용 음식 검색·선택 Composable
- `ui/screens/record/RecordScreen.kt`: 2-pane 분기 로직, `RecordEditPane` 분리, 상태 보존
- `ui/screens/record/MealItemRow.kt`: `isHighlighted` 파라미터 추가
- `navigation/BalanceMealApp.kt`: 레이아웃 전환 시 `FoodSelection` 백스택 자동 팝

### 이번 단계 검증

- `:app:compileDebugKotlin` 성공.
- `:app:assembleDebug` 성공.
- `:app:testDebugUnitTest` 63개 통과.
- `:app:compileDebugAndroidTestKotlin` 성공.
- 에뮬레이터·실기기 실행, 창 크기 직접 조작, 실제 음식 선택 흐름은 이번 단계에서 실행하지 않았습니다.
- 기존 계측 테스트(`food-search` 태그)는 좁은 화면(`FoodSelectionScreen`)에서만 동작하며, 넓은 화면 패널은 `food-search-panel` 태그를 사용합니다.



## 영양 정보 표시 개선

요청: 음식별 정보와 식사 합계를 공통 카드로 표시하고, 사용 가능한 너비에 맞춰 배치.

결과:
- NutrientsDisplay의 순서는 탄수화물 / 지방 / 단백질 / 열량입니다. g / kcal 단위와 이름을 항상 표시합니다.
- MaterialTheme의 색상과 큰 굵은 수치로 표시하며 MealNutritionSummary는 합계 제목·배경·수치 크기를 더 강조합니다.
- 실제 컴포넌트 내부 너비 기준으로 2열 / 4열을 선택합니다. 큰 글꼴이나 매우 좁은 영역은 1열로 재배치합니다.
- 음식 추가·교체·삭제·양 변경에 따른 기존 ViewModel 파생값을 그대로 표시합니다. 계산식·원본 kcal·중간 반올림 없음·표시 시 소수 첫째 자리·g/ml 입력 처리는 유지합니다.
- 잘못된 입력이면 이전 행 수치/합계를 숨기고 기존 안내를 표시합니다.
- 변경: NutrientsDisplay.kt, MealItemRow.kt, RecordScreen.kt, NutritionDisplayPreviews.kt, MealViewModelTest.kt, SharedMealViewModelInstrumentedTest.kt, Readme.MD.
- 검증: assembleDebug와 JVM 63개 통과, compileDebugAndroidTestKotlin 성공. 유효→잘못된 섭취량 전환 시 이전 수치 제거를 검증했습니다.
- Compose Preview: 320×900 세로(2×2), 800×400 가로(4개), 1200×800 태블릿(4개), 태블릿 내부 340dp 제한 카드(2×2)에서 보이는 영양 항목의 잘림·겹침 없음을 확인했습니다. 세로 스크롤 영역의 화면 밖 내용은 스크롤로 접근하는 구조입니다.
- 실제 에뮬레이터·실기기 실행/회전·스크롤 조작은 이번 단계에서 실행하지 않았습니다. 프리뷰 확인과 기기 실행 검증은 구분합니다.


## 사진 선택 입력·다중 첨부 (이번 변경)

- MealDraft / MealRecord의 기준 필드는 `photoReferences: List<String>`입니다. 사진 없음은 빈 목록입니다.
- 공유 UI State는 날짜+끼니별 draftsByKey의 사진 목록을 사용합니다. 화면별 사진 저장소를 만들지 않습니다.
- 사진은 선택 입력입니다. 음식 1개 이상·모든 행의 음식/유한한 양수·현재보다 미래가 아닌 시간 등 기존 검증은 유지합니다.
- `AddPhotos(references, mealType, date)`는 선택기를 연 날짜·끼니에 추가하고 같은 URI 문자열을 중복 첨부하지 않습니다. 빈 결과는 취소로 처리합니다.
- `RemovePhoto(reference, mealType, date)`는 해당 초안에서 한 장만 제거합니다. 마지막 사진을 제거해도 저장할 수 있습니다.
- 시스템 PickMultipleVisualMedia(ImageOnly)를 사용하며 전체 갤러리 권한은 요청하지 않습니다.
- 저장 전에는 선택 URI, 저장 후에는 내부 파일 참조를 목록에 보관합니다. 새 사진만 IO 스트림으로 복사하고 기존 저장 파일은 재사용합니다.
- 저장 실패 시 입력 전체를 유지하고 이번 시도에서 새로 생성한 파일만 정리합니다.
- 기록 화면은 가로 썸네일 목록/개별 제거, 내역은 대표 사진+추가 장수, 상세는 전체 사진을 표시합니다.
- 사진 없음은 '사진 없음', 읽기 실패는 대체 표시로 처리합니다. 상세 통계 구현은 추가하지 않았습니다.
- 제거 결과는 JSON 목록에 반영합니다. 제거된 기존 이미지 파일은 다른 기록의 공유 참조 보호를 위해 자동 삭제하지 않습니다.
- 사진은 앱 내부 files/images에 보관하므로 저장 후 원본 URI 권한이 사라져도 다시 읽을 수 있습니다. 미저장 초안은 프로세스 재시작 복원 대상이 아닙니다.

### 기존 파일 호환

LocalMealRepository가 기존 `photoReference: String?`를 읽을 때 한 장/빈 목록으로 변환합니다.
초기화만으로 파일을 쓰지 않으며, 다음 정상 저장 시 전체 기록을 photoReferences 배열 형식으로 원자적으로 교체합니다.
recordId, 날짜·끼니, 시간, 음식·양, 기존 사진 참조는 그대로 보존합니다.
두 필드가 함께 있으면 목록 뒤에 이전 참조를 보존하며 같은 문자열만 중복 제거합니다.
잘못된 형식은 오류로 전달하고 원본을 덮어쓰지 않습니다. 기존 날짜·끼니 중복 차단도 유지합니다.
모델의 단일 photoReference 접근자/생성자는 이전 호출부 호환용입니다. 새 화면은 반드시 photoReferences를 사용하세요.

### 이번 단계 검증

- :app:assembleDebug, :app:testDebugUnitTest: JVM 63개 통과.
- :app:compileDebugAndroidTestKotlin: 계측 테스트 소스 컴파일 성공. 계측 테스트 실행 결과가 아닙니다.
- 임시 디렉터리의 실제 LocalMealRepository/FilePhotoStorage와 VM으로 0장→1장→3장→개별 제거→전체 제거→새 VM 재조회 검증.
- 같은 recordId/기록 1건 유지, URI 중복 방지·빈 선택 취소·날짜/끼니 분리·새 사진 복사 실패 정리·저장 실패 정리·저장 중 사진 편집 잠금 검증.
- 기존 단일/nullable/누락 필드 마이그레이션, 초기화 원본 바이트 보존, 잘못된 형식 오류 검증.
- 320dp Compose 프리뷰에서 사진 없음 안내와 다중 썸네일/제거/추가 버튼 배치를 확인했습니다. 읽기 실패 프리뷰는 로딩 상태로 렌더되어 실제 실패 콜백 표시는 기기에서 미확인입니다.
- 이번 단계는 사용자 기기의 앱 설치/제거/운영 파일 수정 없이 검증합니다.
- 에뮬레이터/실기기의 시스템 사진 선택기 직접 조작, 실제 앱 프로세스 종료 후 재실행은 이번 단계에서 실행하지 않았습니다. 새 Repository/VM을 만드는 JVM 재조회 검증과 구분합니다.


## 주간 날짜·끼니 시간 선택

- 주간 범위는 월~일 7일. 제목은 해당 주 목요일의 연·월과 그 달 목요일 순번입니다.
- displayedWeekStart는 표시 범위이고 selectedDate는 조회 대상입니다. MoveWeek는 초안을 변경하지 않습니다.
- 미래 날짜/미래 주는 UI에서 비활성화하고 VM에서도 미래 날짜 선택을 거부합니다.
- 새 조식 08:00, 중식 12:00, 석식 18:00. 간식은 EnterRecord 이벤트에서 Clock으로 얻은 현재 시각입니다.
- EnterRecord는 다른 기본 탭에서 기록 탭으로 진입할 때만 발생합니다.
- EnsureRecordInitialized는 새 VM의 기록 목적지 복원에만 최초 진입을 보완합니다. 기존 VM의 회전/복귀에서는 시간 갱신을 하지 않습니다.
- 음식/사진 선택 복귀, 재구성, 크기 변경, RefreshTime 이벤트는 새 진입이 아닙니다.
- 새 빈 간식이며 편집/저장 이력이 없을 때만 다음 실제 진입에서 시간을 갱신합니다.
- 시간 우선순위는 현재 편집값 > 저장 기록 > 기본값입니다.
- UI의 timeValidationMessage와 saveMeal/재시도 직전 검증 모두 날짜+시간이 미래인지 확인합니다.
- 기본값이 미래여도 보정하지 않습니다. 과거 날짜는 하루의 모든 시간을 허용합니다.
- 이번 단계 검증은 JVM 테스트와 프리뷰입니다. 사용자 데이터가 있는 기기에 connectedDebugAndroidTest를 실행하지 않았습니다.


> 검증 중 데이터 보존 사고: connectedDebugAndroidTest 실행 후 emulator-5556의 앱 패키지가 제거됐습니다.
> 재설치 후 files/meal_records.json과 files/images가 존재하지 않는 것을 확인했습니다.
> 최초 읽기 점검에 있던 2026-11-08 조식 3건의 실제 기기 데이터는 복원하지 못했습니다.
> Repository 로직의 중복 보존 테스트 통과와 실제 기기 데이터 보존은 별개입니다.
> 아래 기기 테스트 명령은 사용자 데이터가 없는 전용 테스트 AVD에서만 실행해야 합니다.
> 기존 기기에서 실행하려면 기록·이미지의 앱 외부 백업과 복원 확인을 먼저 수행해야 합니다.

## 날짜·끼니별 저장/수정 개선 확인

이번 단계에서는 기존 JSON 모델을 변경하지 않고 MealKey(date, mealType)를 UI 조회 키로 추가했습니다.
저장 후 폼을 유지하고, 재조회·시간/음식 수정·반복 저장 시 같은 recordId를 사용합니다.
날짜 선택은 조회 전환이며 기존 기록의 이동이 아닙니다.
기존 날짜+끼니 중복은 자동 삭제·병합하지 않습니다.

실제 읽기 전용 점검:
- emulator-5556 최초 점검: 총 3건, 2026-11-08 조식 조합에 3건 중복. 로직은 해당 키 수정을 차단하지만 이후 기기 테스트의 앱 제거로 실제 파일이 사라짐.
- emulator-5554: 총 0건, 중복 없음.
- 중복 ID: 2af60734-1e16-47ce-95fc-29a593c6b0eb, 246d2fa2-9831-4523-a728-3d28420f719d, 8f151add-9c04-462a-b0c2-fd19d93c29bf.

검증: 디버그 빌드와 단위 테스트 51개 통과.
같은 키에 동시 저장 40회 및 재로딩 후 반복 수정에서도 1건과 최초 ID를 유지했습니다.
격리된 단위 테스트의 기존 중복 파일은 바이트 단위 보존을 검증했습니다. 사용자 기기의 실제 파일은 위 사고와 구분해야 합니다.
emulator-5556에서 전체 일반 계측 테스트 통과(14개, 별도 단계용 3개 의도적 건너뜀).
홈·내역·상세·분석은 같은 records를 받으며 별도의 누적 목록을 만들지 않습니다.
상세 통계 화면의 구현 범위는 기존과 동일합니다.
프로세스 종료→콜드 스타트→새 VM에서 재조회→시간 수정→3회 저장도 격리 파일로 검증했습니다.
이 과정에서 같은 식사 ID, 기록 1건, 내부 사진 파일 1개를 유지했습니다. 테스트 폴더는 정리했습니다.
실기기와 실제 사용자 중복 기록의 수동 정리는 실행하지 않았습니다.


## 범위와 실행

패키지는 `com.example.balancemeal`, 앱 모듈은 `:app`입니다.
Kotlin·Jetpack Compose·Material 3·Navigation 3를 사용합니다.
홈·내역·식사 상세·분석은 영역과 연결 틀만 있습니다. 화면별 통계와 세부 기능은 담당 팀원이 구현합니다.
기록 화면에는 날짜·시간·끼니, 사진 선택, 음식 행, 검색·선택, 영양 미리보기와 저장이 연결되어 있습니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

연결 기기가 있어야 계측 테스트를 실행할 수 있습니다. 테스트 정보는 사용자 화면에 표시하지 않습니다.

## 공유 의존성과 화면 구조

- `BalanceMealApplication.appContainer`가 앱 수명 동안 하나의 `AppContainer`를 유지합니다.
- 컨테이너의 `foodRepository`, `mealRepository`, `photoStorage`, `clock`을 재사용합니다.
- `MealAppRoute`가 `MealViewModelFactory`로 Activity 소유의 공유 `MealViewModel`을 내비게이션 엔트리 밖에서 생성합니다.
- `MealRoute`에서 `collectAsStateWithLifecycle()`로 구독합니다.
- Screen은 상태와 콜백만 받습니다. Screen에서 Repository·ViewModel·별도 저장 기록 목록을 만들지 않습니다.
- 공유 `records`의 원천은 `MealRepository.observeRecords()`입니다. 홈·내역·상세·분석은 이를 조회하거나 파생 데이터를 만듭니다.
- 창 너비 600dp 미만은 하단 내비게이션, 이상은 레일입니다. 840dp 이상에서 내역의 목록·상세 자리를 함께 배치합니다.
- 실제 창 크기 변경 시 기존 Activity ViewModel을 재사용합니다. 같은 탭의 반복 선택은 목적지를 추가하지 않습니다.

## 공통 모델

모델은 `app/src/main/java/com/example/balancemeal/model`에 있습니다. 모든 프로퍼티는 `val`입니다.

| 모델 | 필드와 의미 |
| --- | --- |
| AmountUnit | G, ML |
| Nutrients | carbohydrateG, proteinG, fatG, energyKcal: Double |
| Food | code, name, unit, nutrientsPer100 |
| SourceInfo | datasetName, originalFileName, sources: List<NutritionSource> |
| NutritionSource | sourceName, providerName, referenceDate: LocalDate |
| MealType | BREAKFAST(조식), LUNCH(중식), DINNER(석식), SNACK_DRINK(간식/음료). displayName으로 표시 |
| MealItemDraft | itemId: String(UUID 기본 생성), food: Food?, amountInput: String |
| MealDraft | date: LocalDate, time: LocalTime, photoReferences: List<String>, items: List<MealItemDraft> |
| MealItem | itemId, food: Food 전체 스냅샷, amount: Double |
| MealRecord | recordId(UUID 기본 생성), date, time, mealType, photoReferences: List<String>, items: List<MealItem> |

입력 문자열은 저장 검증 전까지 숫자로 바꿔 보관하지 않습니다.
LocalDate·LocalTime은 기존 ISO 직렬화기로 문자열 저장·복원합니다.
초기 날짜·시간은 주입된 Clock의 시간대를 따릅니다. 기본 Clock은 시스템 시간대입니다.
음식의 선택 당시 전체 정보를 기록에 저장하므로 나중에 음식 목록이 바뀌어도 저장 기록의 수치는 유지됩니다.
모델 파일에 계산 가능한 합계를 중복 저장하지 않습니다.

## Repository 계약

### FoodRepository / AssetFoodRepository

- `suspend getFoods(): List<Food>`
- `suspend getSourceInfo(): SourceInfo`

`app/src/main/assets/foods_sample.json`, `nutrition_source.json`을 UTF-8로 읽습니다.
원본 엑셀은 앱에서 가공하지 않습니다. 기존 sampledata 파일은 참고용이며 실행 시에는 assets를 읽습니다.
IO 디스패처에서 파싱·검증하며 성공한 파일별 결과만 캐시합니다. 동시 호출도 파일별 잠금으로 보호합니다.
음식 100개, 코드 중복/공백, 이름, 필수 필드, G/ML, 유한한 0 이상 영양 수치를 검증합니다.
누락·손상·읽기 실패는 예외로 전달하며 가짜 음식이나 빈 목록으로 대체하지 않습니다.

### MealRepository / LocalMealRepository

- `suspend initialize()`: 최초 읽기 완료까지 대기. 성공 후 재호출은 다시 읽지 않음. 실패 후 재시도 가능.
- `observeRecords(): Flow<List<MealRecord>>`: 초기화 성공 전에는 방출하지 않음.
- `suspend saveMeal(record: MealRecord)`: 날짜+끼니를 Mutex 안에서 조회한 뒤 생성/갱신. 기존 식사 ID 유지. 같은 ID를 다른 날짜/끼니로 이동시키는 요청은 거부.

실제 경로는 `context.filesDir/meal_records.json`입니다.
파일이 없을 때만 정상 빈 목록으로 초기화합니다. 손상 파일은 예외를 전달하고 덮어쓰지 않습니다.
초기화 성공 전에는 저장을 거부합니다. IO에서 초기화·저장을 Mutex로 직렬화합니다.
같은 디렉터리의 임시 파일에 쓰고 sync 후 원자적으로 교체하며, 성공 후에만 Flow를 갱신합니다.
원자적 교체를 지원하지 않거나 쓰기에 실패하면 오류를 전달합니다.
수동으로 records JSON을 덮어쓰거나 화면별 LocalMealRepository를 만들지 않습니다.

## 공유 상태와 이벤트

`ui/meal/MealUiState.kt`, `MealEvent.kt`, `MealViewModel.kt`를 기준으로 연결합니다.
외부에는 읽기 전용 `uiState = mutableUiState.asStateFlow()`만 공개합니다.
상태는 update/copy로 갱신하며, 파일 작업은 update 람다 밖에서 수행합니다.

| 상태 | 역할 |
| --- | --- |
| foods / sourceInfo | 공통 음식 목록과 데이터셋 출처 |
| records | Repository에서 관찰한 저장 기록 |
| homeDate | 홈 조회 날짜. 식사 입력 날짜와 독립적. VM 생성 후 Clock 날짜로 초기화 |
| currentMeal | 현재 편집 중인 끼니 |
| selectedDate / currentMeal | 식사 조회 대상 날짜·끼니. 날짜 변경은 이전 기록 이동이 아님 |
| draftsByKey | Map<MealKey, MealDraft>. 날짜+끼니별 초안 원본 |
| draftsByMealType | 현재 selectedDate의 끼니별 초안 투영. 기존 Screen 연결용 |
| dirtyMealKeys | 이 세션에서 사용자가 편집한 키(저장 후 폼 포함). 늦은 Repository 알림으로 입력 표현을 덮어쓰지 않음. 새 VM에서는 저장 기록으로 다시 초기화 |
| editingRecordId | 현재 날짜+끼니의 기존 식사 ID. 없으면 null |
| duplicateMealKeys | 기존 중복 조합과 식사 ID 목록. 자동 정리하지 않음 |
| inputErrors | itemId별 행 오류. items/foods/records/time은 영역 오류 |
| foodReadState | 음식·출처 읽기: Idle/Loading/Success/Error |
| recordReadState | 기록 초기화·관찰: Idle/Loading/Success/Error |
| saveState | 저장: Idle/Loading/Success/Error |
| savingMealKey / savingMealType | 저장 중 잠긴 날짜+끼니와 호환용 끼니 표시. 다른 키는 편집 가능 |
| nutritionPreview | 현재 끼니의 byItemId 행별 영양소와 total. 미완성이면 total=null |

팀원이 Screen에서 발생시킬 이벤트:

| 이벤트 | 동작 |
| --- | --- |
| SelectMeal(mealType) | 현재 끼니 전환. 다른 끼니 초안은 유지 |
| SetHomeDate(date) | 홈 조회 날짜만 변경 |
| SetDate(date) | 조회 날짜 전환. 이전 초안과 저장 기록의 날짜는 유지 |
| SetTime(time) | 현재 키의 시간 편집 |
| AddPhotos(references, mealType, date) / RemovePhoto(reference, mealType, date) | 선택 당시 날짜·끼니에 사진 추가(URI 중복 방지) / 개별 제거. 빈 선택은 취소 |
| AddItem | 현재 끼니에 새 UUID 행 추가 |
| SetAmount(itemId, input), RemoveItem(itemId) | 현재 끼니의 정확한 ID 행만 변경 |
| SelectFood(itemId, foodCode, mealType, date) | 명시한 끼니의 해당 ID 행만 갱신. 다른 음식이면 amountInput을 비움 |
| SaveCurrentMeal / vm.saveMeal() | 현재 끼니를 검증하고 고정해서 저장 |
| RetryFoodReading, RetryRecordReading, RetrySave | 실패한 해당 작업만 재시도 |
| ClearInputErrors | 입력 오류 표시 초기화 |

SetAmount/RemoveItem은 현재 조회 키용입니다. 비동기 음식·사진 결과에는 반드시 선택기를 열 당시의 날짜와 끼니를 명시합니다.
호환 호출에서 SelectFood의 date를 생략하면 itemId가 속한 초안을 찾지만 새 연결에서는 명시하는 것을 권장합니다.
SelectFood는 식품코드가 없거나 대상 행이 삭제됐으면 아무 행도 수정하지 않습니다.
같은 식품을 여러 행에 추가해도 itemId로 구분합니다. 행 Compose key도 itemId입니다.

저장에는 음식 1개 이상, 모든 행의 음식과 유한한 양수 섭취량이 필요합니다. 사진은 없어도 됩니다.
음식·기록 읽기가 성공해야 하며, 입력 누락은 저장 클릭 시 오류로 표시합니다.
기존 날짜+끼니 중복은 읽기 시 보존·안내하며 해당 조합의 저장만 거부합니다. 기록 수와 ID는 자동 합치거나 삭제하지 않습니다.
한 행이라도 잘못되면 전체 저장을 막습니다.
저장 시작 시 대상과 초안을 고정하고 해당 끼니를 UI와 VM 이벤트 양쪽에서 잠급니다.
연속 클릭은 Loading에서 거부합니다.
새 사진을 복사한 뒤 MealRecord를 생성/갱신합니다. 이미 저장된 내부 사진은 재사용합니다.
저장 성공 후에도 선택 날짜·끼니, 시간, 음식·양·사진·영양 미리보기를 유지합니다.
기존 키는 '변경사항 저장', 새 키는 '식사 저장'을 표시합니다.
사진 교체 전의 기존 사진은 다른 기록 참조 보호를 위해 자동 삭제하지 않습니다.
날짜·시간과 다른 끼니 초안은 유지합니다. 실패하면 입력을 유지합니다.
RetrySave는 실패한 스냅샷과 같은 recordId를 사용합니다. 실패 후 입력을 수정하면 새 저장을 요청합니다.
사진 복사부터 기록 저장과 정리까지는 취소로 성공한 사진을 잘못 지우지 않도록 보호합니다.
운영체제의 강제 프로세스 종료는 이 코루틴 보호와 별개입니다.

## 영양소 계산

`nutrition/CalculateNutrients.kt`의 일반 Kotlin 함수:
`calculateNutrients(nutrientsPer100, amount): Nutrients?`.

각 항목은 원본 수치 × 섭취량 ÷ 100입니다. energyKcal을 직접 사용하며 탄단지로 열량을 환산하지 않습니다.
빈칸·문자·0 이하·NaN·Infinity·계산 오버플로는 유효 결과가 아닙니다.
ViewModel이 행별 결과와 합계를 만듭니다. g와 ml 섭취량 자체는 합하지 않습니다.
하나라도 미완성이거나 행이 없으면 완성된 식사 합계를 표시하지 않습니다.
내부 Double은 반올림하지 않습니다. `NutrientsDisplay`에서만 소수 첫째 자리로 포맷합니다.

## 라우트와 ID 규칙

`navigation/Destinations.kt`의 Serializable NavKey를 사용합니다.

- 기본 탭: Home, Record, History, Analysis
- 음식 선택: `FoodSelection(mealType, itemId, date)` (date는 ISO 날짜 문자열)
- 식사 상세: `MealDetail(recordId)`
- 출처: DataSources

Food·MealRecord 전체 객체나 사진 바이트를 라우트에 넣지 않습니다.
음식 선택을 열 때 현재 끼니와 itemId를 고정하고, 식품코드만 선택 콜백으로 반환합니다.
식사 상세는 uiState.records에서 recordId로 찾습니다. 없으면 담당 화면에서 없는 기록 상태를 표시합니다.
음식 선택 취소/뒤로가기는 변경 이벤트를 보내지 않아 기존 입력을 유지합니다.
이름 검색어는 Route의 rememberSaveable 상태이며, 목록은 LazyColumn에서 food.code로 key를 지정합니다.

## 사진 참조와 PhotoStorage

초안에는 PhotoPicker가 준 content:// URI 문자열을 보관합니다.
전체 갤러리 권한을 요청하지 않습니다. 선택 당시 끼니를 rememberSaveable로 보관합니다.
취소 시 기존 참조를 유지합니다. 초안의 선택 URI는 영구 보관 파일이 아닙니다.

- `suspend PhotoStorage.copyPhoto(sourceReference): String`
- `suspend PhotoStorage.deletePhoto(reference)`

실제 구현 `FilePhotoStorage`는 IO 스트림으로 `filesDir/images/<UUID>.img`에 복사합니다.
메모리에 원본 전체를 올리지 않으며, 임시 파일·빈 스트림·읽기 실패를 처리합니다.
.img는 컨테이너 파일명이며 실제 이미지 내용은 변환하지 않습니다.
복사 성공 후 file: URI 문자열을 반환합니다. 저장된 MealRecord에는 이 내부 참조를 사용합니다.
목록의 각 참조는 공통 MealPhoto에서 Coil 3 AsyncImage(model = reference)로 표시합니다. 앱 내부 파일을 외부 공유하는 API는 추가하지 않았습니다.
불러오기 실패는 재선택 안내를 표시합니다.

기록 저장 실패 시 이번 시도에서 생성한 새 파일만 deletePhoto로 정리합니다.
원본 선택 URI·다른 기록의 사진·다른 끼니 사진은 정리 대상으로 전달하지 않습니다.
구현은 자신의 이미지 디렉터리와 UUID 파일명만 삭제하도록 제한합니다.
정리 자체의 오류는 원래 저장 오류의 suppressed 예외에 보존합니다.

## 음식 출처 화면 연결

출처 화면은 이미 로딩한 `state.sourceInfo`를 사용합니다.
전용 로딩이 필요하면 공유 `foodRepository.getSourceInfo()`를 사용하고 화면별 별도 저장소는 만들지 않습니다.

현재 nutrition_source.json:

- datasetName: 전국통합식품영양성분정보_음식_표준데이터
- originalFileName: 전국통합식품영양성분정보_음식_표준데이터-20261108.xls
- sources[0]: 농촌진흥청(국가표준식품성분표), 제공기관 식품의약품안전처, 기준일 2026-09-29
- sources[1]: 식품의약품안전처, 제공기관 식품의약품안전처, 기준일 2026-09-29

기준일은 LocalDate로 전달되므로 화면에서 원하는 형식으로 표시합니다.
출처는 데이터셋 단위 목록입니다. Food에는 개별 출처 식별자가 없으므로 각 음식에 특정 출처를 임의로 연결하지 않습니다.
foods_sample.json은 고유 코드 100개(G 80개, ML 20개)이며 영양 수치는 제공 파일 그대로입니다.

## 복원 범위와 검증

회전·크기 변경·탭 이동에서는 공유 ViewModel의 초안을 유지합니다.
미저장 초안은 디스크에 저장하지 않으므로 프로세스를 종료하면 초기화됩니다.
프로세스 재실행 시 저장된 MealRecord와 내부 사진을 불러오며, 날짜+끼니를 선택하면 편집 초안으로 복원합니다.
홈·내역·상세·분석에 전달되는 records는 같은 Repository Flow의 결과이므로 수정 후 데이터도 같은 ID로 교체됩니다.
해당 화면의 상세 통계 UI는 여전히 담당 팀원의 구현 영역입니다.
최초 로딩 오류와 빈 기록을 구분해야 합니다.

### 실제 확인 기록 (2026-11-08)

환경: emulator-5554. 디버그 빌드·전체 단위 테스트 성공, 일반 계측 테스트 13개 통과.
프로세스 검증 3개는 기본 실행에서는 의도적으로 건너뜁니다.
prepare와 verify는 아래처럼 따로 실행해 각각 1개 통과했습니다.

| 시나리오 | 확인 방법과 결과 |
| --- | --- |
| 조식 입력 → 중식 → 조식 | UI에서 서로 다른 행·양을 입력하고 복귀해 원래 값 보존 확인. 날짜·시간도 별도 UI 테스트로 확인 |
| 탭 이동 후 기록 복귀 | 홈 이동 후 기록에 돌아와 음식·양·ID 유지 확인 |
| 음식 선택 취소·교체 | 실제 assets의 쌀밥/미숫가루 검색. 교체 시 양 초기화, 취소 시 기존 음식·양 유지, 삭제된 대상 보호 확인 |
| 행 추가·삭제 | UI 추가·입력·삭제 및 같은 음식의 서로 다른 ID/양 유지 확인 |
| 회전·크기 변경 | requestedOrientation으로 실제 가로/세로 전환. wm size 2400x1800 및 원복으로 너비 600dp 이상/미만 전환. 공유 VM·초안 유지와 측면 내비게이션 확인 |
| 저장 연속 요청 | 단위 테스트의 지연 저장 중 중복 요청 차단 확인. 실제 저장 준비 단계에서도 saveMeal을 연속 5회 호출해 기록 1개·사진 1개만 생성 확인 |
| 프로세스 종료 후 복원 | prepare → force-stop → MainActivity 콜드 스타트 → 다른 PID의 verify 실행. ID·125.5 섭취량·내부 사진 디코딩 확인. 원본 선택 사진 삭제 뒤에도 복사본 읽기 성공 |

프로세스 검증은 사용자 파일 보호를 위해 filesDir/connection-verification 안에서
동일한 MealViewModel·AssetFoodRepository·LocalMealRepository·FilePhotoStorage 구현을 조립합니다.
실제 운영 meal_records.json은 수정하지 않았습니다.
verify의 finally에서 테스트 폴더를 정리합니다. 테스트 중단 시 cleanup을 별도로 실행할 수 있습니다.
임시 정보는 테스트 stdout/assertion으로만 확인하며 사용자 화면에는 표시하지 않습니다.

실행하지 않은 범위: 실기기, 자유 창을 마우스로 직접 끌어 리사이즈, 폴더블 접기/펼치기,
저장 도중 프로세스 강제 종료·전원 손실, 실제 사용자 운영 기록 파일에 대한 강제 종료 테스트.
크기 변경 검증은 디스플레이 크기를 변경해 앱 창 구성을 바꾸는 방식이며 자유 창 드래그 시험과 구분합니다.
홈·내역·상세·분석의 상세 UI/통계 표시는 구현하지 않았고 검증 범위도 아닙니다.

### 프로세스 검증 재현

Android SDK의 adb가 PATH에 있고 emulator-5554가 연결된 경우:

```powershell
.\gradlew.bat :app:installDebug :app:installDebugAndroidTest
adb -s emulator-5554 shell am instrument -w -e class 'com.example.balancemeal.ProcessRecoveryVerification#prepare' -e recoveryPhase prepare com.example.balancemeal.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 shell am force-stop com.example.balancemeal
adb -s emulator-5554 shell am start -W -n com.example.balancemeal/.MainActivity
adb -s emulator-5554 shell am instrument -w -e class 'com.example.balancemeal.ProcessRecoveryVerification#verify' -e recoveryPhase verify com.example.balancemeal.test/androidx.test.runner.AndroidJUnitRunner
```

중단된 검증의 파일만 정리:

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'com.example.balancemeal.ProcessRecoveryVerification#cleanup' -e recoveryPhase cleanup com.example.balancemeal.test/androidx.test.runner.AndroidJUnitRunner
```

connectedDebugAndroidTest는 실행 뒤 테스트 APK를 제거할 수 있으므로,
수동 am instrument 검증 전에는 installDebugAndroidTest를 실행합니다.
prepare는 남아 있는 검증 폴더를 임의로 덮어쓰지 않습니다.

