**Table of Content**

- [IntegrationConfig - Template Operator 버전](#IntegrationConfig---Template-Operator-버전)
  - [Template에 필요한 파라미터 정의](#template-parameters)
  - [Template Instance 예시](#template-instance-예시)

- [IntegrationConfig - Vanilla 버전](#IntegrationConfig---Vanilla-버전)
  - [Plain YAML](#Plain-YAML)
  - [Sample YAML](#SAMPLE-YAML)

---

# IntegrationConfig - Template Operator 버전

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



## Template Instance 예시

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

# IntegrationConfig - Vanilla 버전

## Plain YAML

- 사용가능한 모든 필드

```yaml
apiVersion: cicd.tmax.io/v1
kind: IntegrationConfig
metadata:
  name: <Name>
spec:
  git:
    type: [github|gitlab|gitea|bitbucket]
    repository: <org>/<repo> (e.g., tmax-cloud/cicd-operator)
    apiUrl: <API server URL>
    token:
      value: <Token value> # Secret이 아닌 YAML에 직접 사용할 경우 
      valueFrom: # Secret을 통해 token을 가져올 경우
        secretKeyRef:
          name: <Token secret name>
          key: <Token secret key>
  secrets:
    - name: <Secret name to be included in a service account>
  workspaces:
    - name: <Name of workspace>
  jobs: # 2가지 타입 존재 (Pre-submit / Post-submit)
    preSubmit: # Pre-submit의 경우, PR merge, 혹은 커밋이 발생했을 때 수행되는 job을 나타냄
    - name: <Job name>
      image: <Job image>
      command:
      - <Command>
      script: <Script>
      resources:
        requests:
          memory: "64Mi"
          cpu: "250m"
        limits:
          memory: "128Mi"
          cpu: "500m"
      env:
      - name: TEST
        value: val
      when: # 특정 branch 혹은 tag에서만 jobs을 수행하고 싶을 때 사용함 <Optional>
        branch: # 특정 branch 이름
        - <RegExp>
        skipBranch: # 예외 branch 이름
        - <RegExp>
        tag: # 특정 Tag 이름
        - <RegExp>
        skipTag: # 예외 Tag 이름
        - <RegExp>
      after: # 특정 job이 끝난 이후에 실행되야 할 때, 작성 <Optional>
      - <Job Name>
      approval: # merge Request에 대한 approval 설정 
        approvers:
        - name: <User name>
          email: <User email>
        approversConfigMap: # approver에 대한 정보를 configmap에 저장하고, 필요할 때마다 재사용할 수 있음
          name: <ConfigMap name>
    postSubmit: # Post-submit은 PR, tag, 또는 commit이 push됐을 때 수행되는 job을 나타냄
    - <Same as preSubmit>
status:
  secrets: <Webhook secret>
  conditions:
  - type: WebhookRegistered
    status: [True|False]
    reason: <Reason of the condition status>
    message: <Message for the condition status>
```



## Sample YAML

```yaml
apiVersion: cicd.tmax.io/v1
kind: IntegrationConfig
metadata:
  name: sample-config
  namespace: default
spec:
  git:
    type: github 
    repository: tmax-cloud/cicd-operator
    token:
      valueFrom:
        secretKeyRef:
          name: tmax-cloud-bot-credential
          key: token
  secrets:
    - name: tmax-cloud-hub
  workspaces:
    - name: s2i
      volumeClaimTemplate:
        spec:
          storageClassName: local-path
          accessModes:
            - ReadWriteOnce
          resources:
            requests:
              storage: 1Gi
  jobs:
    preSubmit:
    - name: test-unit
      image: golang:1.14
      command:
      - go test -v ./pkg/...
      when:
        branch:
        - master
    - name: test-lint
      image: golangci/golangci-lint:v1.32
      command:
      - golangci-lint run ./... -v -E gofmt --timeout 1h0m0s
      when:
        branch:
        - master
    postSubmit:
    - name: build-push-image
      image: quay.io/buildah/stable
      command:
      - buildah bud --format docker --storage-driver=vfs -f ./Dockerfile -t $IMAGE_URL .
      - buildah push --storage-driver=vfs --creds=$CRED $IMAGE_URL docker://$IMAGE_URL
      env:
      - name: IMAGE_URL
        value: tmaxcloudck/cicd-operator:recent
      - name: CRED
        valueFrom:
          secretKeyRef:
             name: tmaxcloudck-hub-credential
             key: .dockerconfigjson
      privileged: true
      when:
        tag:
        - v.*
```

---

