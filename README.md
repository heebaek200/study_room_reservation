# 스터디룸 예약 관리 시스템

일반 회원의 스터디룸 조회·예약·취소·환불 요청과 관리자의 회원·스터디룸·예약·환불 관리를 구현한 팀 프로젝트입니다.

Java 내장 `HttpServer`를 기반으로 간단한 WAS 구조를 직접 구성하고, Handler–Service–DAO 계층과 JDBC·MySQL을 이용해 기능을 구현했습니다.  
단순 CRUD를 넘어 권한 분리, 데이터 소유권 검사, 트랜잭션, 비관적 락을 이용한 동시 예약 제어를 주요 과제로 다뤘습니다.

## 주요 기능

### 일반 회원

- 회원가입·로그인·로그아웃
- 회원 정보 조회·수정·탈퇴
- 스터디룸 목록·상세 조회
- 예약 신청·목록·상세 조회
- 예약 취소와 환불 요청
- 본인 환불 내역 조회

### 관리자

- 회원 조회와 강제 탈퇴
- 스터디룸 등록·수정
- 전체 예약 조회
- 환불 요청 조회·승인·거절

## 프로젝트 관리

| 구분 | 링크 |
| --- | --- |
| 프로젝트 보드 | [GitHub Projects](https://github.com/users/heebaek200/projects/5) |
| Issue 목록 | [GitHub Issues](https://github.com/heebaek200/study_room_reservation/issues) |
| Pull Request 목록 | [Pull Requests](https://github.com/heebaek200/study_room_reservation/pulls) |
| 전체 Wiki | [프로젝트 Wiki](https://github.com/heebaek200/study_room_reservation/wiki) |
| 프로젝트 소개 | [스터디룸 예약 관리 시스템 프로젝트 소개](https://github.com/heebaek200/study_room_reservation/wiki/스터디룸-예약-관리-시스템-프로젝트-소개) |

## 설계 문서

| 문서 | 설명 |
| --- | --- |
| [최초 설계](https://github.com/heebaek200/study_room_reservation/wiki/최초-설계) | 프로젝트 시작 단계의 기능과 구조 |
| [요구사항 명세서](https://github.com/heebaek200/study_room_reservation/wiki/요구사항-명세서) | 기능·제약사항·완료 기준 |
| [권한별 기능 매트릭스](https://github.com/heebaek200/study_room_reservation/wiki/권한별-기능-매트릭스) | 비회원·일반 회원·관리자별 접근 범위 |
| [데이터베이스 설계서 및 ERD](https://github.com/heebaek200/study_room_reservation/wiki/데이터베이스-설계서-및-ERD) | 테이블, 관계, 상태값과 데이터 보존 정책 |
| [사용자 시나리오 및 업무 규칙](https://github.com/heebaek200/study_room_reservation/wiki/사용자-시나리오-및-업무-규칙) | 정상·예외 흐름과 업무 규칙 |
| [화면 설계서](https://github.com/heebaek200/study_room_reservation/wiki/화면-설계서) | 사용자·관리자 화면 구성 |
| [주요 기능 클래스 다이어그램](https://github.com/heebaek200/study_room_reservation/wiki/주요-기능-클래스-다이어그램) | Handler·Service·DAO 관계 |
| [작업 분담 및 Issue 목록](https://github.com/heebaek200/study_room_reservation/wiki/작업-분담-및-Issue-목록) | 기능별 담당 영역과 Issue 구성 |

## 협업 문서

| 문서 | 설명 |
| --- | --- |
| [GitHub Projects 도입](https://github.com/heebaek200/study_room_reservation/wiki/GitHub-Projects-도입) | 프로젝트 관리 도구와 상태 관리 방법 |
| [작업 순서](https://github.com/heebaek200/study_room_reservation/wiki/작업-순서) | Issue 확인부터 브랜치·PR·병합까지의 절차 |
| [Git 문제 해결](https://github.com/heebaek200/study_room_reservation/wiki/Git-문제-해결) | 작업 중 발생할 수 있는 Git 문제 대응 방법 |

## 주요 기술적 고려사항

- BCrypt를 이용한 비밀번호 해시 저장
- 비회원·일반 회원·관리자 권한 분리
- 회원 탈퇴 시 상태를 변경하는 논리 삭제
- 예약 생성·취소·환불 처리의 트랜잭션 적용
- `SELECT ... FOR UPDATE`를 이용한 비관적 락
- 동일 스터디룸·시간대의 동시 예약 방지
- 사용자 입력값 HTML escape
- 환경변수를 이용한 DB 접속 정보 관리
- GitHub Issues·Projects·Pull Requests를 이용한 협업 및 코드 리뷰

## 테스트

권한, 데이터 소유권, 예약 시간 경계값, 중복 예약, 동시 예약, 예약 취소·환불 트랜잭션을 중심으로 통합 테스트를 진행했습니다.

- [통합 테스트 Issue #12](https://github.com/heebaek200/study_room_reservation/issues/12)
