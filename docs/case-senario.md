# Case 별 Task List

## Case 1. Gradle S2I 빌드 시나리오

- [prepare-sonar](#prepare-sonar)
- [code-analysis](#code-analysis)
- [s2i-gradle-generate](#s2i-gradle-generate)
- [build-and-push](#build-and-push(gradle))
- [image-scan](#image-scan)



## Case 2. Gradle Nexus publish 시나리오

- [prepare-sonar](#prepare-sonar)

- [code-analysis](#code-analysis)

- [gradle-build-and-publish](#gradle-build-and-publish)



## Case 3. Maven 빌드 시나리오

- [prepare-sonar](#prepare-sonar)
- [code-analysis](#code-analysis)
- [maven-package](#maven-package)
- [build-and-push](#build-and-push(maven))
- [image-scan](#image-scan)

---

## prepare-sonar

- 생성 시기: main branch에 push, pull request 이벤트 발생 시 task 생성
- 목적: code-analysis가 실행되는 source-dir로 target 소스를 복사하는 job
- 대상 시나리오: Case 1, 2, 3

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

##  code-analysis

- 생성시기: `prepare-sonar` 수행 이후 실행됨
- 목적: 빌드 전 소스코드 단계의 정적분석 실행 (등록한 tekton task- `sonarqube-scanner`활용)
  - Host URL을 통해 sonarqube 콘솔 접속 시 아래와 같이 정적 분석 결과 확인 가능
- 대상 시나리오: Case 1, 2, 3


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

## build-and-push(gradle)

- 생성시기:  Tag 생성 (Release) 이벤트 발생 시 s2i-gradle-generate complete 후 task 생성
- 목적: Build 후 생성 된 바이너리, 라이브러리, 테스트 결과에 대한 정적분석 실행 후 push 
  - 해당 task는 gradle 빌드 과정에 포함. Pipeline에서 생성한 task가 아님
- 대상 시나리오: Case 1

```yaml
- name: build-and-push
    image: quay.io/buildah/stable
    script: |
      IMG_TAG=${CI_HEAD_REF#refs/tags/}
      buildah bud --tls-verify=false --storage-driver=vfs --format docker -f $(workspaces.bookinfo-workspace.path)/Dockerfile.gen -t ${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG $(workspaces.bookinfo-workspace.path)
      buildah login --tls-verify=false -u ${REG_USER} -p ${REG_PASS} ${REGISTRY_URL}
      buildah push --tls-verify=false --storage-driver=vfs ${REGISTRY_URL}/${IMG_PATH}:${IMG_TAG} docker://${REGISTRY_URL}/${IMG_PATH}:$IMG_TAG
    securityContext: # pod내 root 권한 부여
      privileged: true
    after:
      - s2i-gradle-generate
    when: # v.로 시작하는 tag가 생성됐을 때
      tag:
        - v.*
```

---

## build-and-push(maven)

- 생성시기:  Tag 생성 (Release) 이벤트 발생 시 maven-package 후 task 생성
- 목적: maven-package에서 생성된 jar과 작성해놓은 Dockerfile을 이용해 이미지 빌드 및 푸시

- 대상 시나리오: Case 3

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

## image-scan 

- 생성시기: Tag 생성 (Release) 이벤트 발생 시 build-and-push complete 후 task 생성
- 목적:  레지스트리에 푸시한 이미지를 Trivy를 이용하여 스캔하는 job
- 결과: Job complete 후 image-scan pod log를 통해 아래와 같은 스캔 결과 확인 가능

- 대상 시나리오: Case 1, 3

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

---

##  s2i-gradle-generate 

- 생성 시기: Tag 생성 (Release) 이벤트 발생 시 task 생성
- 목적:  Gradle s2i 빌더 이미지를 통해 타겟 소스에서 dockerfile을 생성하는 job
- 결과:  Dockerfile.gen 생성 후 workspace에 저장
- 대상 시나리오: Case 1

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

---

## gradle-build-and-publish 

- 생성 시기: Tag 생성 (Release) 이벤트 발생 시 task 생성
- 목적: 소스를 빌드하여 정적분석 후 Nexus에 저장하는 Job
  - 설정한 sonarqube 및 nexus 콘솔에서 결과 확인 가능
  - 대상 시나리오: Case 2

![image](https://user-images.githubusercontent.com/56624551/147319778-1d35a2d3-765c-43a3-b4d7-87a39f7e3c9e.png)

```YAML
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

---

## maven-package 

- 생성시기: Tag 생성 (Release) 이벤트 발생 시 task 생성
- 목적: Maven을 이용해 소스를 빌드하여 workspace에 저장하는 Job
- 대상 시나리오: Case 3

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



