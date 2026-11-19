# Java 수업 제출 점검 안내

2026-11-19 H49 문서 점검 기준. [12주차 강의자료](<../../../2026-2 Java 12주차.pdf>) 3·9~11쪽을 대조했다. 최신 자동 실행과 PDF 검토 결과는 [검증 기록](VERIFICATION.md)의 V-20261119-03에 있다. 이 문서는 제출 완료나 점수 보장이 아니다.

강의자료는 **11월 23일 월요일까지 이메일 제출**, 개발 문서와 발표자료는 **PDF**, 소스 코드는 **ZIP 파일 또는 GitHub 저장소 주소**를 요구한다. 수신 주소는 원본 PDF 3쪽에서 제출 직전에 다시 확인한다. [개발 문서 PDF](BandDecision_Development_Report.pdf)는 생성·화면 검토했다. **발표자료 PDF와 이메일 전송은 아직 수행하지 않았다.**

개발 문서를 수정하면 `BandDecision` 폴더에서 `python docs/render_development_report.py`를 실행해 PDF를 다시 만든다. 이 스크립트는 로컬 Microsoft Edge를 사용한다. 생성 후 PDF를 열어 표·한글·쪽나눔을 다시 확인한다.

## 제출 구성

- 필수 실행 자료: src/, test/, build.ps1, run.ps1, run-gui.ps1.
- 설명 자료: README.md, DEVELOPMENT.md, IMPLEMENTATION.md, SPEC.md, docs/와 하위 evidence/. 제출용 개발 문서 원본은 docs/DEVELOPMENT_REPORT.md, PDF는 docs/BandDecision_Development_Report.pdf.
- AGENTS.md는 개발 기록 규칙이므로 함께 보관할 수 있다.
- out/, tmp/, 개인 data/, IDE 설정은 제출용 실행에 필요하지 않다. 기존 개인 파일을 삭제하지 말고 제출 복사본에서 제외한다.
- DEVELOPMENT.md의 강의자료 링크는 프로젝트 밖 원본 PDF를 참조한다. 배포 폴더에서 PDF를 제공하지 않으면 해당 참고 링크는 열리지 않지만 프로그램 실행에는 영향이 없다.

읽는 순서는 README → docs/DEVELOPMENT_REPORT → DEVELOPMENT와 세부 기록을 권장한다. SPEC은 초기 명세 원본이다. 이후 변경은 DECISIONS, 현재 완료 기준은 제출용 보고서 2절과 EVALUATION의 R01~R14(R15는 범위 제외)를 따른다.

## 실행 조건과 재현

Windows PowerShell과 JDK 17 이상이 필요하다. java와 javac 둘 다 설치되어야 한다. 실행 스크립트가 있는 폴더에서 다음을 실행한다.

```powershell
java -version
javac -version
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-gui.ps1 -Demo
```

`.\run.ps1 -Demo`도 샘플 GUI이며 일반 GUI는 `.\run-gui.ps1`이다. 샘플도 재실행 시 저장 내용을 읽는다. 제출 시 개인 data를 제외하면 첫 샘플 실행에서 실행일 기준 예시를 생성한다. 빌드는 --release 17로 Java 17 대상 바이트코드를 만든다.

H34 당시 새 임시 경로에서 377개 검사를 확인했다. H35에서 콘솔·수동 백업을 제거했으며 최신 H46은 170+198=368개 자동 검사와 Swing 렌더링을 통과했다. 이는 동일 PC에서의 검사이며 다른 PC 검증은 아니다.

2026-11-19 사용자 전달 안내: 구현 결과와 시연은 `-Demo`로 보여주면 된다. 별도 시연 기능이나 모든 데이터를 새로 입력하는 순서는 요구하지 않는다. PDF 10~11쪽의 발표 기준에 따라 Demo 화면에서 핵심 기능의 연결, 구조·설계 이유, 현재 범위와 확장 가능성을 설명할 준비는 남아 있다. 발표자료 PDF·실제 시간 측정과 제출은 아직 미완료다.

## 설명할 수 있어야 하는 내용

| 수업 개념 | 구현 사례 | 설명할 질문 |
|---|---|---|
| 배열·반복문 | AttendanceService의 날짜/응답/사유 배열, 추천기 정렬 | 같은 인덱스의 자료가 어떻게 연결되는가? |
| 클래스·캡슐화 | Member, Song, 서비스 클래스와 배열 복사 | 외부에서 내부 배열을 바꾸지 못하게 하는 이유는? |
| 조건·검증·예외 | 등급 1~5, 투표 사유, 5개 계획 제한 | 잘못된 입력 시 기존 값은 어떻게 보존되는가? |
| 정렬·계산 | 날짜 인원순, 곡 점수와 동점 순서 | 3×찬성−2×부담과 max(0, 난이도−실력)의 의미는? |
| 데이터 흐름 | preview → 확인 → 재검증 → 확정 스냅샷 | 원본이 바뀌어도 이전 확정 계획은 왜 유지되는가? |

추가 학습 범위는 Swing/EDT/이벤트 리스너, LocalDate/YearMonth, Properties와 Files, record(ConfirmedPlan), 람다·일부 테스트의 Stream이다. 핵심 상태는 배열 중심이지만 GUI·저장은 초기 수업 범위보다 넓어졌다. 실제 수업의 허용 범위와 개발 지원 도구 사용 표기 규정을 확인하고, 직접 설명한 내용과 지원받은 내용을 구분한다. 동료 피드백이나 직접 테스트를 수행하지 않았다면 만들어 적지 않는다.

## 제출 직전 남은 일

- [ ] 본인이 [사용 예시](USAGE.md)를 따라 GUI를 직접 조작하고 결과 기록.
- [ ] 계획 확정/수정/삭제 확인창을 포함한 흐름 확인.
- [ ] 가능하면 다른 PC에서 새 폴더로 실행.
- [ ] 제출 복사본에서 개인 일정·백업 파일 제외 확인.
- [ ] 교수자가 요구한 파일 형식·학번/이름·개발 지원 도구 표기 확인.
- [x] 개발 문서를 PDF로 변환하고 전 페이지를 열어 한글·표·쪽나눔 확인(2026-11-19, 개발 지원 도구 수행).
- [ ] 발표자료를 PDF로 변환하고 실제로 열어 내용 확인.
- [ ] 소스 ZIP 또는 최신 GitHub 저장소 주소를 준비하고 11/23 기한 내 이메일 제출 여부 기록.
- [ ] 10분 설명·시연을 직접 연습하고 2분 질의/전환을 고려한 발표 순서 확인.

기능 추가보다 이 설명·시연을 마무리하는 것이 현재 제출 준비의 우선순위다.
