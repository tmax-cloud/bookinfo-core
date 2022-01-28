# Bookinfo CI

전체 목차

- [Integration Config Core Concepts](#Integration-Config-Core-Concepts)
- [소개 및 시연 관련 설명](#소개-및-시연-관련-설명)
- [Repository 구성 요소](#Repository-구성-요소)
- [Prerequisites](#Prerequisites)
- [시나리오 구성](#시나리오-구성)
- [IntegrationConfig 가이드](#IntegrationConfig-가이드)
  - [CICD-Operator 설정](#CICD-Operator-설정)
    - [Git Access Token 설정](#1.-Git-Access-Token-설정)
    - [Webhook 서버 설정](#2.-Webhook-서버-설정)
  - [integrationConfig 생성](#integrationConfig-생성)
    - [integrationConfig Template 생성](#integrationConfig-Template-생성)
    - [integrationConfig Template Instance 생성](#integrationConfig-Template-Instance-생성)

- [IntegrationConfig  Job 상세 설명](#IntegrationConfig-Job-상세-설명)

  

---

## Integration Config Core Concepts 

* clusterTemplate 정의(내부 ci/cd에서 사용할 변수 정의) &rarr; templateInstance 생성(실제 변수값 입력 / tmax templateOperator) &rarr;
  integrationConfig 생성 &rarr;  git push or pull request 이벤트 발생 (tmax ci/cd operator) &rarr; PipelineRun 실행(tekton operator) &rarr; TaskRun 실행  &rarr; 
  생성 및 step 수행

## 소개 및 시연 관련 설명
* Tekton 기반의 CI Operator를 통해 마이크로서비스 <u>core, order, rating, common</u>의 소스 및 통합. 
* Template instance의 파라미터를 정의 후 배포하면 그에 따라 integration config가 생성되는 방식
* Repository에 Pull Request 및 Push 이벤트 발생 시 설정한 <u>Integration Config</u>에 따라 CI Operator의 파이프라인이 돌아가는 시스템
## Repository 구성 요소 
* src
  * 빌드 대상이 되는 java 소스 코드 및 테스트 코드
* build.gradle, gradlew
  * Java 소스의 빌드, 테스트 등을 위한 script
* Dockerfile
  * 컨테이너 이미지 빌드를 위한 Dockerfile (S2I 빌드 시나리오에선 사용 X)

## Prerequisites
1. CICD Operator/Tekton
    * install 가이드 : https://github.com/tmax-cloud/install-tekton
2. Sonarqube 
    * install 가이드 : https://docs.sonarqube.org/latest/setup/sonarqube-on-kubernetes
3. Template Operator
    * install 가이드 : https://github.com/tmax-cloud/template-operator#install-template-operator
4. Nexus

---

## 시나리오 구성

* master branch에 push는 pull request를 통해서만 가능
1. Pull request 생성 시: 정적분석
   - Pull request 생성 시 approval이 필수로 필요. code 정적분석까지 완료되면 자동으로 merge
    ```yaml
    mergeConfig:
        method: squash
        query:
          approveRequired: true
          blockLabels:
            - hold
          checks:
            - prepare-sonar
            - code-analysis
    ```
2. PR 통과되어 merge 시: 정적분석
3. Release (tag 형식 v.*) 시:
   * Case 1. Gradle S2I 빌드 시나리오: 
     * 도커파일 생성 (S2I) -> 이미지 빌드 및 푸시 (Buildah) -> 이미지 스캔 (Trivy)
     * https://github.com/tmax-cloud/bookinfo-core/blob/main/core-config.yaml
   * Case 2. Gradle Nexus publish 시나리오: 
     * 빌드 & 퍼블리시 (Gradle)
     * https://github.com/tmax-cloud/bookinfo-common/blob/main/common-config.yaml
   * Case 3. Maven 빌드 시나리오: 
     * 빌드 (Maven) -> 이미지 빌드 및 푸시 (Buildah) -> 이미지 스캔 (Trivy)
     * https://github.com/changju-test/echo_maven/blob/master/maven.yaml

---

## IntegrationConfig 가이드

- 작업 순서:
  - 1. CICD-Operator 설정 수정하기
    2. integrationConfig를 위한 Template 작성하기
       - 파라미터 및 integrationConfig 설정 기입
    3. Template Instance 생성하기

### CICD-Operator 설정

####  1. Git Access Token 설정

1. Git Repo에 권한이 있는 계정의 token 생성 - [가이드](https://github.com/tmax-cloud/cicd-operator/blob/master/docs/quickstart.md#create-bot-account-and-token)
2. Token Secret 생성
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: <Secret Name>
  namespace: cicd-system
stringData:
  token: <Token From Git Repository>
```
#### 2. Webhook 서버 설정

- Webhook 설정은 `ConfigMap`에서 이루어짐

```bash
# ConfigMap 확인
$ kubectl get cm -n cicd-system cicd-config -o yaml
```

- 사용가능한 Webhook 서버의 서비스 타입(`exposeMode`): Ingress / LoadBalancer / ClusterIP (기본: Ingress)
  - 서비스 타입이 Ingress 일 경우, `ingressClass`, `ingressHost` 설정 필요. 없으면 공백 처리. 
    - `ingressHost` 기본값: cicd-webhook.{Ingress Controller IP}.nip.io
  - 서비스 타입을 LoadBalancer로 바꿀 경우, Configmap의 `externalHostName` 값 수정 필요

```bash
# LoadBalancer IP, Port 가져오기
$ externalHostName=$(kubectl get svc -n cicd-system cicd-webhook -o jsonpath='{.status.loadBalancer.ingress[].ip}{":"}{.spec.ports[].nodePort}')

# 사용자 지정 IP를 사용할 경우
$ externalHostName=<사용자 지정 IP>

# ConfigMap수정하기 
$ kubectl patch -n cicd-system cm cicd-config --type=json -p '[{"op": "replace", "path": "/data/externalHostName", "value": '$externalHostName'}]'

```

---

### integrationConfig 생성

- integrationConfig 생성은 두가지 방법으로 생성할 수 있음
  - Template Operator 기반 - 해당 프로젝트(Bookinfo)는 Template을 활용함
  - Plain YAML 파일

#### integrationConfig Template 생성

- template operator 를 이용하여 인스턴스 생성 시 사용자가 지정해야하는 파라미터 정보

```yaml
apiVersion: tmax.io/v1
kind: ClusterTemplate
metadata:
  name: {TEMPLATE NAME}
parameters:
  - name: CONFIG_NAME
    description: 생성할 IntegrationConfig의 이름 지정
    required: true
    valueType: string
  - name: CONFIG_SECRET
    description: docker config 등을 담고있는 시크릿 이름
    required: false
    valueType: string
  - name: GIT_TYPE
    description: github, gitlab 등의 타깃 레포지토리 타입
    required: true
    valueType: string
  - name: GIT_API_URL
    description: 타깃 레포지토리의 api server url
    required: false
    value: ""
    valueType: string
  - name: GIT_REPO
    description: 타깃 레포지토리 경로명 (ex. tmax-cloud/bookinfo-common)
    required: true
    valueType: string
  - name: TOKEN_SECRET_NAME
    description: 웹훅 사용을 위한 토큰 정보를 담고있는 시크릿 이름
    required: true
    valueType: string
  - name: PVC_NAME
    description: Job들이 공유하는 workspace에서 사용할 PVC 이름
    requierd: true
    valueType: string
  - name: SONAR_HOST_URL_TPL
    description: 소나큐브 URL
    required: true
    valueType: string
  - name: SONAR_PROJECT_KEY_TPL
    description: 소나큐브 프로젝트 키. 지정 안할 시 컨피그맵의 디폴트값이 사용됨
    required: false
    valueType: string
  - name: JAR_NAME
    description: maven, gradle 등으로 빌드 시 생성되는 jar 이름
    required: true
    valueType: string
  - name: REGISTRY_URL
    description: 이미지 저장소 URL
    required: true
    valueType: string
  - name: IMG_PATH
    description: 이미지 저장소 path
    required: true
    valueType: string
  - name: REG_USER
    description: 이미지 저장소 user name
    required: true
    valueType: string
  - name: REG_PASS
    description: 이미지 저장소 user password
    required: true
    valueType: string
objects: # template과 연관된 Object manifests
  - apiVersion: v1 
    kind: PersistentVolumeClaim # 생성된 데이터 저장하기 위한 PVC
    metadata:
      name: ${PVC_NAME} 
    spec:
      resources:
        requests:
          storage: 1Gi
      volumeMode: Filesystem
      accessModes:
        - ReadWriteOnce
  - apiVersion: cicd.tmax.io/v1
    kind: IntegrationConfig # integrationConfig 설정
    metadata:
      name: ${CONFIG_NAME}
    spec:
      git:
        type: ${GIT_TYPE} # git 종류 설정 (github,gitlab)
        apiUrl: ${GIT_API_URL} # git 대표 url (eg. https://github.com) 
        repository: ${GIT_REPO} # <Owner>/<repo명> 형식 (eg. <tmaxcloud/cicd-operator)
        token: # 사전에 생성한 토큰 정보 
          valueFrom:
            secretKeyRef: # 시크릿으로부터 토큰 정보 추출
              name: ${TOKEN_SECRET_NAME} 
              key: token
      workspaces: # task간 생성된 데이터가 공유될 수 있도록 설정
        - name: maven-workspace
          persistentVolumeClaim:
            claimName: ${PVC_NAME}
        - name: sonar-settings
          configMap:
            name: sonar-maven-properties
      secrets:
        - name: ${CONFIG_SECRET}
      mergeConfig: # merge방법에 대한 설정
        method: squash 
        query: # PR이 merge되기 위한 조건 탐색 위한 query 
          approveRequired: true # approval 필요 여부 
          blockLabels: # hold label이 붙을 경우, 해당 PR은 merge되지 않음
            - hold
          checks: # 나열된 task가 완료되었을 때, merge될 수 있음
            - prepare-sonar
            - code-analysis
        jobs:
      jobs:
        preSubmit: #PR merge, 혹은 커밋이 발생했을 때 수행되는 job을 나타냄
          - name: prepare-sonar
            image: docker.io/alpine:3.13.6
            script: |
              cp -r ./src $(workspaces.maven-workspace.path)/src
            when: # 특정 branch 혹은 tag에서만 jobs을 수행하고 싶을 때 사용함
              branch:
                - master
          - name: code-analysis
            tektonTask: # tetktonTask로 등록한 것을 사용하고자 할 때 사용함
              taskRef:
                local:
                  name: sonarqube-scanner
                  kind: Task
              params:
                - name: SONAR_HOST_URL
                  stringVal: ${SONAR_HOST_URL_TPL}
                - name: SONAR_PROJECT_KEY
                  stringVal: ${SONAR_PROJECT_KEY_TPL}
              workspaces:
                - name: source-dir
                  workspace: maven-workspace
                - name: sonar-settings
                  workspace: sonar-settings
            after:  # 특정 job이 끝난 이후에 실행되야 할 때, 선후관계를 의미함
              - prepare-sonar
            when:
              branch:
                - master
```



#### integrationConfig Template Instance 생성

- 해당 형식에 맞춰 manifest apply 시 template operator가 integrationConfig 생성

```yaml
apiVersion: tmax.io/v1
kind: TemplateInstance
metadata:
  name: bookinfo-rating-ic-instance
  namespace: default
spec:
  clustertemplate:
    metadata:
      name: gradle-image-integrationconfig-template
    parameters:
      - name: CONFIG_NAME
        value: bookinfo-rating-config
      - name: CONFIG_SECRET
        value: <Docker config secret name>
      - name: GIT_TYPE
        value: github
      - name: GIT_REPO
        value: tmax-cloud/bookinfo-rating
      - name: TOKEN_SECRET_NAME
        value: <Secret name including token info>
      - name: PVC_NAME
        value: rating-pvc
      - name: SONAR_HOST_URL_TPL
        value: <Sonarqube host url>
      - name: SONAR_PROJECT_KEY_TPL
        value: <Sonarqube project key>
      - name: JAR_NAME
        value: rating.jar
      - name: REGISTRY_URL
        value: <Registry URL>
      - name: IMG_PATH
        value: shinhan-bookinfo/bookinfo-rating
      - name: REG_USER
        value: <User name>
      - name: REG_PASS
        admin: <User password>
```

---

## IntegrationConfig  Job 상세 설명

bookinfo 프로젝트에서 사용되는 job list

- [prepare-sonar](#prepare-sonar)
- [code-analysis](#code-analysis)
- [gradle-package](#gradle-package)
- [build-and-push](#build-and-push)
- [image-scan](#image-scan)

### prepare-sonar

- 생성 시기: main branch에 push, pull request 이벤트 발생 시 task 생성
- 목적: code-analysis가 실행되는 source-dir로 target 소스를 복사하는 job

```YAML
- name: prepare-sonar
    image: docker.io/alpine:3.13.6
    script: |
      cp -r ./src $(workspaces.bookinfo-workspace.path)/src
    when: # main branch에 push/pull request가 발생했을 때 
      branch:
        - main
```

---

### code-analysis

- 생성시기: `prepare-sonar` 수행 이후 실행됨
- 목적: 빌드 전 소스코드 단계의 정적분석 실행 (등록한 tekton task- `sonarqube-scanner`활용)
  - Host URL을 통해 sonarqube 콘솔 접속 시 아래와 같이 정적 분석 결과 확인 가능


![image](https://user-images.githubusercontent.com/56624551/147317616-af0dd469-d071-493a-a499-e6bcec933cc5.png)

```yaml
- name: code-analysis
    tektonTask: #등록한 tektonTask 가져와서 실행 
      taskRef: 
        local:
          name: sonarqube-scanner
          kind: Task
      params:
        - name: SONAR_HOST_URL
          stringVal: ${SONAR_HOST_URL_TPL}
        - name: SONAR_PROJECT_KEY
          stringVal: ${SONAR_PROJECT_KEY_TPL}
      workspaces: # 발생하는 데이터를 공유할 수 있도록 설정
      # bookinfo-workspace를 설정함으로써, bookinfo서비스간 데이터를 공유할 수 있음
        - name: source-dir
          workspace: bookinfo-workspace
        - name: sonar-settings
          workspace: sonar-settings
    after:
      - prepare-sonar # prepare-sonar가 끝난 이후 실행
    when:
      branch:
        - main
```

---

### gradle-package

- 생성 시기: Tag 생성 (Release) 이벤트 발생 시 task 생성
- 목적:  Gradle build를 실행 후, jar 파일 생성 
- 결과:  <FILE_NAME>.jar 생성 후 workspace에 저장

```yaml
- name: gradle-package
  image: docker.io/gradle:7.3.1-jdk11
  script: |
    gradle build
    cp build/libs/$JAR_NAME $(workspaces.core-workspace.path)/$JAR_NAME
  env: 
    - name: JAR_NAME
      value : core.jar
  when:
    tag:
      - v.*
```

---

### build-and-push

- 생성시기:  Tag 생성 (Release) 이벤트 발생 시 maven-package 후 task 생성
- 목적: maven-package에서 생성된 jar과 작성해놓은 Dockerfile을 이용해 이미지 빌드 및 푸시

```yaml
- name: build-and-push
    image: quay.io/buildah/stable
    script: |
      IMG_TAG=${CI_HEAD_REF#refs/tags/}
      cp $(workspaces.maven-workspace.path)/${JAR_NAME} ./
      
      buildah bud --tls-verify=false --storage-driver=vfs --format docker -f Dockerfile -t ${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG
      buildah login --tls-verify=false -u ${REG_USER} -p ${REG_PASS} ${REGISTRY_URL}
      buildah push --tls-verify=false --storage-driver=vfs ${REGISTRY_URL}/${IMG_PATH}:${IMG_TAG} docker://${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG
    securityContext:
      privileged: true
    after:
      - maven-package
    when:
      tag:
        - v.*
```

---

### image-scan 

- 생성시기: Tag 생성 (Release) 이벤트 발생 시 build-and-push complete 후 task 생성
- 목적:  레지스트리에 푸시한 이미지를 Trivy를 이용하여 스캔하는 job
- 결과: Job complete 후 image-scan pod log를 통해 아래와 같은 스캔 결과 확인 가능

![image](https://user-images.githubusercontent.com/56624551/147319070-a0c291fb-d5ad-46b0-bd50-ab3b3eae0365.png)

```yaml
- name: image-scan
    image: docker.io/bitnami/trivy:latest
    script: |
      IMG_TAG=${CI_HEAD_REF#refs/tags/}
      TRIVY_INSECURE=true trivy image ${REGISTRY_URL}/${IMG_PATH}:${IMG_TAG}
    securityContext:
      privileged: true
    after:
      - build-and-push
    when:
      tag:
        - v.*
```



- 참조 - [콘솔 UI 통한 integrationConfig 생성 방법](#./console-ui.md)

  

  
