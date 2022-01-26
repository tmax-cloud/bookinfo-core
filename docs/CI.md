# Bookinfo CI
## Integration Config Core Concepts 
* clusterTemplate 정의 (내부 ci/cd에서 사용할 변수 정의) --> templateInstance 생성 (실제 변수값 입력) --(tmax templateOperator)--> integrationConfig 생성 --> git push or pull request 이벤트 발생 --(tmax ci/cd operator)--> PipelineRun 실행 --(tekton operator)--> TaskRun 실행 --> pod 생성 및 step 수행

## 소개 및 시연 관련 설명
* Tekton 기반의 CI Operator를 통해 마이크로서비스 <U>core, order, rating, common</U>의 소스 및 통합. 
* Template instance의 파라미터를 정의 후 배포하면 그에 따라 integration config가 생성되는 방식
* Repository에 Pull Request 및 Push 이벤트 발생 시 설정한 <U>Integration Config</U>에 따라 CI Operator의 파이프라인이 돌아가는 시스템
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
## Template Parameters
template operator 를 이용하여 인스턴스 생성 시 사용자가 지정해야하는 파라미터 정보
```yaml
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
```

## Integration Config Job Description
### prepare-sonar
code-analysis가 실행되는 source-dir로 target 소스를 복사하는 job
- main branch에 push, pull request 이벤트 발생 시 task 생성
```yaml
- name: prepare-sonar
    image: docker.io/alpine:3.13.6
    script: |
      cp -r ./src $(workspaces.bookinfo-workspace.path)/src
    when:
      branch:
        - main
```
### code-analysis
Sonarqube scanner task를 실행하는 job
- main branch에 push, pull request 이벤트 발생 시 copy-source job complete 후 task 생성
- 빌드 전 소스코드 단계의 정적분석 실행
- Host URL을 통해 sonarqube 콘솔 접속 시 아래와 같이 정적 분석 결과 확인 가능

![image](https://user-images.githubusercontent.com/56624551/147317616-af0dd469-d071-493a-a499-e6bcec933cc5.png)
```yaml
- name: code-analysis
    tektonTask:
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
          workspace: bookinfo-workspace
        - name: sonar-settings
          workspace: sonar-settings
    after:
      - copy-source
    when:
      branch:
        - main
```
### s2i-gradle-generate (Case 1)
Gradle s2i 빌더 이미지를 통해 타겟 소스에서 dockerfile을 생성하는 job
- Tag 생성 (Release) 이벤트 발생 시 task 생성
- Dockerfile.gen 생성 후 workspace에 저장
```yaml
- name: s2i-gradle-generate
    image: docker.io/tmaxcloudck/cicd-util:5.0.5
    when:
      tag:
        - v.*
    script: |
      /usr/local/bin/s2i \
      build . docker.io/changjjjjjjjj/s2i-gradle-java:dev \
      --env JAR_NAME=${JAR_NAME} \
      --tlsverify=false \
      --as-dockerfile $(workspaces.bookinfo-workspace.path)/Dockerfile.gen
```
### build-and-push (Case 1)
s2i-gradle-generate로 생성한 Dockerfile.gen으로 이미지 빌드 및 푸시하는 job
- Tag 생성 (Release) 이벤트 발생 시 s2i-gradle-generate complete 후 task 생성
- Build 후 생성 된 바이너리, 라이브러리, 테스트 결과에 대한 정적분석 실행 후 push (해당 task는 gradle 빌드 과정에 포함. Pipeline에서 생성한 task가 아님)
```yaml
- name: build-and-push
    image: quay.io/buildah/stable
    script: |
      IMG_TAG=${CI_HEAD_REF#refs/tags/}
      buildah bud --tls-verify=false --storage-driver=vfs --format docker -f $(workspaces.bookinfo-workspace.path)/Dockerfile.gen -t ${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG $(workspaces.bookinfo-workspace.path)
      buildah login --tls-verify=false -u ${REG_USER} -p ${REG_PASS} ${REGISTRY_URL}
      buildah push --tls-verify=false --storage-driver=vfs ${REGISTRY_URL}/${IMG_PATH}:${IMG_TAG} docker://${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG
    securityContext:
      privileged: true
    after:
      - s2i-gradle-generate
    when:
      tag:
        - v.*
```
### image-scan (Case 1, Case3)
레지스트리에 푸시한 이미지를 Trivy를 이용하여 스캔하는 job
- Tag 생성 (Release) 이벤트 발생 시 build-and-push complete 후 task 생성
- Job complete 후 image-scan pod log를 통해 아래와 같은 스캔 결과 확인 가능

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
### gradle-build-and-publish (Case 3)
소스를 빌드하여 정적분석 후 Nexus에 저장하는 Job
- Tag 생성 (Release) 이벤트 발생 시 task 생성
- 설정한 sonarqube 및 nexus 콘솔에서 결과 확인 가능

![image](https://user-images.githubusercontent.com/56624551/147319778-1d35a2d3-765c-43a3-b4d7-87a39f7e3c9e.png)
```yaml
- name: gradle-build-and-publish
        image: docker.io/gradle:7.3.1-jdk11
        script: |
          gradle build
          gradle sonarqube
          gradle publish
        when:
          tag:
            - v.*
```
### maven-package (Case 3)
Maven을 이용해 소스를 빌드하여 workspace에 저장하는 Job
- Tag 생성 (Release) 이벤트 발생 시 task 생성
```yaml
- name: maven-package
        image: docker.io/maven:3.8.4
        script: |
          mvn clean
          mvn package
          cp target/${JAR_NAME} $(workspaces.maven-workspace.path)/${JAR_NAME}
        when:
          tag:
            - v.*
```
### build-and-push (Case 3)
maven-package에서 생성된 jar과 작성해놓은 Dockerfile을 이용해 이미지 빌드 및 푸시
- Tag 생성 (Release) 이벤트 발생 시 maven-package 후 task 생성
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
