# BalanceMeal 팀 연결 안내

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
| MealDraft | date: LocalDate, time: LocalTime, photoReference: String?, items: List<MealItemDraft> |
| MealItem | itemId, food: Food 전체 스냅샷, amount: Double |
| MealRecord | recordId(UUID 기본 생성), date, time, mealType, photoReference, items: List<MealItem> |

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
- `suspend saveMeal(record: MealRecord)`: 같은 recordId는 갱신, 새 ID는 추가.

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
| draftsByMealType | Map<MealType, MealDraft>. 끼니 전환 시 각 초안 보존 |
| inputErrors | itemId별 행 오류. photo/items/foods/records는 영역 오류 |
| foodReadState | 음식·출처 읽기: Idle/Loading/Success/Error |
| recordReadState | 기록 초기화·관찰: Idle/Loading/Success/Error |
| saveState | 저장: Idle/Loading/Success/Error |
| savingMealType | 저장 중 잠긴 대상 끼니. 나머지 끼니 편집은 가능 |
| nutritionPreview | 현재 끼니의 byItemId 행별 영양소와 total. 미완성이면 total=null |

팀원이 Screen에서 발생시킬 이벤트:

| 이벤트 | 동작 |
| --- | --- |
| SelectMeal(mealType) | 현재 끼니 전환. 다른 끼니 초안은 유지 |
| SetHomeDate(date) | 홈 조회 날짜만 변경 |
| SetDate(date), SetTime(time) | 현재 끼니 입력 날짜·시간 변경 |
| SetPhoto(reference, mealType) | 선택 당시 끼니의 사진 참조 변경. null은 취소로 처리 |
| AddItem | 현재 끼니에 새 UUID 행 추가 |
| SetAmount(itemId, input), RemoveItem(itemId) | 현재 끼니의 정확한 ID 행만 변경 |
| SelectFood(itemId, foodCode, mealType) | 명시한 끼니의 해당 ID 행만 갱신. 다른 음식이면 amountInput을 비움 |
| SaveCurrentMeal / vm.saveMeal() | 현재 끼니를 검증하고 고정해서 저장 |
| RetryFoodReading, RetryRecordReading, RetrySave | 실패한 해당 작업만 재시도 |
| ClearInputErrors | 입력 오류 표시 초기화 |

SetAmount/RemoveItem은 현재 끼니용입니다. 비동기 음식·사진 결과에는 반드시 대상 끼니를 명시합니다.
SelectFood는 식품코드가 없거나 대상 행이 삭제됐으면 아무 행도 수정하지 않습니다.
같은 식품을 여러 행에 추가해도 itemId로 구분합니다. 행 Compose key도 itemId입니다.

저장에는 사진, 음식 1개 이상, 모든 행의 음식과 유한한 양수 섭취량이 필요합니다.
음식·기록 읽기가 성공해야 하며, 입력 누락은 저장 클릭 시 오류로 표시합니다.
한 행이라도 잘못되면 전체 저장을 막습니다.
저장 시작 시 대상과 초안을 고정하고 해당 끼니를 UI와 VM 이벤트 양쪽에서 잠급니다.
연속 클릭은 Loading에서 거부합니다.
사진 복사 후 MealRecord를 만들어 저장하며 성공해야 대상의 음식·사진·양을 비웁니다.
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
- 음식 선택: `FoodSelection(mealType, itemId)`
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
표시는 Coil 3 AsyncImage(model = photoReference)로 합니다. 앱 내부 파일을 외부 공유하는 API는 추가하지 않았습니다.
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
프로세스 재실행 시 복원 대상은 성공적으로 저장한 MealRecord와 내부 사진입니다.
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

