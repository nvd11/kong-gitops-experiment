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

**1. 开放云服务器防火墙端口 (控制台 或 tccli)**
*方法 A：通过网页控制台*
登录腾讯云控制台，在轻量应用服务器的【防火墙】中，添加一条放通 **TCP 6443** 端口的规则。同时建议一并放通后续 ArgoCD 需要的 **TCP 30080** 端口。

*方法 B：使用 tccli 自动化命令行（极客推荐）*
```bash
# 1. 安装 tccli (无视 CPU 架构的纯 Python 方案)
pip3 install tccli --user --break-system-packages  # 如果有 pipx 推荐使用 pipx install tccli

# 2. 配置密钥 (需在腾讯云 CAM 生成 API 密钥，推荐赋予 QcloudResourceFullAccess 角色)
tccli configure
# 按提示依次输入 SecretId, SecretKey, region (如 ap-guangzhou), output format (json)

# 3. 获取您的 Lighthouse 轻量服务器实例 ID
tccli lighthouse DescribeInstances | grep InstanceId

# 4. 一键写入防火墙规则放通 6443 和 30080 端口 (请将 lhins-xxxx 替换为实际的 InstanceId)
tccli lighthouse CreateFirewallRules --InstanceId lhins-xxxx --FirewallRules \
  '[{"Protocol":"TCP","Port":"6443","Action":"ACCEPT","CidrBlock":"0.0.0.0/0","FirewallRuleDescription":"K3s API Server"}, {"Protocol":"TCP","Port":"30080","Action":"ACCEPT","CidrBlock":"0.0.0.0/0","FirewallRuleDescription":"ArgoCD UI"}]'
```

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

**⚠️ 故障排查：如果本地连接报错 `x509: certificate is valid for 10.x.x.x, 127.0.0.1, not 43.139.214.231`**
如果您在最初安装 K3s 时**漏掉了 `--tls-san` 参数**，会导致 K3s 生成的证书里没有包含公网 IP。补救措施如下：
```bash
# 1. 登录服务器，修改 K3s 的 systemd 服务文件
sudo sed -i 's/    server \\/    server \\\n    --tls-san 43.139.214.231 \\/g' /etc/systemd/system/k3s.service

# 2. 重新加载 systemd 并停止 K3s
sudo systemctl daemon-reload
sudo systemctl stop k3s

# 3. 极其重要：删除旧的 API Server 证书，逼迫 K3s 重新生成带有公网 IP 的新证书
sudo rm -rf /var/lib/rancher/k3s/server/tls/serving-kube-apiserver.crt \
            /var/lib/rancher/k3s/server/tls/serving-kube-apiserver.key \
            /var/lib/rancher/k3s/server/tls/dynamic-cert.json
            
# 4. 启动 K3s
sudo systemctl start k3s

# 5. 最后，别忘了把服务器上新生成的 /etc/rancher/k3s/k3s.yaml 重新覆盖到本地电脑！
```

---

## 🧠 阶段二：部署控制面 (CP1) - ArgoCD

1. **创建命名空间并安装**:
   ```bash
   kubectl create namespace argocd
   
   # 避坑指南：ArgoCD 的 CRD 声明文件极大，如果使用传统的客户端 Apply 会触发 Annotation 太长 (超过 262144 bytes) 的报错。
   # 必须加上 --server-side 参数，将合并压力转移给 K8s 服务端！
   kubectl apply --server-side -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
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
