from fastapi import FastAPI

app = FastAPI(root_path="/svc2")

@app.get("/")
def health_probe():
    return {"status": "up"}

@app.get("/hello")
def read_root():
    return {
        "status": "ok",
        "service": "fastapi-svc",
        "node": "tencent-cloud",
        "message": "Hello from FastAPI!"
    }
