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
* Release.yaml
  * 클러스터에 배포할 인스턴스들을 명시한 스크립트 

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
   * Gradle 빌드 -> 이미지 빌드 및 푸시 (Buildah) -> 이미지 스캔 (Trivy) -> 클러스터 배포
     * https://github.com/tmax-cloud/bookinfo-core/blob/docs/core-config.yaml
---
## Setup

###  1. Git Access Token 시크릿 설정
Git 레포지토리의 clone, webhook 등의 권한을 위한 토큰 설정
1. Git Repo에 권한이 있는 계정의 token 생성 - [가이드](https://github.com/tmax-cloud/cicd-operator/blob/master/docs/quickstart.md#create-bot-account-and-token)
2. Token Secret 생성
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: <Secret Name>
  namespace: <Integration Config 가 생성될 namespace>
stringData:
  token: <Token From Git Repository>
```
### 2. Webhook 서버 설정
Git event 발생 시 생성되는 webhook을 처리하기 위한 server config 설정
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

### 3. Kubeconfig 시크릿 생성 
배포 대상 클러스터 정보를 담은 kubeconfig 시크릿 생성
```bash
$  kubectl create secret generic <secret name> --from-file <path to kubeconfig (ex. /.kube/config)>
```

### 4. Sonarqube configmap 생성
Sonarqube 정적 분석 시 이용할 정보등을 담는 configmap 
- 아래 간단한 예시 외에도 test 코드 경로, 바이너리 경로 등 분석 대상 지정 가능 - [가이드](https://docs.sonarqube.org/latest/analysis/analysis-parameters/)
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: sonar-properties
data:
  sonar-project.properties: "# required metdata\nsonar.projectKey=sonar-test\nsonar.login=admin\nsonar.projectVersion=0.1\nsonar.sourceEncoding=UTF-8\n
    sonar.password=*******\nsonar.sources=src/main/java/\nsonar.language=java\nsonar.java.binaries=src/main/java"
```
### 5. PVC 생성
Job마다 하나의 pod를 생성하는데 job 사이에 리소스들을 공유하기 위한 pvc 생성
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: ${PVC_NAME}
spec:
  resources:
    requests:
      storage: 1Gi
  volumeMode: Filesystem
  accessModes:
    - ReadWriteOnce
```
---
## 시나리오에 따른 IntegrationConfig 가이드
### 1. Git 설정
IntegrationConfig의 타겟 레포지토리 설정 파트
```yaml
    apiVersion: cicd.tmax.io/v1
    kind: IntegrationConfig
    metadata:
      name: ${CONFIG_NAME}
    spec:
      git:
        type: ${GIT_TYPE} # git 레포지토리 타입 지정 (github,gitlab)
        apiUrl: ${GIT_API_URL} # git 레포지토리 API URL 지정
        repository: ${GIT_REPO} # <Owner>/<repo명> 형식 (eg. tmaxcloud/cicd-operator)
        token: # 토큰 정보: 값을 직접 넣을 수 있으나 토큰을 시크릿으로 저장해서 참조하는 방법 권장 
          valueFrom:
            secretKeyRef: # Setup-1 에서 생성한 시크릿 참조
              name: ${TOKEN_SECRET_NAME}
              key: token
```
### 2. Workspace 설정
IntegrationConfig로 인해 생성된 task들에 마운트 될 수 있는 volume 설정 파트
- workspaces 리소스 사용 시 $(workspaces.<workspace name>.path)로 사용 가능
```yaml
    apiVersion: cicd.tmax.io/v1
    kind: IntegrationConfig
    metadata:
      name: ${CONFIG_NAME}
    spec:
      ...
      workspaces: # task pods 간에 공유되는 workspace. Volume과 비슷한 개념
        - name: gradle-workspace # Setup-5에서 생성한 PVC를 gradle-workspace라는 이름의 workspace로 이용
          persistentVolumeClaim:
            claimName: ${PVC_NAME}
        - name: sonar-settings # Setup-4 Sonarqube 정적 분석 태스크에서 참고하기 위한 configmap
          configMap:
            name: sonar-properties 
        - name: kubeconfig # Setup-3 에서 생성한 배포용 시크릿
          secret:
            secretName: kubeconfig
```
### 3. MergeConfig 설정
Pull Request 이벤트 시 파이프라인이 끝난 뒤 merge 될 방식에 대한 설정 파트
```yaml
    apiVersion: cicd.tmax.io/v1
    kind: IntegrationConfig
    metadata:
      name: ${CONFIG_NAME}
    spec:
      ...
      mergeConfig: # merge방법에 대한 설정
        method: squash
        query: # PR이 merge되기 위한 조건 탐색 위한 query 
          approveRequired: true # approval 필요 여부 
          blockLabels: # hold label이 붙을 경우, 해당 PR은 merge되지 않음
            - hold
          checks: # 나열된 task가 완료되었을 때, merge될 수 있음
            - prepare-sonar
            - code-analysis
```
### 4. Job 설정
Pull Request, Push 등의 이벤트 발생시에 동작할 job 설정 파트 (job을 기반으로 taskrun 생성)
```yaml
    apiVersion: cicd.tmax.io/v1
    kind: IntegrationConfig
    metadata:
      name: ${CONFIG_NAME}
    spec:
      ...
      jobs:
        preSubmit: # Pull Request 이벤트 발생시에 동작할 job 정의
        - name: sample-job
          image: docker.io/alpine:3.13.6
          script: |
            cp -r ./src $(workspaces.bookinfo-workspace.path)/src
          when:
            branch:
              - main
        ...
        postSubmit: # Push 이벤트 발생시에 동작할 job 정의
        - name: sample-job
          image: docker.io/alpine:3.13.6
          script: |
            cp -r ./src $(workspaces.bookinfo-workspace.path)/src
          when:
            branch:
              - main
```
---
## IntegrationConfig Job 샘플 상세 설명

샘플 config 에서 사용되는 job list

- [prepare-sonar](#prepare-sonar)
- [code-analysis](#code-analysis)
- [gradle-package](#gradle-package)
- [build-and-push](#build-and-push)
- [image-scan](#image-scan)
- [deploy](#deploy)

### prepare-sonar

- 생성 시기: main branch에 push, pull request 이벤트 발생 시 생성
- 목적: code-analysis에 사용될 소스를 PVC로 복사하는 Job 

```YAML
  - name: prepare-sonar
    image: docker.io/alpine:3.13.6 # Task Pod 생성 이미지
    script: | # Pod 내에서 동작할 script 명세
      cp -r ./src $(workspaces.gradle-workspace.path)/src
    when: # 해당 Task가 동작할 시기 지정. (branch, tag 지정 가능)
      branch: # 이 경우 main 브랜치에 pull request, push 이벤트 발생시 taskrun 생성
        - main
```

### code-analysis

- 생성시기: `prepare-sonar` 수행 이후 생성
- 목적: 빌드 전 소스코드 단계의 정적분석 실행 (미리 배포한 tekton task- `sonarqube-scanner`활용)
    - Host URL을 통해 sonarqube 콘솔 접속 시 아래와 같이 정적 분석 결과 확인 가능


![image](https://user-images.githubusercontent.com/56624551/147317616-af0dd469-d071-493a-a499-e6bcec933cc5.png)

```yaml
  - name: code-analysis
    tektonTask: # 클러스터에 사전에 정의한 Task 참조하는 job
      taskRef:  # task는 로컬, 혹은 외부의 카탈로그 참조 가능
        local: # 로컬의 sonarqube-scanner라는 task 참조
          name: sonarqube-scanner
          kind: Task
      params: # Task에서 사용되는 parameter 값 설정
        - name: SONAR_HOST_URL
          stringVal: ${SONAR_HOST_URL_TPL}
        - name: SONAR_PROJECT_KEY
          stringVal: ${SONAR_PROJECT_KEY_TPL}
      workspaces: # 
      # task에서 참조할 workspaces 설정. IntegrationConfig workspaces 를 참조하여 설정함
        - name: source-dir # gradle-workspace를 source-dir라는 별명으로 사용한다고 생각하면 됨 (sonarqube-scanner task에서 source-dir 참조하기 때문)
          workspace: gradle-workspace
        - name: sonar-settings
          workspace: sonar-settings
    after: # 다른 taskrun의 종료 이후 taskrun이 생성되도록 설정
      - prepare-sonar # prepare-sonar가 끝난 이후 실행
    when:
      branch:
        - main
```
### gradle-package

- 생성 시기: Tag 생성 (Release) 이벤트 발생 시 생성
- 목적:  Gradle build를 실행 후, jar 파일 생성
- 결과:  <FILE_NAME>.jar 생성 후 workspace(PVC)에 저장

```yaml
    - name: gradle-package
      image: docker.io/gradle:7.3.1-jdk11
      script: |
        gradle build
        cp build/libs/${JAR_NAME} $(workspaces.gradle-workspace.path)/${JAR_NAME}
      when: # tag가 v.* 형식인 push 이벤트 (release) 일 때 동작
        tag:
          - v.*
```

### build-and-push

- 생성시기:  Tag 생성 (Release) 이벤트 발생 시 gradle-package 후 생성
- 목적: gradle-package에서 생성된 jar과 작성해놓은 Dockerfile을 이용해 이미지 빌드 및 푸시

```yaml
  - name: build-and-push
    image: quay.io/buildah/stable
    script: |
      IMG_TAG=${CI_HEAD_REF#refs/tags/}
      cp $(workspaces.gradle-workspace.path)/${JAR_NAME} ./
      
      buildah bud --tls-verify=false --storage-driver=vfs --format docker -f Dockerfile -t ${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG
      buildah login --tls-verify=false -u ${REG_USER} -p ${REG_PASS} ${REGISTRY_URL}
      buildah push --tls-verify=false --storage-driver=vfs ${REGISTRY_URL}/${IMG_PATH}:${IMG_TAG} docker://${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG
    securityContext:
      privileged: true
    after:
      - gradle-package
    when:
      tag:
        - v.*
```

### image-scan

- 생성시기: Tag 생성 (Release) 이벤트 발생 시 build-and-push complete 후 생성
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
### deploy
- 생성시기: Tag 생성 (Release) 이벤트 발생 시 build-and-push complete 후 생성
- 목적:  타겟 클러스터로 설정해놓은 release.yaml에 따라 배포
```yaml
  - name: deploy
    image: docker.io/bitnami/kubectl:latest
    script: | # secret으로 mount한 kubeconfig를 default kubeconfig path(/.kube/config)로 복사 후 manifest apply
      cp $(workspaces.kubeconfig.path)/kubeconfig /.kube/config
      kubectl apply -f release.yaml
    after:
      - build-and-push
    when:
      tag:
        - v.*
```
---

## Using Template 
재사용성 및 편의성을 위해 IntegrationConfig를 template화
### Template 정의
```yaml
apiVersion: tmax.io/v1
kind: ClusterTemplate
metadata:
  name: gradle-image-integrationconfig-template
parameters:
  - name: CONFIG_NAME
    displayName: ConfigName
    description: 생성할 IntegrationConfig의 이름 지정
    required: true
    valueType: string
  - name: GIT_TYPE
    displayName: GitType
    description: github, gitlab 등의 타깃 레포지토리 타입
    required: true
    valueType: string
  - name: GIT_API_URL
    displayName: GitApiUrl
    description: 타깃 레포지토리의 api server url
    required: false
    value: ""
    valueType: string
  - name: GIT_REPO
    displayName: GitRepo
    description: 타깃 레포지토리 경로명 (ex. tmax-cloud/bookinfo-common)
    required: true
    valueType: string
  - name: TOKEN_SECRET_NAME
    displayName: TokenSecretName
    description: 웹훅 사용을 위한 토큰 정보를 담고있는 시크릿 이름
    required: true
    valueType: string
  - name: PVC_NAME
    displayName: PVCName
    description: Job들이 공유하는 workspace에서 사용할 PVC 이름
    requierd: true
    valueType: string
  - name: SONAR_HOST_URL_TPL
    displayName: SonarHostURL
    description: 소나큐브 URL
    required: true
    valueType: string
  - name: SONAR_PROJECT_KEY_TPL
    displayName: SonarProjectKey
    description: 소나큐브 프로젝트 키. 지정 안할 시 컨피그맵의 디폴트값이 사용됨
    required: false
    valueType: string
  - name: JAR_NAME
    displayName: JarName
    description: maven, gradle 등으로 빌드 시 생성되는 jar 이름
    required: true
    valueType: string
  - name: REGISTRY_URL
    displayName: RegistryURL
    description: 이미지 저장소 URL
    required: true
    valueType: string
  - name: IMG_PATH
    displayName: ImagePath
    description: 이미지 저장소 path
    required: true
    valueType: string
  - name: REG_USER
    displayName: RegUser
    description: 이미지 저장소 user name
    required: true
    valueType: string
  - name: REG_PASS
    displayName: RegPass
    description: 이미지 저장소 user password
    required: true
    valueType: string
objects:
  - apiVersion: v1
    kind: PersistentVolumeClaim
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
    kind: IntegrationConfig
    metadata:
      name: ${CONFIG_NAME}
    spec:
      ...
```


### Template Instance 생성
해당 형식에 맞춰 manifest apply 시 template operator가 integrationConfig 생성

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
        value: bookinfo-core-config
      - name: GIT_TYPE
        value: github
      - name: GIT_REPO
        value: tmax-cloud/bookinfo-core
      - name: TOKEN_SECRET_NAME
        value: <Secret name including token info>
      - name: PVC_NAME
        value: gradle-pvc
      - name: SONAR_HOST_URL_TPL
        value: <Sonarqube host url>
      - name: SONAR_PROJECT_KEY_TPL
        value: <Sonarqube project key>
      - name: JAR_NAME
        value: core.jar
      - name: REGISTRY_URL
        value: <Registry URL>
      - name: IMG_PATH
        value: shinhan-bookinfo/bookinfo-core
      - name: REG_USER
        value: <User name>
      - name: REG_PASS
        admin: <User password>
```



- 참조 - [콘솔 UI 통한 integrationConfig 생성 방법](#./console-ui.md)

  

  
