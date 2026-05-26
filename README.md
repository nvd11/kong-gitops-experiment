# Kong GitOps Experiment: Heterogeneous Dual-Gateway Architecture

本仓库演示了如何在极度严苛的网络隔离与安全合规限制下，利用 **Kong (DB-less)** 和 **GitOps** 理念，构建一个管理混合云异构计算后端（本地 K8s 原生微服务 + GCP Cloud Run 无服务器架构）的统一流量管控平台。

## 🎯 业务背景与架构痛点 (Requirements)

我们需要部署两个独立的数据面 (Data Plane)，分别代理内网和公网的两组后端服务。面临的核心挑战如下：

1. **异构计算环境跨度大**：
   * **内部业务**：运行在受 Tailscale 零信任网络保护的局域网自建 K8s 集群中。
   * **外部业务**：运行在公有云 (GCP Cloud Run) 的 Serverless 环境中。
2. **极端的安全合规约束 (HSBC Org Policy)**：
   * GCP 的组织策略严禁 Cloud Run 开放 `allUsers` 公网访问。
   * 必须配置为“仅限内部流量 (Internal Ingress)”，且调用方必须携带合法的 Google Identity Token。
3. **架构洁癖与运维减负**：
   * 拒绝传统的 PostgreSQL 数据库，追求 100% 的 **DB-less（无状态）**。
   * 配置必须由 GitHub 作为单一真理源 (Single Source of Truth) 全盘驱动。
   * 为了降低云端运维成本，决绝在 GCP 端部署昂贵的 GKE 集群，追求以极简的 Compute Engine VM 承载外部网关。

## 💡 解决方案：去中心化双控制面架构 (The Solution)

为了满足上述苛刻条件，我们放弃了 Kong 官方传统的集中式 Hybrid 模式，独创性地采用了**“真理中心化 (GitHub) + 控制非中心化 (Decentralized Control Planes)”**的非对称网关拓扑。

### 🛡️ Site 1: 局域网本地堡垒 (Local K8s)
负责处理局域网内生流量的极低延迟路由。
* **物理位置**：局域网私有 K8s 集群（受 Tailscale 保护）。
* **网关形态 (DP1)**：Kong Ingress Controller (KIC) + DB-less Kong Proxy。
* **控制面 (CP1)**：**ArgoCD**。
* **工作流**：ArgoCD 通过 Outbound 网络主动拉取 `k8s-dp/` 目录的 K8s Ingress YAML 配置，写入本地 K8s API Server。KIC (作为本地大脑) 实时监听并翻译成 Kong 路由刷入代理内存。

### ☁️ Site 2: GCP 瘦前置网关 (Cloud Engine VM)
负责承接外网流量，清洗并突破内网限制调用被封锁的 Cloud Run。
* **物理位置**：GCP VPC 内部的一台极简 Compute Engine VM。
* **网关形态 (DP2)**：原生 Systemd 部署的 Standalone DB-less Kong。
* **控制面 (CP2)**：**GitHub Actions (Self-hosted Runner) + decK**。
* **安全鉴权破局 (The Magic)**：
  * 该 VM 绑定了具有 `Cloud Run Invoker` 权限的 GCP Service Account。
  * 我们编写了自定义的 Lua 插件 (`kong-gcp-identity`) 挂载在 DP2 上。当外网请求到达 Kong 时，插件会瞬间向本地 GCE 元数据服务器申请 Google Identity Token，并将其包装进请求头 (`Authorization: Bearer <ID_TOKEN>`)，从而畅通无阻地叩开被 Org Policy 锁死的 Cloud Run 大门。
* **工作流**：当 `cloudrun-dp/` 目录发生代码变更时，VM 内部驻留的 Github Runner 会被唤醒拉取最新配置，并在本地调用 `deck sync` 经由 `127.0.0.1:8001` 瞬间刷新网关内存，全程绝不向公网暴露管理端口。

## 📁 仓库结构 (Repository Layout)

```text
kong-gitops-experiment/
├── k8s-dp/                      # 👉 CP1 (ArgoCD) 监听的目录
│   └── ingress.yaml             # 原生 K8s Ingress 路由定义 (代理 svc1, svc2)
│
├── cloudrun-dp/                 # 👉 CP2 (Runner) 监听的目录
│   ├── kong.yaml                # decK 专用的声明式 Kong 配置文件
│   └── plugins/
│       └── kong-gcp-identity/   # 核心鉴权组件：绕过 GCP Org Policy 的 Lua 拦截器
│
└── diagrams/
    └── kong-gitops-architecture.drawio # 最新定稿的系统架构拓扑图
```

---
*Architected with ❤️ by Jason Poon & Moon (May 2026)*
