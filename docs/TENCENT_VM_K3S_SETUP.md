# ☁️ 腾讯云 4G VM (CP1+DP1) K3s 安装与部署实战指南

本文档专门针对我们刚刚采购的 **腾讯云 4核 4G 纯净版 Debian 12 (IP: 43.139.214.231)** 编写，旨在将其零误差地打造成 GitOps 控制面 (ArgoCD) 与 Kong 数据面 (DP1) 的混合沙盒。

---

## 📍 阶段一：纯净系统基建与 K3s 安装

**注意：** 因为我们计划使用 Kong 作为 K8s 的 API 网关，为了避免 80 和 443 端口冲突，我们**必须在安装 K3s 时彻底禁用其默认捆绑的 Traefik 网关**！

1. **登录战舰**:
   ```bash
   ssh gateman@43.139.214.231
   ```

2. **一键安装定制版 K3s**:
   ```bash
   # 核心注意：因为我们接下来要做 Kong 的网关实验，必须通过 --disable traefik 参数把 K3s 默认的 Traefik 彻底剥离，
   # 否则 Traefik 会霸占 80 和 443 端口，导致 Kong 无法正常监听！
   # 避坑指南：必须加上 --tls-san 公网IP，否则后续在本地电脑远程连接 K3s 时会报 x509 证书错误。
   # (国内主机推荐使用 Rancher 镜像源加速安装)
   curl -sfL https://rancher-mirror.rancher.cn/k3s/k3s-install.sh | INSTALL_K3S_MIRROR=cn INSTALL_K3S_EXEC="--disable traefik --tls-san 43.139.214.231" sh -
   ```

3. **配置普通用户 (gateman) 的 kubectl 权限**:
   默认情况下只有 root 能用 kubectl，我们需要让 `gateman` 也能顺滑操作：
   ```bash
   mkdir -p ~/.kube
   sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
   sudo chown gateman:gateman ~/.kube/config
   
   # 避坑指南：K3s 软链接的 kubectl 默认读取 /etc 路径，需显式声明 KUBECONFIG 变量
   echo 'export KUBECONFIG=~/.kube/config' >> ~/.bashrc
   source ~/.bashrc
   
   # 验证集群健康状态
   kubectl get nodes
   ```

---

## 💻 附加：配置本地电脑远程管理 K3s (Remote kubectl)

如果您希望在本地电脑（例如您的笔记本/台式机）上直接使用 `kubectl` 操作远端的 K3s 集群，请按以下步骤操作：

**1. 开放云服务器防火墙端口**
登录腾讯云控制台，在轻量应用服务器的【防火墙】中，添加一条放通 **TCP 6443** 端口的规则（或者使用 `tccli` 命令行配置）。

**2. 在本地电脑安装纯净版 kubectl (以 Linux 为例，告别臃肿的 snap)**
```bash
# 下载官方编译的独立二进制文件
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
# 赋予执行权限并移至系统目录
chmod +x kubectl
sudo mv kubectl /usr/local/bin/
```

**3. 将服务器的 Kubeconfig 拷贝到本地并替换 IP**
```bash
# 将服务端的配置文件拉取到本地
scp gateman@43.139.214.231:/home/gateman/.kube/config ~/.kube/kube-tencent-k3s.config

# 避坑指南：默认生成的配置里 IP 是 127.0.0.1，必须替换为腾讯云的公网 IP
sed -i 's/127.0.0.1/43.139.214.231/g' ~/.kube/kube-tencent-k3s.config

# 声明环境变量后测试连接
export KUBECONFIG=~/.kube/kube-tencent-k3s.config
kubectl get nodes
```

---

## 🧠 阶段二：部署控制面 (CP1) - ArgoCD

1. **创建命名空间并安装**:
   ```bash
   kubectl create namespace argocd
   kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   ```

2. **获取初始管理员密码**:
   ```bash
   kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
   ```

3. **暴露 Web UI 供公网访问**:
   由于这是一台公网 VM，为了方便您在浏览器里看可视化图表，我们将其改为 NodePort（假设映射到端口 30080）：
   ```bash
   kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "NodePort", "ports": [{"port": 443, "nodePort": 30080}]}}'
   ```
   *(请记得在腾讯云的安全组中放行 TCP `30080` 端口)*

---

## 🚀 阶段三：部署网关数据面 (DP1) - Kong

我们将安装 DB-less 模式的 Kong Ingress Controller。

1. **安装 Helm (如果尚未安装)**:
   ```bash
   curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
   ```

2. **添加并安装 Kong**:
   ```bash
   helm repo add kong https://charts.konghq.com
   helm repo update
   
   helm install kong-dp1 kong/ingress \
     --namespace kong --create-namespace \
     --set ingressController.installCRDs=false \
     --set ingressController.ingressClass=kong-k8s \
     --set env.database="off"
   ```

---

## 🐍 阶段四：构建微服务靶标 (FastAPI)

1. **安装 Docker (用于本地打包镜像)**:
   ```bash
   sudo apt update && sudo apt install -y docker.io
   sudo usermod -aG docker gateman
   ```
   *(退出重进终端以使 Docker 权限生效)*

2. **编写代码与 Dockerfile**:
   创建 `main.py`:
   ```python
   from fastapi import FastAPI
   app = FastAPI()
   @app.get("/svc1")
   def read_root():
       return {"status": "ok", "service": "svc1", "node": "tencent-cloud"}
   ```
   创建 `Dockerfile`:
   ```dockerfile
   FROM python:3.9-slim
   WORKDIR /app
   RUN pip install fastapi uvicorn
   COPY main.py .
   CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
   ```

3. **打包并直接喂给 K3s**:
   这是一种高级黑科技，避免了把测试镜像推送到公网 Registry：
   ```bash
   docker build -t my-fastapi-svc:v1 .
   docker save my-fastapi-svc:v1 > svc1.tar
   sudo k3s ctr images import svc1.tar
   ```

---

## 🔗 阶段五：GitOps 自动化贯通

1. **编写 K8s 声明文件**:
   在我们的 GitHub 仓库 (`kong-gitops-experiment`) 中创建 `Deployment`, `Service`, 和 `Ingress` 的 YAML 文件，镜像指定为我们刚才导入的 `my-fastapi-svc:v1`，Ingress Class 指定为 `kong-k8s`。
2. **在 ArgoCD 界面绑定仓库**:
   浏览器访问 `https://43.139.214.231:30080`，登录 ArgoCD。
   新建 App，指向本仓库的 YAML 所在目录。
3. **点击 Sync**：
   ArgoCD 会自动将 FastAPI 跑起来，并且 Kong 会立刻装载对应的路由规则！通过公网请求您的腾讯云 80 端口，流量打通！
