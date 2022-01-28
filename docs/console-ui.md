# 콘솔 UI - integrationConfig 생성 

[TASK 생성](#TASK-생성)

[파이프라인 생성](#파이프라인-생성)

---

## TASK 생성

### step 1. 네임스페이스 설정

- integrationConfig Task가 생성될 네임스페이스 선택

![ns-config](https://user-images.githubusercontent.com/25574165/151495791-426a1c50-e5fc-4637-9c87-129fc9935f56.PNG)

### step 2. Step 설정

- 기입 항목: Task에서 실행될 image 정보, Script 혹은 command,  환경 변수 기입

![2 step-setting](https://user-images.githubusercontent.com/25574165/151501491-d79cb10f-124b-4953-be33-5fc4beb47459.PNG)

### 생성완료 화면

![list](https://user-images.githubusercontent.com/25574165/151501585-5883e75f-4cb0-4667-8059-21f4bca2a854.PNG)

---

## 파이프라인 생성

### step 1. 파이프라인 파라미터 및 리소스 기입 

- 파이프라인에서 사용될 파라미터 및 리소스 작성
- Bookinfo 프로젝트에서는 사용하지 않음 

### step 2. 워크스페이스 및 task 배치 

- task는병렬, 순차 배치 가능 (태스크 선택 후 `+` 버튼 클릭) 

![pipe](https://user-images.githubusercontent.com/25574165/151501813-7d6a23db-2fa4-40bc-a18d-9ae320417a31.PNG)

### 최종 생성 완료 화면

![done](https://user-images.githubusercontent.com/25574165/151501890-bbc695c9-de85-4853-b811-e103694c3a35.PNG)

