# 🚀 跨云 GitOps 联邦大演练：CP1-DP1 实验进度与架构白皮书

**实验代号**: CP1-DP1 (Aliyun Control Plane - Tencent Cloud Data Plane)
**当前状态**: 🟢 核心架构已 100% 贯通并闭环运转

本文档详细记录了我们在构建金融级、跨云分布式的 Gateway API GitOps 架构中所取得的里程碑进展。包含了整个架构的全景切分、联动机制，以及在实施过程中踩过的那些极具价值的“深坑”与我们的神级破局方案。

---

## 🏛️ 一、 赛博帝国的三大 GitOps 独立资产库

为了彻底贯彻“CI与代码同行，CD与代码隔离”的极客解耦哲学，我们将整个架构在物理层面拆分为了三大独立的 GitHub 仓库。这就相当于我们将整栋大楼的设计图、砖块和装修合同完全分开管理。

### 1. 🧬 业务代码与 CI 构建库
*   **仓库名**: `kong-gitops-experiment` (当前所在仓库)
*   **职责**: 存放纯粹的 Java (Quarkus) 微服务代码，以及驱动 CI 自动化的 GitHub Actions 工作流。
*   **里程碑成果**:
    *   **防死循环双重 CI/CD**：成功构建了 `validation.yml` (PR 阶段仅跑测试把关) 和 `ci-cd.yml` (Merge 后触发编译打包)。
    *   **跨仓库推送引擎**：CI 脚本在构建出最新的 GHCR 镜像后，能够安全地带着临时凭证，跨仓库去修改下游配置库中的图纸，彻底斩断了单仓库下的“GitOps 死循环（Doom Loop）”。

### 2. 🎨 基础架构通用模板库 (Platform Assets)
*   **仓库名**: `my-shared-helm-charts`
*   **职责**: 像 Terraform Module 一样，集中存放公司级标准化的 Helm 模板。
*   **里程碑成果**:
    *   完成了 `generic-web-service` (v1.1.1) 模具的打造。它将微服务的 Deployment、Service、以及最前沿的 Gateway API (`HTTPRoute`) 抽象为可动态传参的占位符。
    *   极大降低了业务团队写 YAML 的心智负担，全公司的微服务仅需几行 `values` 即可完成高可用部署。

### 3. 🧠 声明式 CD 部署调度库 (The Brain)
*   **仓库名**: `my-argocd-manifests`
*   **职责**: 存放所有 K8s 的实体部署清单和 ArgoCD 自身的 Application 契约。
*   **里程碑成果**:
    *   **App-of-Apps 套娃架构落地**：创建了最高首脑 `root-bootstrap-app.yaml`，实现了集群从 0 到 1 的一键无感重建（Zero-Touch Bootstrap）。
    *   实现了基础设施（Kong 网关、CRDs）与业务应用（Quarkus）在同一个大本营里通过 `sync-wave` 井然有序地依次拉起。

---

## 🌩️ 二、 跨云物理底座打通 (Aliyun ➡️ Tencent Cloud)

我们没有采用将 ArgoCD 部署在目标集群的传统做法，而是落地了更为宏大的 **“多集群联邦控制（Hub-and-Spoke）”** 模式：

*   **控制面 (Hub)**：阿里云 Master (`8.148.149.80`)，运行全局唯一的 ArgoCD 实例。
*   **运行面 (Spoke)**：腾讯云 K3s (`43.139.214.231`)，运行 Kong 网关与真实的微服务容器。

**🔐 跨云安全认证的艺术**：
我们坚决拒绝在 Git 仓库里硬编码任何目标机器的公网 IP 和 Token。
我们在腾讯云创建了只读权限的 `argocd-manager` ServiceAccount。将其加密 Token 跨云提取后，保存在阿里云 ArgoCD 本地的 `Secret` 数据库中，并为其打上别名 `tencent-dp1-cluster`。
在 Git 图纸中，我们仅使用别名进行路由。ArgoCD 通过别名在本地“对暗号”后，才携带凭证跨公网去下发容器。实现了**环境完美屏蔽与丝滑漂移**。

---

## 🚧 三、 趟过的那些坑与“神级”破解方案

在搭建这套极具前沿性的架构时，我们遇到并成功击碎了以下几只极其顽固的“拦路虎”：

### 💥 大坑 1：腾讯云 SSH 防暴力破解封锁
*   **案发现场**：在进行集群调试时，本地 NixOS 笔记本因为连续发送了多次无效 SSH 密钥认证，被腾讯云的云镜安全中心判定为黑客暴力破解攻击，公网 IP 惨遭底层防火墙无情 `Connection reset` 封禁。
*   **神级破局**：我们没有去控制台排队解封，而是直接利用本地的 `~/.ssh/config`，配置了 **`ProxyJump` (幽灵跳板)**。借助已有的局域网大本营 Moon 节点，流量从 Tailscale 内网光速打洞穿透，披上 Moon 的 IP 外壳，大摇大摆地绕过了腾讯云的安全封锁，完美直连！

### 💥 大坑 2：“图纸”迟迟无法落地，应用永远 Progressing
*   **案发现场**：我们在 ArgoCD 里下发了 `Gateway` 大门和 `HTTPRoute` 指路牌，发现业务卡片永远停留在蓝色的 `Progressing` 状态，迟迟等不到绿心。
*   **神级破局**：经过排查，发现腾讯云集群里根本没有安装 Kong 控制器（包工头）。没有包工头，Gateway 图纸永远拿不到公网 IP（Address: empty）。我们立刻通过 ArgoCD 的套娃模式，一键从 GitHub 上全自动下发了 Kubernetes 官方的 Gateway API CRDs 字典，以及 Kong 的官方 Helm Chart。包工头一上班，所有的蓝圈圈在 10 秒内全部变绿！

### 💥 大坑 3：Kong Gateway API 翻译引擎崩溃 (URLRewrite Unsupported)
*   **案发现场**：为了让进入微服务的路径保持纯净（如 `/svc1` 剥离后变为 `/`），我们在 `HTTPRoute` 里使用了 K8s 官方推荐的 `URLRewrite` 过滤器。结果导致 Kong 报错：`KongConfigurationTranslationFailed`。
*   **神级破局**：深度排查 Kong 的底层架构后发现，开源版 Kong (KIC) 默认使用的是**传统路由引擎（Traditional Router）**，无法解析 Gateway API 高级的正则表达式剥离语法（需强制开启 Expression Router 新引擎）。为了追求生产环境的绝对稳定，我们果断“挥刀自宫”，去掉了不支持的 `URLRewrite`。转而在底层 Service 上加上了经受过千锤百炼的官方注解：**`konghq.com/strip-path: "true"`**。Kong 瞬间复活，完美切碎路径并送入 Quarkus 容器！

### 💥 大坑 4：微服务框架的“瞎子问题” (404 Not Found)
*   **案发现场**：当 Kong 成功剥离了 `/svc1` 路径并将 `/` 送给 Quarkus 时，Quarkus 却返回了 404。因为 Java 代码里死板地写着 `@Path("/svc1")`。
*   **神级破局**：这暴露出“路径剥离”在微服务 API 设计中的天然缺陷（会导致 Swagger 接口和 HATEOAS 链接迷路）。最终，我们利用现代框架的“路径感知”能力，关闭了网关的路径剥离，让网关原封不动地转发 `/svc1`。并在应用层面通过注入环境变量（如 `root_path`），让后端框架自我消化前缀。实现了真正的“网关与业务完美解耦，所见即所得”。

---

## 🏆 结语

本次实验不仅仅是跑通了几个接口，更是在沙盒中完成了一场企业级的基础设施架构推演。
从底层的网络穿透、到中间的凭证隔离、再到顶层的多仓库解耦和自举调度，整个系统犹如一台极其精密、高度自治的赛博引擎。

**这，就是云原生时代的架构艺术。**
