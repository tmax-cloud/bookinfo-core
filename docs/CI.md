# Bookinfo CI
## 소개 및 시연 관련 설명
* Tekton 기반의 CI Operator를 통해 마이크로서비스 <U>core, order, rating, common</U>의 소스 및 통합. 
* 각각의 Repository에 대한 CI Operator의 커스텀 리소스 <U>Integration Config</U>를 생성해야 함. 
* Repository에 Pull Request 및 Push 이벤트 발생 시 설정한 <U>Integration Config</U>에 따라 CI Operator의 파이프라인이 돌아가는 시스템

## Repository 구성 요소 
* src
  * 빌드 대상이 되는 java 소스 코드 및 테스트 코드
* build.gradle, gradlew
  * Java 소스의 빌드, 테스트 등을 위한 script
* Dockerfile
  * 컨테이너 이미지 빌드를 위한 Dockerfile (CI 과정에선 사용되지 않음. 수동으로 이미지 빌드 시 사용)

## Prerequisites
1. CICD Operator/Tekton
    * install 가이드 : https://github.com/tmax-cloud/install-tekton
2. Sonarqube 
    * install 가이드 : https://docs.sonarqube.org/latest/setup/sonarqube-on-kubernetes
3. Nexus

## Integration Config Job Description
### copy-source
code-analysis가 실행되는 source-dir로 target 소스를 복사하는 job
- main branch에 push, pull request 이벤트 발생 시 task 생성
```yaml
- name: copy-source
        image: docker.io/alpine:3.13.6
        script: |
          cp -r ./src $(workspaces.<workspace name>.path)/src
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
            stringVal: <sonarqube의 host url>
          - name: SONAR_PROJECT_KEY
            stringVal: <sonarqube에서 생성한 project key>
          workspaces:
          - name: source-dir
            workspace: <workspace name>
          - name: sonar-settings
            workspace: sonar-settings
        after:
        - copy-source
        when:
          branch: 
          - main
```
### s2i-gradle-generate
Gradle s2i 빌더 이미지를 통해 타겟 소스에서 dockerfile을 생성하는 job
- Tag 생성 (Release) 이벤트 발생 시 task 생성
- Dockerfile.gen 생성 후 workspace에 저장
```yaml
- name: s2i-gradle-generate
        image: docker.io/tmaxcloudck/cicd-util:5.0.5
        env: 
          - name: JAR_NAME
            value : <빌드된 jar name>
        when:
          tag:
            - v.*
        script: |
          /usr/local/bin/s2i \
          build . docker.io/changjjjjjjjj/s2i-gradle-java:dev \
          --env JAR_NAME=$JAR_NAME \
          --tlsverify=false \
          --as-dockerfile $(workspaces.<workspace name>.path)/Dockerfile.gen
```
### build-and-push
s2i-gradle-generate로 생성한 Dockerfile.gen으로 이미지 빌드 및 푸시하는 job
- Tag 생성 (Release) 이벤트 발생 시 s2i-gradle-generate complete 후 task 생성
- Build 후 생성 된 바이너리, 라이브러리, 테스트 결과에 대한 정적분석 실행 후 push (해당 task는 gradle 빌드 과정에 포함. Pipeline에서 생성한 task가 아님)
```yaml
- name: build-and-push
        image: quay.io/buildah/stable
        script: |
          IMG_TAG=${CI_HEAD_REF#refs/tags/}

          buildah bud --tls-verify=false --storage-driver=vfs --format docker -f $(workspaces.<workspace name>.path)/Dockerfile.gen -t $REGISTRY_URL/$IMG_PATH:$IMG_TAG $(workspaces.<workspace name>.path)
          buildah login --tls-verify=false -u $REG_USER -p $REG_PASS $REGISTRY_URL
          buildah push --tls-verify=false --storage-driver=vfs $REGISTRY_URL/$IMG_PATH:$IMG_TAG docker://$REGISTRY_URL/$IMG_PATH:$IMG_TAG
        env:
          - name: REGISTRY_URL
            value: <Target registry URL (ex. core.hr.172.22.11.16.nip.io)>
          - name: IMG_PATH
            value: <Target Image path (ex. shinhan-bookinfo/bookinfo-core)>
          - name: REG_USER
            value: <Registry login user name>
          - name: REG_PASS
            value: <Registry login password>
        securityContext:
          privileged: true
        after:
          - s2i-gradle-generate
        when:
          tag:
            - v.*
```
### image-scan
레지스트리에 푸시한 이미지를 Trivy를 이용하여 스캔하는 job
- Tag 생성 (Release) 이벤트 발생 시 build-and-push complete 후 task 생성
- Job complete 후 image-scan pod log를 통해 아래와 같은 스캔 결과 확인 가능

![image](https://user-images.githubusercontent.com/56624551/147319070-a0c291fb-d5ad-46b0-bd50-ab3b3eae0365.png)
```yaml
- name: image-scan
        image: docker.io/bitnami/trivy:latest
        script: |
          IMG_TAG=${CI_HEAD_REF#refs/tags/}

          TRIVY_INSECURE=true trivy image $REGISTRY_URL/$IMG_PATH:$IMG_TAG
        env:
          - name: REGISTRY_URL
            value: <Target registry URL (ex. core.hr.172.22.11.16.nip.io)>
          - name: IMG_PATH
            value: <Target Image path (ex. shinhan-bookinfo/bookinfo-core)>
        securityContext:
          privileged: true
        after:
          - build-and-push
        when:
          tag:
            - v.*
```
### gradle-build-and-publish (common only)
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