# k6 부하 테스트 (Cotea Backend)

로컬/스테이징 백엔드에 대해 [k6](https://k6.io/)로 부하를 걸 수 있는 스크립트입니다.

## 사전 조건

- k6 설치 (`brew install k6` — 이미 있으면 `k6 version`)
- 백엔드 기동 (`cd backend && ./gradlew bootRun`)
- 추천/힌트 dryRun은 DB에 문제 메타(예: `1829`)가 있어야 함

## 스크립트

| 파일 | 용도 | LLM 호출 |
|------|------|----------|
| `smoke.js` | 헬스 + 추천 + 힌트 dryRun 1회 | 없음 |
| `load-recommend.js` | 추천 API 부하 | 없음 |
| `load-hint-dryrun.js` | 힌트 dryRun 부하 (프롬프트 조립) | 없음 |
| `load-mixed.js` | health / recommend / dryRun 혼합 | 없음 |
| `load-hint-live.js` | **실제 힌트(Claude)** — 비용 발생 | **있음** |

## 실행 예시

```bash
# 레포 루트에서
k6 run k6/smoke.js

# 추천 부하 (VU 20, 1분)
VUS=20 DURATION=1m k6 run k6/load-recommend.js

# 힌트 dryRun 부하
VUS=5 DURATION=30s k6 run k6/load-hint-dryrun.js

# 혼합
VUS=10 DURATION=1m k6 run k6/load-mixed.js

# 실제 LLM (명시적 확인 필요)
CONFIRM_LIVE=1 k6 run k6/load-hint-live.js
```

### 환경변수

| 변수 | 기본 | 설명 |
|------|------|------|
| `BASE_URL` | `http://localhost:8080` | 백엔드 주소 |
| `PROBLEM_ID` | `1829` | 테스트 문제 ID |
| `AUTH_TOKEN` | (없음) | JWT. `Bearer ` 접두 없어도 됨 |
| `VUS` | 스크립트별 | 동시 가상 유저 |
| `DURATION` | 스크립트별 | 예: `30s`, `1m` |
| `THINK_TIME` | 스크립트별 | 요청 사이 sleep(초) |
| `CONFIRM_LIVE` | 없음 | live 스크립트는 `1` 필수 |

## Grafana로 그래프 보기 (Prometheus + Grafana)

터미널 요약 외에 시계열 차트로 보려면 compose 스택을 띄운다.

```bash
# 1) Prometheus(9090) + Grafana(3000) 기동
docker compose -f k6/docker-compose.yml up -d

# 2) 브라우저
#    Grafana  http://localhost:3000  (admin / admin)
#    대시보드 "Cotea k6 Load Test" (폴더: Cotea k6)
#    Prometheus http://localhost:9090

# 3) 메트릭을 Prometheus로 보내며 k6 실행
chmod +x k6/run-with-grafana.sh
./k6/run-with-grafana.sh k6/smoke.js
VUS=10 DURATION=30s ./k6/run-with-grafana.sh k6/load-recommend.js
```

직접 실행하려면:

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,min,max' \
k6 run -o experimental-prometheus-rw k6/load-recommend.js
```

종료:

```bash
docker compose -f k6/docker-compose.yml down
# 데이터까지 지우려면: docker compose -f k6/docker-compose.yml down -v
```

## 권장 순서

1. `smoke.js`로 연결 확인  
2. (선택) compose up 후 Grafana 대시보드 열어두기  
3. `load-recommend.js` / `load-hint-dryrun.js`로 서버·DB 부하 측정  
4. 필요할 때만 `CONFIRM_LIVE=1` live 스크립트 (VU·시간 최소화)

## 해석 팁

- `http_req_duration` p95, `http_req_failed` 비율을 먼저 본다
- dryRun 실패가 많으면 메타 로드/프롬프트 조립/DB 쪽을 의심
- live만 느리면 LLM·네트워크 병목일 가능성이 큼
- 운영 EC2에 강한 부하는 합의 후, VU를 낮게 시작
- Grafana에서 그래프가 안 보이면: k6가 끝난 직후 시간 범위를 `Last 15 minutes`로 두고, Prometheus UI에서 `k6_http_reqs_total` 조회해 본다
