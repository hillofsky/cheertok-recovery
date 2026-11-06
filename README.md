# CheerTok Recovery

CheerTok Air의 Android Special 모드 입력을 복구하는 비공식 실험용 Android 앱입니다. 제조사 제품이나 Shizuku 프로젝트와 제휴하지 않습니다.

## 설치 및 사용

1. [v0.6.15 릴리스](https://github.com/hillofsky/cheertok-recovery/releases/tag/v0.6.15)에서 `CheerTok-Recovery-0.6.15.apk`를 받아 설치합니다. 기존 공개 APK 위에 업데이트할 수 있습니다.
2. [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next/releases)를 별도로 설치하고 최초 페어링을 완료합니다. 이 앱에는 Shizuku가 포함되어 있지 않습니다.
3. Shizuku-Next에서 `Start without Wi-Fi (experimental)`를 설정합니다. 이 기능은 기기별로 다를 수 있습니다.
4. Shizuku-Next를 시작하고 `Running / ADB / Without Wi-Fi / UID 2000`을 확인합니다.
5. 치어톡을 `CheerTok Special`로 연결합니다.
6. **치어톡 복구** 앱을 열고 Shizuku 접근을 허용한 뒤 스위치를 켭니다.
7. **실행 중**이 표시되면 사용합니다. 앱 화면을 닫아도 수신기는 계속 실행됩니다.

중지하려면 복구 앱의 스위치를 끕니다. 재부팅 후에는 Shizuku-Next를 먼저 시작한 뒤 복구 앱을 켭니다. 검증된 환경에서는 외부 Wi-Fi 연결이나 USB 연결 없이도 재부팅 후 시작할 수 있었습니다. 모든 Android 기기에서 가능한 것은 아닙니다.

기존 공식 Shizuku에서 Shizuku-Next로 바꿀 때는 서명이 달라 기존 앱 제거와 재페어링이 필요할 수 있습니다. 기존 설치를 무작정 덮어쓰지 말고 해당 프로젝트의 안내를 확인하세요. 공식 Shizuku의 무선/USB 시작도 사용할 수 있지만 오프라인 시작 검증은 아래 버전으로 했습니다.

## 스크롤 옵션

앱에서 **복구 옵션**을 열고 아래 항목을 조정한 뒤 **저장 및 적용**을 누릅니다. 복구가 켜져 있으면 잠시 재연결됩니다. **기본값**은 옵션을 초기값으로 되돌리며, **저장 및 적용**을 눌러야 반영됩니다.

| 옵션 | 사용법 | 기본값 |
| --- | --- | --- |
| 빠른 세로 스크롤을 터치로 처리 | 천천히 위아래로 쓸면 마우스 휠, 빠르게 쓸면 터치 스와이프로 동작합니다. 끄면 세로 스크롤을 휠로 처리합니다. | 켜짐 |
| 빠른 가로 스크롤을 터치로 처리 | 천천히 좌우로 쓸면 가로 휠, 빠르게 쓸면 터치 스와이프로 동작합니다. 끄면 가로 스크롤을 휠로 처리합니다. | 켜짐 |
| 가로 스크롤 방향 반전 | 가로 휠과 가로 터치의 기본 방향을 함께 뒤집습니다. | 켜짐 |
| 최근 앱에서만 가로 터치 방향 추가 반전 | 빠른 가로 스와이프가 시작될 때 최근 앱 화면인지 확인합니다. 최근 앱에서만 터치 방향을 추가로 뒤집습니다. | 켜짐 |
| 터치 이동량 | 50~200% 범위에서 터치 스와이프의 이동량을 조절합니다. | 100% |
| 추가 관성 | 0~150% 범위에서 스와이프 끝부분에 추가하는 가속을 조절합니다. 0%에서도 앱이 자체 관성을 적용할 수 있습니다. | 100% |

## 멀티태스킹 단축키

**복구 옵션 → 홈 → 뒤로가기 멀티태스킹 조합**을 켭니다. 기본값은 켜짐입니다. 치어톡의 물리 버튼을 아래 순서대로 누릅니다.

- **홈 → 뒤로가기 한 번** · 홈을 누른 뒤 0.45초 안에 뒤로가기를 누르면 최근 앱 목록이 열립니다.
- **홈 → 뒤로가기 두 번** · 첫 번째 뒤로가기 후 0.35초 안에 한 번 더 누르면 직전에 사용한 앱으로 전환합니다.
- **홈만 한 번** · 조합 입력을 기다린 뒤 약 0.45초 후 홈 화면으로 이동합니다.
- **뒤로가기만 한 번** · 일반 뒤로가기가 즉시 실행됩니다.

조합을 끄면 홈과 뒤로가기는 각각 단독 버튼으로 동작합니다.

## 검증 범위

아래 표는 이전 테스트판을 포함해 실제 기기에서 확인한 결과입니다. 0.6.15에서는 설치, 복구 옵션 표시와 기본 설정으로 수신 프로세스가 시작되는 것을 확인했습니다.

| 항목 | 확인 결과 |
| --- | --- |
| 환경 | Galaxy S26 Ultra, Android 17 / One UI 9, CheerTok Air 양버튼 기기 |
| 시작 도구 | Shizuku-Next 14.0.32-next, ADB UID 2000 |
| 기본 입력 | 포인터, 상하좌우 스크롤, 탭, 두 번 탭, 길게 누르기, 드래그 정상 |
| 버튼 | 홈, 뒤로가기, 볼륨 정상 |
| Fn 음악 조합 | 재생/일시정지, 이전 곡, 다음 곡 정상 |
| 카메라 | 카메라 앱에서 사진 촬영 정상 |
| Fn 매크로 | 녹화, 단일/반복 재생, 중지 정상 |
| 재부팅 | USB·외부 Wi-Fi 없이 다시 시작한 뒤 기본 입력 정상 |
| 미검증 | 커서 위치 저장, 탐험판 전용 카메라 전환·줌·영상 진행 막대 제어 |

## 동작 방식과 권한

BLE GATT로 기기의 입력을 읽고 Android의 `hid` 명령을 통해 가상 마우스와 소비자 제어 장치를 만듭니다. Shizuku UserService는 ADB 셸 권한으로 수신기를 실행합니다. 루트는 검증 환경에서 사용하지 않았습니다.

재부팅 후 원래 HID와 가상 HID가 동시에 입력하지 않도록, 복구 실행 중에는 해당 CheerTok Special의 시스템 HID 프로필을 분리합니다. 스위치를 끄면 시스템 HID 연결 복원을 요청합니다. 다른 Bluetooth 기기의 연결 정책은 변경하지 않습니다.

일반 앱 권한만으로 동작하는 프로그램이 아닙니다. Bluetooth 원시 보고서와 숨겨진 Android API, `/system/bin/hid`를 사용하므로 OS 변경에 취약합니다. 앱에는 인터넷 권한이 없고 서버에 데이터를 전송하는 코드도 없습니다. Shizuku-Next의 네트워크 사용은 그 프로젝트에서 별도로 관리합니다.

## 알려진 제한

- 현재 기기의 GATT 인스턴스 `28`/`32`, 보고서 형식과 USB vendor/product ID에 맞춘 구현입니다. 자동 프로토콜 탐지 기능은 없습니다.
- 탭과 물리 뒤로가기 버튼이 동일 비트를 보내므로 수신 시간 차이 **12ms**로 구분합니다. 기기·부하·펌웨어에 따라 오판할 수 있습니다.
- 연결이 끊기면 스위치를 껐다 켜야 할 수 있습니다. 연결 복원 및 프로세스 강제 종료 처리는 완전하지 않습니다.
- 장치 확인은 2초마다 수행합니다. 배터리 소모량은 측정하지 않았습니다.
- 앱 화면은 한국어이며 접근성·화면 크기 대응은 아직 제한적입니다.
- 기본 배포 APK는 초기 개발 빌드 설정(`debuggable=true`, target SDK 31)을 유지합니다. Google Play 배포용으로 준비된 앱이 아닙니다.

## 빌드

Python 3.9 이상, JDK 17 권장, Android SDK 플랫폼 31 및 Build Tools 31.0.0이 필요합니다. Android Studio 없이도 빌드할 수 있습니다.

```sh
sdkmanager 'platforms;android-31' 'build-tools;31.0.0'
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
python scripts/build.py
```

PowerShell에서는 환경 변수를 `$env:JAVA_HOME`, `$env:ANDROID_SDK_ROOT`로 지정하세요. 결과는 `build/CheerTok-Recovery-0.6.apk`입니다.

처음 빌드할 때 Shizuku API 13.1.5 AAR 4개를 Maven Central에서 받고 고정 SHA-256을 검사합니다. 의존성 캐시와 새 디버그 서명키는 무시되는 `.local/`에 저장합니다. 개인 SDK 경로, 로그, 기기 주소와 서명키는 저장소에 포함하지 않습니다.

자체 빌드는 새 디버그 키로 서명되므로 Releases APK를 덮어쓰지 못할 수 있습니다. 앱을 제거하면 설정이 사라집니다. 공식 릴리스의 서명키는 공개하지 않습니다. 자체 배포 시 본인의 키를 사용하세요.

```sh
python scripts/build.py --test-only
# 비밀번호는 환경 변수 CHEERTOK_STORE_PASSWORD / CHEERTOK_KEY_PASSWORD로 전달
python scripts/build.py --keystore /private/path/release.jks --alias your-alias
```

CI는 재현 가능한 탭/뒤로가기 시간 기록 테스트와 SDK 빌드를 수행합니다. CI 아티팩트는 임시 디버그 키로 서명되며, Releases APK와 서명이 다릅니다. CI는 실제 Bluetooth 장치를 시험하지 않습니다.

## 소스 안내

- `ControlActivity`: 스위치 UI와 Shizuku 승인/연결
- `RecoveryService`: 수신 프로세스 수명과 상태
- `ShellProbe`, `MainActivity`: 셸 환경 초기화 및 BLE 진단/수신
- `NativeInputGuard`: 원래 HID의 중복 입력 방지
- `UhidBridge`: 가상 HID, 휠·터치 스크롤 변환과 멀티태스킹 조합
- `RecoveryOptions`: 복구 옵션의 기본값과 허용 범위
- `ScrollPacer`: 휠 보고서 간격 조정
- `TapRouter`: 터치 탭과 물리 뒤로가기의 시간 구분

## 라이선스와 참고

프로젝트 코드는 [MIT](LICENSE)입니다. 포함된 Shizuku API 의존성의 MIT 고지는 [licenses/Shizuku-API-MIT.txt](licenses/Shizuku-API-MIT.txt)에 있습니다. Shizuku/Shizuku-Next 앱 자체는 저장소나 APK에 포함하지 않습니다.

- [제조사 매뉴얼](https://cheerdots.com/pages/cheertok-air-user-manual)
- [Shizuku API](https://github.com/RikkaApps/Shizuku-API)
- [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next)

버그를 제보할 때는 기기·OS·펌웨어와 재현 동작을 알려주세요. 원시 Bluetooth 보고서, 기기 MAC, 페어링 코드, 개인 화면·알림은 공개 이슈에 첨부하지 마세요.
