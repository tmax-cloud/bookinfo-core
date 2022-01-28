# Bookinfo CI
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

----

## IntegrationConfig 가이드

### IntegrationConfig  기본 설명

- Template Operator를 통해 생성할 경우 - [link](./integrationConfig.md#IntegrationConfig---Template-Operator-버전)
- Template Operator를 <u>사용하지 않을 경우</u> (vanilla) - [link](./integrationConfig.md#IntegrationConfig---Vanilla-버전)
- 파라미터 별 상세 설명 [link](./integrationConfig.md/#Plain-YAML)

---

### CICD-Operator 설정

####  1. Git Access Token 설정

1. Git Repo에 권한이 있는 계정의 token 생성 - [가이드](https://github.com/tmax-cloud/cicd-operator/blob/master/docs/quickstart.md#create-bot-account-and-token)
2. Token Secret 생성
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: <Secret Name>
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

## BOOKINFO -IntegrationConfig  설정

- Case별 integrationConfig 상세 설명

  - [Case 1. Gradle S2I 빌드 시나리오](./case-senario.md#case-1-gradle-s2i-빌드-시나리오)
  - [Case 2. Gradle Nexus publish 시나리오](./case-senario.md#case-2-gradle-nexus-publish-시나리오)
  - [Case 3. Maven 빌드 시나리오](./case-senario.md#case-3-maven-빌드-시나리오)

  
