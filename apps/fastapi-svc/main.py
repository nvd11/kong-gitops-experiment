from fastapi import FastAPI

# 在 FastAPI 中，如果网关没有剥离路径，单纯设置 root_path 只会影响 OpenAPI 的生成。
# 为了让应用实际接收包含 /svc2 的请求，我们需要把路由前缀加上，或者通过 APIRouter 挂载。
app = FastAPI()

@app.get("/svc2")
def health_probe():
    return {"status": "up"}

@app.get("/svc2/hello")
def read_root():
    return {
        "status": "ok",
        "service": "fastapi-svc",
        "node": "tencent-cloud",
        "message": "Hello from FastAPI!"
    }
